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

package com.jvdegithub.aiscatcher.ui.main;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.jvdegithub.aiscatcher.AisCatcherJava;
import com.jvdegithub.aiscatcher.R;

public class StatisticsFragment extends Fragment {

    TextView MB, Total, ChannelA, ChannelB, Msg123, Msg5, Msg1819, Msg24, MsgOther;

    public static StatisticsFragment newInstance() {
        return new StatisticsFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View rootview = inflater.inflate(R.layout.fragment_statistics, container, false);

        MB = rootview.findViewById(R.id.MBs);
        Total = rootview.findViewById(R.id.totalMSG);
        ChannelA = rootview.findViewById(R.id.ChannelAMSG);
        ChannelB = rootview.findViewById(R.id.ChannelBMSG);
        Msg123 = rootview.findViewById(R.id.MSG123);
        Msg5 = rootview.findViewById(R.id.MSG5);
        Msg1819 = rootview.findViewById(R.id.MSG1819);
        Msg24 = rootview.findViewById(R.id.MSG24);
        MsgOther = rootview.findViewById(R.id.MSGOther);

        Update();
        return rootview;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    public void Update() {
        getActivity().runOnUiThread(() -> {
            MB.setText(String.format("%d MB", AisCatcherJava.getStatistics_Data()));
            Total.setText(String.format("%d", AisCatcherJava.getStatistics_Total()));
            ChannelA.setText(String.format("%d", AisCatcherJava.getStatistics_ChA()));
            ChannelB.setText(String.format("%d", AisCatcherJava.getStatistics_ChB()));
            Msg123.setText(String.format("%d", AisCatcherJava.getStatistics_Msg123()));
            Msg5.setText(String.format("%d", AisCatcherJava.getStatistics_Msg5()));
            Msg1819.setText(String.format("%d", AisCatcherJava.getStatistics_Msg1819()));
            Msg24.setText(String.format("%d", AisCatcherJava.getStatistics_Msg24()));
            MsgOther.setText(String.format("%d", AisCatcherJava.getStatistics_MsgOther()));
        });
    }
}