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
import java.util.Locale;

import com.ceco.kitkat.gravitybox.GravityBoxSettings;
import com.ceco.kitkat.gravitybox.ModHwKeys;
import com.ceco.kitkat.gravitybox.R;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class LedSettingsActivity extends Activity implements OnClickListener {
    protected static final String EXTRA_PACKAGE_NAME = "packageName";
    protected static final String EXTRA_APP_NAME = "appName";

    private static final int NOTIF_ID = 2049;

    private LedSettings mLedSettings;
    private LedSettingsFragment mPrefsFragment;
    private Button mBtnPreview;
    private Button mBtnSave;
    private Button mBtnCancel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        File file = new File(getFilesDir() + "/" + GravityBoxSettings.FILE_THEME_DARK_FLAG);
        if (file.exists()) {
            setTheme(android.R.style.Theme_Holo);
        }

        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        if (intent == null || !intent.hasExtra(EXTRA_PACKAGE_NAME) ||
                intent.getStringExtra(EXTRA_PACKAGE_NAME) == null) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        mLedSettings = LedSettings.deserialize(this, intent.getStringExtra(EXTRA_PACKAGE_NAME));
        setContentView(R.layout.led_settings_activity);

        mPrefsFragment = (LedSettingsFragment) getFragmentManager().findFragmentById(R.id.prefs_fragment);
        mPrefsFragment.initialize(mLedSettings);

        mBtnPreview = (Button) findViewById(R.id.btnPreview);
        mBtnPreview.setOnClickListener(this);

        mBtnSave = (Button) findViewById(R.id.btnSave);
        mBtnSave.setOnClickListener(this);

        mBtnCancel = (Button) findViewById(R.id.btnCancel);
        mBtnCancel.setOnClickListener(this);

        setTitle(intent.getStringExtra(EXTRA_APP_NAME));
    }

    @Override
    public void onResume() {
        super.onResume();
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(NOTIF_ID);
    }

    @Override
    public void onClick(View v) {
        if (v == mBtnPreview) {
            previewSettings();
        } else if (v == mBtnSave) {
            saveSettings();
        } else if (v == mBtnCancel) {
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    private void previewSettings() {
        Notification.Builder builder = new Notification.Builder(this)
            .setContentTitle(getString(R.string.lc_preview_notif_title))
            .setContentText(String.format(Locale.getDefault(),
                    getString(R.string.lc_preview_notif_text), getTitle()))
            .setSmallIcon(R.drawable.ic_launcher)
            .setLights(mPrefsFragment.getColor(), mPrefsFragment.getLedOnMs(), mPrefsFragment.getLedOffMs());
        final Notification n = builder.build();
        if (mPrefsFragment.getSoundOverride() && mPrefsFragment.getSoundUri() != null) {
            n.defaults &= ~Notification.DEFAULT_SOUND;
            n.sound = mPrefsFragment.getSoundUri();
        }
        if (mPrefsFragment.getInsistent()) {
            n.flags |= Notification.FLAG_INSISTENT;
        }
        if (mPrefsFragment.getVibrateOverride()) {
            try {
                long[] pattern = LedSettings.parseVibratePatternString(
                        mPrefsFragment.getVibratePatternAsString());
                n.defaults &= ~Notification.DEFAULT_VIBRATE;
                n.vibrate = pattern;
            } catch (Exception e) {
                Toast.makeText(this, getString(R.string.lc_vibrate_pattern_invalid),
                        Toast.LENGTH_SHORT).show();
            }
        }
        Intent intent = new Intent(ModHwKeys.ACTION_SLEEP);
        sendBroadcast(intent);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(NOTIF_ID,  n);
            }
        }, 1000);
    }

    private void saveSettings() {
        mLedSettings.setColor(mPrefsFragment.getColor());
        mLedSettings.setLedOnMs(mPrefsFragment.getLedOnMs());
        mLedSettings.setLedOffMs(mPrefsFragment.getLedOffMs());
        mLedSettings.setOngoing(mPrefsFragment.getOngoing());
        mLedSettings.setSoundOverride(mPrefsFragment.getSoundOverride());
        mLedSettings.setSoundUri(mPrefsFragment.getSoundUri());
        mLedSettings.setSoundOnlyOnce(mPrefsFragment.getSoundOnlyOnce());
        mLedSettings.setInsistent(mPrefsFragment.getInsistent());
        mLedSettings.setVibrateOverride(mPrefsFragment.getVibrateOverride());
        mLedSettings.setVibratePatternFromString(mPrefsFragment.getVibratePatternAsString());
        mLedSettings.serialize();
        Intent intent = new Intent();
        intent.putExtra(EXTRA_PACKAGE_NAME, mLedSettings.getPackageName());
        setResult(RESULT_OK, intent);
        finish();
    }
}
