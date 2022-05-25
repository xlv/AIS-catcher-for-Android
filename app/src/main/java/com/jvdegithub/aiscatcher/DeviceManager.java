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

package com.jvdegithub.aiscatcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;

import java.util.ArrayList;


public class DeviceManager {

    static Context context = null;

    enum DeviceType {NONE, RTLTCP, RTLSDR, AIRSPY, AIRSPYHF, HACKRF}

    static class Device {

        private final UsbDevice device;
        private final int UID;
        private final String description;
        private final DeviceType type;

        Device(UsbDevice d, String e, DeviceType t, int u) {
            device = d;
            description = e;
            type = t;
            UID = u;
        }

        String getDescription() {
            return description;
        }

        int getUID() {
            return UID;
        }

        UsbDevice getDevice() {
            return device;
        }

        DeviceType getType() {
            return type;
        }
    }

    private static final ArrayList<Device> devices = new ArrayList<>();

    static int deviceIndex = 0;
    static int deviceUID = 0;
    static DeviceType deviceType = DeviceType.NONE;

    static UsbDeviceConnection usbDeviceConnection = null;

    public static void register() {
        registerUSBBroadCast();
    }

    public static void unregister() {
        unregisterUSBBroadCast();
    }

    public static int openDevice() {

        int fd = -1;

        AisCatcherJava.callbackConsole("Opening Device Connection\n");

        if (devices.get(deviceIndex).type != DeviceType.RTLTCP) {

            UsbManager mUsbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
            UsbDeviceConnection conn = mUsbManager.openDevice(devices.get(deviceIndex).getDevice());
            fd = conn.getFileDescriptor();
            AisCatcherJava.callbackConsole("Device SN: " + conn.getSerial() + ", FD: " + fd + "\n");
        }
        return fd;
    }

    public static DeviceType getDeviceType() {
        return deviceType;
    }

    public static int getDeviceCode() {
        switch (deviceType) {
            case RTLTCP:
                return 0;
            case RTLSDR:
                return 1;
            case AIRSPY:
                return 2;
            case AIRSPYHF:
                return 3;
            case HACKRF:
                return 4;
        }
        return 0;
    }

    public static void closeDevice() {

        AisCatcherJava.callbackConsole("Closing USB connection\n");

        if (devices.get(deviceIndex).getType() != DeviceType.RTLTCP && usbDeviceConnection != null) {
            usbDeviceConnection.close();
        }
        usbDeviceConnection = null;
    }

    public static void Init(Context m) {
        context = m;
        refreshList(false);
    }

    private static boolean refreshList(boolean add) {

        devices.clear();
        devices.add(new Device(null, "RTL-TCP", DeviceType.RTLTCP, 0));

        UsbManager mUsbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

        for (UsbDevice device : mUsbManager.getDeviceList().values()) {
            if (mUsbManager.hasPermission(device)) {
                Device dev;
                if (device.getVendorId() == 7504 && device.getProductId() == 24737)
                    dev = new Device(device, "Airspy", DeviceType.AIRSPY, device.getDeviceId());
                else if (device.getVendorId() == 7504 && device.getProductId() == 24713)
                    dev = new Device(device, "HackRF", DeviceType.HACKRF, device.getDeviceId());
                else if (device.getVendorId() == 1003 && device.getProductId() == 32780)
                    dev = new Device(device, "Airspy HF+", DeviceType.AIRSPYHF, device.getDeviceId());
                else
                    dev = new Device(device, "RTL-SDR", DeviceType.RTLSDR, device.getDeviceId());
                devices.add(dev);
            }
        }

        int nDev = devices.size();
        int select = nDev - 1;
        boolean changed = true;

        if (!(add && deviceType == DeviceType.RTLTCP))
            for (int i = 0; i < devices.size(); i++)
                if (devices.get(i).getType() == deviceType && devices.get(i).getUID() == deviceUID) {
                    select = i;
                    changed = false;
                }

        setDevice(select);

        if (changed) AisCatcherJava.callbackSourceChanged();
        return changed;
    }

    public static void setDevice(int select) {
        deviceIndex = select;
        Device dev = devices.get(select);
        deviceType = dev.getType();
        deviceUID = dev.getUID();

        AisCatcherJava.callbackSourceChanged();
    }

    public static String getDeviceTypeString() {
        switch (getDeviceType()) {
            case RTLTCP:
                return "RTLTCP";
            case RTLSDR:
                return "RTLSDR";
            case AIRSPY:
                return "AIRSPY";
            case AIRSPYHF:
                return "AIRSPYHF";
            case HACKRF:
                return "HACKRF";
        }
        return "NONE";
    }

    public static String[] getDeviceStrings() {
        UsbManager mUsbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

        refreshList(false);

        String[] devs = new String[devices.size()];
        int idx = 0;
        String SN;

        for (Device dev : devices) {
            if (dev.type != DeviceType.RTLTCP) {
                UsbDeviceConnection usbDeviceConnection = mUsbManager.openDevice(dev.device);
                SN = usbDeviceConnection.getSerial();
                usbDeviceConnection.close();
            } else
                SN = null;

            devs[idx] = (idx + 1) + ": " + dev.description + (SN == null ? "" : ", SN: " + SN);
            idx++;
        }

        return devs;
    }

    public static void registerUSBBroadCast() {

        IntentFilter filter = new IntentFilter();

        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction("com.jvdegithub.aiscatcher.USB_PERMISSION");

        context.registerReceiver(mUsbReceiver, filter);
    }

    public static void unregisterUSBBroadCast() {
        context.unregisterReceiver(mUsbReceiver);
    }

    static BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {

            boolean add = false;

            String action = intent.getAction();
            String action_clean = "";

            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                action_clean = "USB device connected";
                add = true;
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                action_clean = "USB device disconnected";
            } else if ("com.jvdegithub.aiscatcher.USB_PERMISSION".equals(action)) {
                action_clean = "USB device granted extra permission";
            }

            AisCatcherJava.callbackConsole("Android: " + action_clean + ".\n");
            refreshList(add);
        }

    };
}
