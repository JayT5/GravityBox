/*
 * Copyright (C) 2014 Peter Gregus for GravityBox Project (C3C076@xda)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ceco.kitkat.gravitybox.ledcontrol;

import java.util.Calendar;
import java.util.GregorianCalendar;

import com.ceco.kitkat.gravitybox.ModLedControl;
import com.ceco.kitkat.gravitybox.Utils;

import de.robv.android.xposed.XSharedPreferences;

public class QuietHours {
    public enum Mode { ON, OFF, AUTO };

    public boolean uncLocked;
    public boolean enabled;
    long start;
    long end;
    long startAlt;
    long endAlt;
    boolean muteLED;
    public boolean showStatusbarIcon;
    public Mode mode;

    public QuietHours(XSharedPreferences prefs) {
        uncLocked = prefs.getBoolean(LedSettings.PREF_KEY_LOCKED, false);
        enabled = prefs.getBoolean(QuietHoursActivity.PREF_KEY_QH_ENABLED, false);
        start = prefs.getLong(QuietHoursActivity.PREF_KEY_QH_START, 0);
        end = prefs.getLong(QuietHoursActivity.PREF_KEY_QH_END, 0);
        startAlt = prefs.getLong(QuietHoursActivity.PREF_KEY_QH_START_ALT, 0);
        endAlt = prefs.getLong(QuietHoursActivity.PREF_KEY_QH_END_ALT, 0);
        muteLED = prefs.getBoolean(QuietHoursActivity.PREF_KEY_QH_MUTE_LED, false);
        showStatusbarIcon = prefs.getBoolean(QuietHoursActivity.PREF_KEY_QH_STATUSBAR_ICON, true);
        mode = Mode.valueOf(prefs.getString(QuietHoursActivity.PREF_KEY_QH_MODE, "AUTO"));
    }

    public boolean quietHoursActive() {
        if (!enabled) return false;

        if (mode != Mode.AUTO) {
            return (mode == Mode.ON ? true : false);
        }

        int startMin, endMin;
        Calendar c = new GregorianCalendar();
        c.setTimeInMillis(System.currentTimeMillis());
        int curMin = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        int dayOfWeek = c.get(Calendar.DAY_OF_WEEK);
        boolean isFriday = dayOfWeek == Calendar.FRIDAY;
        boolean isSunday = dayOfWeek == Calendar.SUNDAY;
        long s = start; 
        long e = end;
        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
            s = startAlt;
            e = endAlt;
        }

        // special logic for Friday and Sunday
        // we assume people stay up longer on Friday  
        if (isFriday) {
            c.setTimeInMillis(end);
            endMin = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
            if (curMin > endMin) {
                // we are after previous QH
                c.setTimeInMillis(startAlt);
                startMin = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
                c.setTimeInMillis(endAlt);
                endMin = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
                if (startMin > endMin) {
                    // weekend range spans midnight
                    // let's apply weekend start time instead
                    s = startAlt;
                    if (ModLedControl.DEBUG) ModLedControl.log("Applying weekend start time for Friday");
                } else {
                    // weekend range happens on the next day
                    if (ModLedControl.DEBUG) ModLedControl.log("Ignoring quiet hours for Friday");
                    return false;
                }
            }
        }
        // we assume people go to sleep earlier on Sunday
        if (isSunday) {
            c.setTimeInMillis(endAlt);
            endMin = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
            if (curMin > endMin) {
                // we are after previous QH
                c.setTimeInMillis(start);
                startMin = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
                c.setTimeInMillis(end);
                endMin = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
                if (startMin > endMin) {
                    // weekday range spans midnight
                    // let's apply weekday start time instead
                    s = start;
                    if (ModLedControl.DEBUG) ModLedControl.log("Applying weekday start time for Sunday");
                } else {
                    // weekday range happens on the next day
                    if (ModLedControl.DEBUG) ModLedControl.log("Ignoring quiet hours for Sunday");
                    return false;
                }
            }
        }

        return (Utils.isTimeOfDayInRange(System.currentTimeMillis(), s, e));
    }

    public boolean quietHoursActiveIncludingLED() {
        return quietHoursActive() && muteLED;
    }
}
