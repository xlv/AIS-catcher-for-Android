/*
 *     AIS-catcher for Android
 *     Copyright (C)  2022 jvde.github@gmail.com.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#include <jni.h>
#include <android/log.h>

#include <vector>
#include <string>
#include <sstream>

#include <AIS-catcher.h>

const int TIME_CONSTRAINT = 120;

#define LOG_TAG "AIS-catcher JNI"

#define LOGE(...) \
  __android_log_print(ANDROID_LO \
  G_ERROR, LOG_TAG, __VA_ARGS__)

#include "AIS-catcher.h"

#include "Signals.h"
#include "Common.h"
#include "Model.h"
#include "IO.h"
#include "AISMessageDecoder.h"

#include "Device/RTLSDR.h"
#include "Device/AIRSPYHF.h"
#include "Device/HACKRF.h"
#include "Device/RTLTCP.h"
#include "Device/SpyServer.h"
#include "Device/AIRSPY.h"

static int javaVersion;

static JavaVM *javaVm = nullptr;
static jclass javaClass = nullptr;
static jclass javaStatisticsClass = nullptr;

struct Statistics {
    long DataB;
    long DataGB;
    int Total;
    int ChA;
    int ChB;
    int Msg[28];
    int Error;

} statistics;

std::string nmea_msg;
std::string json_queue;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *, void *) {
    return JNI_VERSION_1_6;
}

/*
void Attach(JNIEnv *env)
{
    if (javaVm->GetEnv((void **) &env, javaVersion) == JNI_EDETACHED)
       javaVm->AttachCurrentThread(&env, nullptr);
}

void DetachThread()
{
    javaVm->DetachCurrentThread();
}
*/

// JAVA interaction and callbacks

void pushStatistics(JNIEnv *env) {

    env->SetStaticIntField(javaStatisticsClass,
                           env->GetStaticFieldID(javaStatisticsClass, "DataB", "I"),
                           statistics.DataB);
    env->SetStaticIntField(javaStatisticsClass,
                           env->GetStaticFieldID(javaStatisticsClass, "DataGB", "I"),
                           statistics.DataGB);
    env->SetStaticIntField(javaStatisticsClass,
                           env->GetStaticFieldID(javaStatisticsClass, "Total", "I"),
                           statistics.Total);
    env->SetStaticIntField(javaStatisticsClass,
                           env->GetStaticFieldID(javaStatisticsClass, "ChA", "I"), statistics.ChA);
    env->SetStaticIntField(javaStatisticsClass,
                           env->GetStaticFieldID(javaStatisticsClass, "ChB", "I"), statistics.ChB);
    env->SetStaticIntField(javaStatisticsClass,
                           env->GetStaticFieldID(javaStatisticsClass, "Msg123", "I"),
                           statistics.Msg[1] + statistics.Msg[2] + statistics.Msg[3]);
    env->SetStaticIntField(javaStatisticsClass,
                           env->GetStaticFieldID(javaStatisticsClass, "Msg5", "I"),
                           statistics.Msg[5]);
    env->SetStaticIntField(javaStatisticsClass,
                           env->GetStaticFieldID(javaStatisticsClass, "Msg1819", "I"),
                           statistics.Msg[18] + statistics.Msg[19]);
    env->SetStaticIntField(javaStatisticsClass,
                           env->GetStaticFieldID(javaStatisticsClass, "Msg24", "I"),
                           statistics.Msg[24] + statistics.Msg[25]);
    env->SetStaticIntField(javaStatisticsClass,
                           env->GetStaticFieldID(javaStatisticsClass, "MsgOther", "I"),
                           statistics.Total -
                           (statistics.Msg[1] + statistics.Msg[2] + statistics.Msg[3] +
                            statistics.Msg[5] + statistics.Msg[18] + statistics.Msg[19] +
                            statistics.Msg[24] + statistics.Msg[25]));
}

static void callbackNMEA(JNIEnv *env, const std::string &str) {

    jstring jstr = env->NewStringUTF(str.c_str());
    jmethodID method = env->GetStaticMethodID(javaClass, "onNMEA", "(Ljava/lang/String;)V");
    env->CallStaticVoidMethod(javaClass, method, jstr);
}

