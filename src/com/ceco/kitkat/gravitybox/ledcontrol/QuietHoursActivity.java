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

import java.io.File;

import com.ceco.kitkat.gravitybox.GravityBoxSettings;
import com.ceco.kitkat.gravitybox.R;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceFragment;

public class QuietHoursActivity extends Activity {

    public static final String PREF_KEY_QH_ENABLED = "pref_lc_qh_enabled";
    public static final String PREF_KEY_QH_START = "pref_lc_qh_start";
    public static final String PREF_KEY_QH_END = "pref_lc_qh_end";
    public static final String PREF_KEY_QH_MUTE_LED = "pref_lc_qh_mute_led";

    public static final String ACTION_QUIET_HOURS_CHANGED = 
            "gravitybox.intent.action.QUIET_HOURS_CHANGED";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        File file = new File(getFilesDir() + "/" + GravityBoxSettings.FILE_THEME_DARK_FLAG);
        if (file.exists()) {
            setTheme(android.R.style.Theme_Holo);
        }

        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction().replace(android.R.id.content, new PrefsFragment()).commit();
        }
    }

    public static class PrefsFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener {

        private SharedPreferences mPrefs;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            getPreferenceManager().setSharedPreferencesName("ledcontrol");
            getPreferenceManager().setSharedPreferencesMode(Context.MODE_WORLD_READABLE);
            mPrefs = getPreferenceManager().getSharedPreferences();

            addPreferencesFromResource(R.xml.led_control_quiet_hours_settings);
        }

        @Override
        public void onResume() {
            super.onResume();
            mPrefs.registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            mPrefs.unregisterOnSharedPreferenceChangeListener(this);
            super.onPause();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            Intent intent = new Intent(ACTION_QUIET_HOURS_CHANGED);
            prefs.edit().commit();
            getActivity().sendBroadcast(intent);
        }
    }
}
