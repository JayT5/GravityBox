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

import android.app.Activity;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.provider.Settings;

public class LedSettingsFragment extends PreferenceFragment {
    private static final String PREF_KEY_LED_COLOR = "pref_lc_led_color";
    private static final String PREF_KEY_LED_TIME_ON = "pref_lc_led_time_on";
    private static final String PREF_KEY_LED_TIME_OFF = "pref_lc_led_time_off";
    private static final String PREF_KEY_ONGOING = "pref_lc_ongoing";
    private static final String PREF_KEY_NOTIF_SOUND_OVERRIDE = "pref_lc_notif_sound_override";
    private static final String PREF_KEY_NOTIF_SOUND = "pref_lc_notif_sound";
    private static final String PREF_KEY_NOTIF_SOUND_ONLY_ONCE = "pref_lc_notif_sound_only_once";
    private static final String PREF_KEY_NOTIF_INSISTENT = "pref_lc_notif_insistent";

    private static final int REQ_PICK_SOUND = 101;

    private ColorPickerPreference mColorPref;
    private SeekBarPreference mLedOnMsPref;
    private SeekBarPreference mLedOffMsPref;
    private CheckBoxPreference mOngoingPref;
    private Preference mNotifSoundPref;
    private CheckBoxPreference mNotifSoundOverridePref;
    private Uri mSoundUri;
    private CheckBoxPreference mNotifSoundOnlyOncePref;
    private CheckBoxPreference mNotifInsistentPref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.led_control_settings);

        mColorPref = (ColorPickerPreference) findPreference(PREF_KEY_LED_COLOR);
        mLedOnMsPref = (SeekBarPreference) findPreference(PREF_KEY_LED_TIME_ON);
        mLedOffMsPref = (SeekBarPreference) findPreference(PREF_KEY_LED_TIME_OFF);
        mOngoingPref = (CheckBoxPreference) findPreference(PREF_KEY_ONGOING);
        mNotifSoundOverridePref = (CheckBoxPreference) findPreference(PREF_KEY_NOTIF_SOUND_OVERRIDE);
        mNotifSoundPref = findPreference(PREF_KEY_NOTIF_SOUND);
        mNotifSoundOnlyOncePref = (CheckBoxPreference) findPreference(PREF_KEY_NOTIF_SOUND_ONLY_ONCE);
        mNotifInsistentPref = (CheckBoxPreference) findPreference(PREF_KEY_NOTIF_INSISTENT);
    }

    protected void initialize(LedSettings ledSettings) {
        mColorPref.setValue(ledSettings.getColor());
        mLedOnMsPref.setValue(ledSettings.getLedOnMs());
        mLedOffMsPref.setValue(ledSettings.getLedOffMs());
        mOngoingPref.setChecked(ledSettings.getOngoing());
        mNotifSoundOverridePref.setChecked(ledSettings.getSoundOverride());
        mSoundUri = ledSettings.getSoundUri();
        mNotifSoundOnlyOncePref.setChecked(ledSettings.getSoundOnlyOnce());
        mNotifInsistentPref.setChecked(ledSettings.getInsistent());
        updateSoundPrefSummary();
    }

    private void updateSoundPrefSummary() {
        mNotifSoundPref.setSummary(getString(R.string.lc_notif_sound_none));
        if (mSoundUri != null) {
            Ringtone r = RingtoneManager.getRingtone(getActivity(), mSoundUri);
            if (r != null) {
                mNotifSoundPref.setSummary(r.getTitle(getActivity()));
            } else {
                mSoundUri = null;
            }
        }
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

    protected boolean getSoundOverride() {
        return mNotifSoundOverridePref.isChecked();
    }

    protected Uri getSoundUri() {
        return mSoundUri;
    }

    protected boolean getSoundOnlyOnce() {
        return mNotifSoundOnlyOncePref.isChecked();
    }

    protected boolean getInsistent() {
        return mNotifInsistentPref.isChecked();
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen prefScreen, Preference pref) {
        if (pref == mNotifSoundPref) {
            Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, mSoundUri);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, 
                    Settings.System.DEFAULT_NOTIFICATION_URI);
            startActivityForResult(intent, REQ_PICK_SOUND);
            return true;
        }
        return super.onPreferenceTreeClick(prefScreen, pref);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_PICK_SOUND && resultCode == Activity.RESULT_OK && data != null) {
            mSoundUri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            updateSoundPrefSummary();
        }
    }
}