static void callbackMessage(JNIEnv *env, const std::string &str) {

    jstring jstr = env->NewStringUTF(str.c_str());
    jmethodID method = env->GetStaticMethodID(javaClass, "onMessage", "(Ljava/lang/String;)V");
    env->CallStaticVoidMethod(javaClass, method, jstr);
}

static void callbackConsole(JNIEnv *env, const std::string &str) {

    jstring jstr = env->NewStringUTF(str.c_str());
    jmethodID method = env->GetStaticMethodID(javaClass, "onStatus", "(Ljava/lang/String;)V");
    env->CallStaticVoidMethod(javaClass, method, jstr);
}

static void callbackConsoleFormat(JNIEnv *env, const char *format, ...) {

    char buffer[256];
    va_list args;
    va_start (args, format);
    vsnprintf(buffer, 255, format, args);

    jstring jstr = env->NewStringUTF(buffer);
    jmethodID method = env->GetStaticMethodID(javaClass, "onStatus", "(Ljava/lang/String;)V");
    env->CallStaticVoidMethod(javaClass, method, jstr);

    va_end (args);
}

static void callbackUpdate(JNIEnv *env) {

    pushStatistics(env);

    jmethodID method = env->GetStaticMethodID(javaClass, "onUpdate", "()V");
    env->CallStaticVoidMethod(javaClass, method);
}

static void callbackError(JNIEnv *env, const std::string &str) {

    callbackConsole(env, str+"\r\n");

    jstring jstr = env->NewStringUTF(str.c_str());
    jmethodID method = env->GetStaticMethodID(javaClass, "onError", "(Ljava/lang/String;)V");
    env->CallStaticVoidMethod(javaClass, method, jstr);
}

// AIS-catcher model

class JSONHandler : public StreamIn<std::string> {
public:

    void Receive(const std::string *data, int len, TAG &tag) {
        bool seperate = true;

        if(json_queue.empty()) {
            seperate = false;
        }

        for(int i = 0; i < len; i++) {
            if(seperate) json_queue += ',';
            json_queue += data[i];
            seperate = true;
        }
    }
};

class NMEAcounter : public StreamIn<AIS::Message> {
    std::string list;
    bool clean = true;

public:

    void Receive(const AIS::Message *data, int len, TAG &tag) {
        std::string str;

        for (int i = 0; i < len; i++) {
            for (const auto &s: data[i].sentence) {
                str.append("\n" + s);
            }

            statistics.Total++;

            if (data[i].channel == 'A')
                statistics.ChA++;
            else
                statistics.ChB++;

            int msg = data[i].type();

            if (msg > 27 || msg < 1) statistics.Error++;
            if (msg <= 27) statistics.Msg[msg]++;

            nmea_msg += str;
        }
    }
};

// Counting Data received from device
class RAWcounter : public StreamIn<RAW> {
public:

    void Receive(const RAW *data, int len, TAG &tag) {
        const int GB = 1000000000;
        statistics.DataB += data->size;
        statistics.DataGB += statistics.DataB / GB;
        statistics.DataB %= GB;
    }
};

struct Drivers {
    Device::RTLSDR RTLSDR;
    Device::RTLTCP RTLTCP;
    Device::SpyServer SPYSERVER;
    Device::AIRSPY AIRSPY;
    Device::AIRSPYHF AIRSPYHF;
} drivers;

//Device::Type type = Device::Type::NONE;
std::vector<IO::UDP > UDP_connections;
std::vector<std::string> UDPhost;
std::vector<std::string> UDPport;

NMEAcounter NMEAcounter;
RAWcounter rawcounter;

Device::Device *device = nullptr;
AIS::Model *model = nullptr;

AIS::AISMessageDecoder ais_decoder;
IO::PropertyToJSON to_json;
JSONHandler json_handler;

bool stop = false;

