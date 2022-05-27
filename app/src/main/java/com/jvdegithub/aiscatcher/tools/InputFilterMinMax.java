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

package com.jvdegithub.aiscatcher.tools;

import android.text.InputFilter;
import android.text.Spanned;

public class InputFilterMinMax implements InputFilter {

    protected int min;
    protected int max;

    public InputFilterMinMax(int m, int M)
    {
        min = m;
        max = M;
    }

    @Override
    public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {

        String r = dest.toString().substring(0, dstart) + source.subSequence(start, end) + dest.toString().substring(dend);

        if (!r.matches("^([-]?|[-]?[1-9]\\d*|[0]|[1-9]?[1-9]\\d*)")) return "";
        if(r.isEmpty()||r.equals("-")) return null;
        if ((Integer.parseInt(r) > max || Integer.parseInt(r) < min)) return "";

        return null;
    }
}