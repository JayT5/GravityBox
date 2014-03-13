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

import net.margaritov.preference.colorpicker.ColorPickerPreference;

import com.ceco.kitkat.gravitybox.R;
import com.ceco.kitkat.gravitybox.preference.SeekBarPreference;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceFragment;

public class LedSettingsFragment extends PreferenceFragment {
    private static final String PREF_KEY_LED_COLOR = "pref_lc_led_color";
    private static final String PREF_KEY_LED_TIME_ON = "pref_lc_led_time_on";
    private static final String PREF_KEY_LED_TIME_OFF = "pref_lc_led_time_off";
    private static final String PREF_KEY_ONGOING = "pref_lc_ongoing";

    private ColorPickerPreference mColorPref;
    private SeekBarPreference mLedOnMsPref;
    private SeekBarPreference mLedOffMsPref;
    private CheckBoxPreference mOngoingPref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.led_control_settings);

        mColorPref = (ColorPickerPreference) findPreference(PREF_KEY_LED_COLOR);
        mLedOnMsPref = (SeekBarPreference) findPreference(PREF_KEY_LED_TIME_ON);
        mLedOffMsPref = (SeekBarPreference) findPreference(PREF_KEY_LED_TIME_OFF);
        mOngoingPref = (CheckBoxPreference) findPreference(PREF_KEY_ONGOING);
    }

    protected void initialize(LedSettings ledSettings) {
        mColorPref.setValue(ledSettings.getColor());
        mLedOnMsPref.setValue(ledSettings.getLedOnMs());
        mLedOffMsPref.setValue(ledSettings.getLedOffMs());
        mOngoingPref.setChecked(ledSettings.getOngoing());
    }

    protected int getColor() {
        return mColorPref.getValue();
    }

    protected int getLedOnMs() {
        return mLedOnMsPref.getValue();
    }

    protected int getLedOffMs() {
        return mLedOffMsPref.getValue();
    }

    protected boolean getOngoing() {
        return mOngoingPref.isChecked();
    }
}