extern "C"
JNIEXPORT jint JNICALL
Java_com_jvdegithub_aiscatcher_AisCatcherJava_InitNative(JNIEnv *env, jclass instance) {

    env->GetJavaVM(&javaVm);
    javaVersion = env->GetVersion();
    javaClass = (jclass) env->NewGlobalRef(instance);

    callbackConsole(env, "AIS-Catcher " VERSION "-24\n");
    memset(&statistics, 0, sizeof(statistics));

    return 0;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_jvdegithub_aiscatcher_AisCatcherJava_isStreaming(JNIEnv *, jclass) {
    if (device && device->isStreaming()) return JNI_TRUE;

    return JNI_FALSE;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_jvdegithub_aiscatcher_AisCatcherJava_applySetting(JNIEnv *env, jclass, jstring dev,
                                                           jstring setting, jstring param) {

    try {
        jboolean isCopy;
        std::string d = (env)->GetStringUTFChars(dev, &isCopy);
        std::string s = (env)->GetStringUTFChars(setting, &isCopy);
        std::string p = (env)->GetStringUTFChars(param, &isCopy);

        switch (d[0]) {
            case 't':
                callbackConsoleFormat(env, "Set RTLTCP: [%s] %s\n", s.c_str(), p.c_str());
                drivers.RTLTCP.Set(s, p);
                break;
            case 'r':
                callbackConsoleFormat(env, "Set RTLSDR: [%s] %s\n", s.c_str(), p.c_str());
                drivers.RTLSDR.Set(s, p);
                break;
            case 'm':
                callbackConsoleFormat(env, "Set AIRSPY: [%s] %s\n", s.c_str(), p.c_str());
                drivers.AIRSPY.Set(s, p);
                break;
            case 'h':
                callbackConsoleFormat(env, "Set AIRSPYHF: [%s] %s\n", s.c_str(), p.c_str());
                drivers.AIRSPYHF.Set(s, p);
                break;
            case 's':
                callbackConsoleFormat(env, "Set SpyServer: [%s] %s\n", s.c_str(), p.c_str());
                drivers.SPYSERVER.Set(s, p);
                break;

        }

    } catch (const char *msg) {
        callbackError(env, msg);
        device = nullptr;
        return -1;
    }
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_jvdegithub_aiscatcher_AisCatcherJava_Run(JNIEnv *env, jclass) {


    const int TIME_INTERVAL = 1000;
    const int TIME_MAX = (TIME_CONSTRAINT * 1000) / TIME_INTERVAL;

    try {
        callbackConsole(env, "Creating output channels\n");
        UDP_connections.resize(UDPhost.size());
        for (int i = 0; i < UDPhost.size(); i++) {
            UDP_connections[i].openConnection(UDPhost[i], UDPport[i]);
            model->Output() >> UDP_connections[i];
        }

        callbackConsole(env, "Starting device\n");
        device->Play();

        stop = false;

        callbackConsole(env, "Run started\n");

        int time_idx = 0;

        while (device->isStreaming() && !stop) {
            std::this_thread::sleep_for(std::chrono::milliseconds(TIME_INTERVAL));

            callbackUpdate(env);
            if (!nmea_msg.empty()) {
                callbackNMEA(env, nmea_msg);
                nmea_msg = "";
            }
        }

    }
    catch (const char *msg) {
        callbackError(env, msg);
    }
    catch (const std::exception &e) {
        callbackError(env, e.what());
    }

    try {
        device->Stop();

        model->Output().out.Clear();

        for (auto u: UDP_connections) u.closeConnection();
        UDP_connections.clear();
        UDPport.clear();
        UDPhost.clear();
    } catch (const char *msg) {
        callbackError(env, msg);
    }

    if (!stop) {
        callbackError(env, "Device disconnected");
    }

    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_jvdegithub_aiscatcher_AisCatcherJava_Close(JNIEnv *env, jclass) {
    callbackConsole(env, "Device closing\n");

    try {
        if (device) device->Close();
        device = nullptr;
        delete model;
        model = nullptr;
    }
    catch (const char *msg) {
        callbackError(env, msg);
        return -1;
    }
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_jvdegithub_aiscatcher_AisCatcherJava_forceStop(JNIEnv *env, jclass) {
    callbackConsole(env, "Stop requested\n");
    stop = true;
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_jvdegithub_aiscatcher_AisCatcherJava_createReceiver(JNIEnv *env, jclass, jint source,
                                                             jint fd,  jint CGF_wide, jint model_type, int FPDS) {

    callbackConsoleFormat(env, "Creating Receiver (source = %d, fd = %d, CGF wide = %d, model = %d, FPDS = %d)\n",
                          (int)source, (int)fd,(int)CGF_wide,(int)model_type,(int)FPDS);
/*
    if (device != nullptr) {
        callbackConsole(env, "Error: device already assigned.");
        return -1;
    }
*/

    if (source == 0) {
        callbackConsole(env, "Device : RTLTCP\n");
        device = &drivers.RTLTCP;
    } else if (source == 1) {
        callbackConsole(env, "Device : RTLSDR\n");
        device = &drivers.RTLSDR;
    } else if (source == 2) {
        callbackConsole(env, "Device : AIRSPY\n");
        device = &drivers.AIRSPY;
    } else if (source == 3) {
        callbackConsole(env, "Device : AIRSPYHF\n");
        device = &drivers.AIRSPYHF;
    } else if (source == 4) {
        callbackConsole(env, "Device : SPYSERVER\n");
        device = &drivers.SPYSERVER;
    } else {
        callbackConsole(env, "Support for this device not included.");
        return -1;
    }

    try {
        device->out.Clear();
        device->OpenWithFileDescriptor(fd);
        device->setFrequency(162000000);
    }
    catch (const char *msg) {
        callbackError(env, msg);
        device = nullptr;
        return -1;
    }
    catch (const std::exception& e)
    {
        callbackError(env, e.what());
        return -1;
    }

    callbackConsole(env, "Creating Model\n");
    try {

        callbackConsoleFormat(env, "Building model with sampling rate: %dK\n",
                              device->getSampleRate() / 1000);

        delete model;

        if(model_type == 0) {
            callbackConsole(env, "Model: default\n");
            model = new AIS::ModelDefault();

            std::string s = (CGF_wide == 0)?"OFF":"ON";
            model->Set("AFC_WIDE",s);
            callbackConsoleFormat(env, "AFC wide: %s\n", s.c_str());
        }
        else {
            callbackConsole(env, "Model: base (FM)\n");
            model = new AIS::ModelBase();
        }

        std::string s = (FPDS == 0)?"OFF":"ON";
        model->Set("FP_DS",s);
        callbackConsoleFormat(env, "Fixed Point Downsampler: %s\n", s.c_str());

        model->buildModel('A','B',device->getSampleRate(), false, device);

    } catch (const char *msg) {
        callbackError(env, msg);
        device = nullptr;
        return -1;
    }

    device->out >> rawcounter;
    model->Output() >> NMEAcounter;

    ais_decoder.Clear();
    to_json.out.Clear();

    model->Output() >> ais_decoder;
    ais_decoder >> to_json;
    to_json >> json_handler;

    ais_decoder.setSparse(true);

    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_jvdegithub_aiscatcher_AisCatcherJava_createUDP(JNIEnv *env, jclass clazz, jstring h,
                                                        jstring p) {
    try {
        UDPport.resize(UDPport.size() + 1);
        UDPhost.resize(UDPhost.size() + 1);

        jboolean b;
        std::string host = (env)->GetStringUTFChars(h, &b);
        std::string port = (env)->GetStringUTFChars(p, &b);

        UDPport[UDPport.size() - 1] = port;
        UDPhost[UDPhost.size() - 1] = host;

        callbackConsoleFormat(env, "UDP: %s %s\n", host.c_str(), port.c_str());

    } catch (const char *msg) {
        callbackError(env, msg);
        device = nullptr;
        return -1;
    }
    return 0;
}
extern "C"
JNIEXPORT jint JNICALL
Java_com_jvdegithub_aiscatcher_AisCatcherJava_getSampleRate(JNIEnv *env, jclass clazz) {
    if (device == NULL) return 0;

    return device->getSampleRate();
}


extern "C"
JNIEXPORT void JNICALL
Java_com_jvdegithub_aiscatcher_AisCatcherJava_00024Statistics_Init(JNIEnv *env, jclass instance) {
    javaStatisticsClass = (jclass) env->NewGlobalRef(instance);
    memset(&statistics, 0, sizeof(statistics));
}

extern "C"
JNIEXPORT void JNICALL
Java_com_jvdegithub_aiscatcher_AisCatcherJava_00024Statistics_Reset(JNIEnv *env, jclass instance) {

    memset(&statistics, 0, sizeof(statistics));

    callbackUpdate(env);
    callbackConsole(env, "");
    callbackNMEA(env, "");
}
