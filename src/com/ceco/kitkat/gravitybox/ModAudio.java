/*
 * Copyright (C) 2013 Peter Gregus for GravityBox Project (C3C076@xda)
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

package com.ceco.kitkat.gravitybox;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.XResources;
import android.media.AudioManager;
import android.view.Surface;
import android.view.WindowManager;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModAudio {
    private static final String TAG = "GB:ModAudio";
    private static final String CLASS_AUDIO_SERVICE = "android.media.AudioService";
    private static final boolean DEBUG = false;

    private static final int STREAM_MUSIC = 3;
    private static final int VOLUME_STEPS = 30;
    private static final int DEFAULT_STREAM_TYPE_OVERRIDE_DELAY_MS = 5000;

    private static boolean mSafeMediaVolumeEnabled;
    private static boolean mVolForceMusicControl;
    private static boolean mSwapVolumeKeys;
    private static HandleChangeVolume mHandleChangeVolume;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) log("Broadcast received: " + intent.toString());
            if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_SAFE_MEDIA_VOLUME_CHANGED)) {
                mSafeMediaVolumeEnabled = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_SAFE_MEDIA_VOLUME_ENABLED, false);
                if (DEBUG) log("Safe headset media volume set to: " + mSafeMediaVolumeEnabled);
            } else if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_VOL_FORCE_MUSIC_CONTROL_CHANGED)) {
                mVolForceMusicControl = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_VOL_FORCE_MUSIC_CONTROL, false);
                if (DEBUG) log("Force music volume control set to: " + mVolForceMusicControl);
            } else if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_VOL_SWAP_KEYS_CHANGED)) {
                mSwapVolumeKeys = intent.getBooleanExtra(GravityBoxSettings.EXTRA_VOL_SWAP_KEYS, false);
                if (DEBUG) log("Swap volume keys set to: " + mSwapVolumeKeys);
            }
        }
    };

    public static void initZygote(final XSharedPreferences prefs) {
        try {
            final Class<?> classAudioService = XposedHelpers.findClass(CLASS_AUDIO_SERVICE, null);

            mSwapVolumeKeys = prefs.getBoolean(GravityBoxSettings.PREF_KEY_VOL_SWAP_KEYS, false);

            XposedBridge.hookAllConstructors(classAudioService, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_MUSIC_VOLUME_STEPS, false)) {
                        int[] maxStreamVolume = (int[])
                                XposedHelpers.getStaticObjectField(classAudioService, "MAX_STREAM_VOLUME");
                        maxStreamVolume[STREAM_MUSIC] = VOLUME_STEPS;
                        if (DEBUG) log("MAX_STREAM_VOLUME for music stream set to " + VOLUME_STEPS);
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {    
                    Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                    if (context != null) {
                        mHandleChangeVolume = new HandleChangeVolume(context);
                        XposedHelpers.findAndHookMethod(classAudioService, "adjustMasterVolume", 
                                int.class, int.class, String.class, mHandleChangeVolume);
                        XposedHelpers.findAndHookMethod(classAudioService, "adjustSuggestedStreamVolume", 
                                int.class, int.class, int.class, String.class, mHandleChangeVolume);

                        IntentFilter intentFilter = new IntentFilter();
                        intentFilter.addAction(GravityBoxSettings.ACTION_PREF_SAFE_MEDIA_VOLUME_CHANGED);
                        intentFilter.addAction(GravityBoxSettings.ACTION_PREF_VOL_FORCE_MUSIC_CONTROL_CHANGED);
                        intentFilter.addAction(GravityBoxSettings.ACTION_PREF_VOL_SWAP_KEYS_CHANGED);
                        context.registerReceiver(mBroadcastReceiver, intentFilter);
                        if (DEBUG) log("AudioService constructed. Broadcast receiver registered");
                    }
                    if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_MUSIC_VOLUME_STEPS, false)) {
                        XposedHelpers.setIntField(param.thisObject, "mSafeMediaVolumeIndex", 150);
                        if (DEBUG) log("Default mSafeMediaVolumeIndex set to 150");
                    }
                }
            });

            XResources.setSystemWideReplacement("android", "bool", "config_safe_media_volume_enabled", true);
            mSafeMediaVolumeEnabled = prefs.getBoolean(GravityBoxSettings.PREF_KEY_SAFE_MEDIA_VOLUME, false);
            if (DEBUG) log("Safe headset media volume set to: " + mSafeMediaVolumeEnabled);
            XposedHelpers.findAndHookMethod(classAudioService, "enforceSafeMediaVolume", new XC_MethodHook() {

                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!mSafeMediaVolumeEnabled) {
                        param.setResult(null);
                        return;
                    }
                }
            });

            XposedHelpers.findAndHookMethod(classAudioService, "checkSafeMediaVolume", 
                    int.class, int.class, int.class, new XC_MethodHook() {
    
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!mSafeMediaVolumeEnabled) {
                        param.setResult(true);
                        return;
                    }
                }
            });

            if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_MUSIC_VOLUME_STEPS, false)) {
                XposedHelpers.findAndHookMethod(classAudioService, "onConfigureSafeVolume",
                        boolean.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        param.setObjectExtra("gbCurSafeMediaVolIndex",
                                XposedHelpers.getIntField(param.thisObject, "mSafeMediaVolumeIndex"));
                    }
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if ((Integer) param.getObjectExtra("gbCurSafeMediaVolIndex") !=
                                XposedHelpers.getIntField(param.thisObject, "mSafeMediaVolumeIndex")) {
                            int safeMediaVolIndex = XposedHelpers.getIntField(param.thisObject, "mSafeMediaVolumeIndex") * 2;
                            XposedHelpers.setIntField(param.thisObject, "mSafeMediaVolumeIndex", safeMediaVolIndex);
                            if (DEBUG) log("onConfigureSafeVolume: mSafeMediaVolumeIndex set to " + safeMediaVolIndex);
                        }
                    }
                });
            }

            mVolForceMusicControl = prefs.getBoolean(
                    GravityBoxSettings.PREF_KEY_VOL_FORCE_MUSIC_CONTROL, false);
            XposedHelpers.findAndHookMethod(classAudioService, "getActiveStreamType",
                    int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (mVolForceMusicControl &&
                            (Integer) param.args[0] == AudioManager.USE_DEFAULT_STREAM_TYPE) {
                        final boolean voiceCapable = XposedHelpers.getBooleanField(
                                param.thisObject, "mVoiceCapable");
                        final boolean isInComm = (Boolean) XposedHelpers.callMethod(
                                param.thisObject, "isInCommunication");
                        final boolean activeMusic = (Boolean) XposedHelpers.callMethod(
                                param.thisObject, "isAfMusicActiveRecently",
                                DEFAULT_STREAM_TYPE_OVERRIDE_DELAY_MS);
                        final Object mediaFocusControl = XposedHelpers.getObjectField(
                                param.thisObject, "mMediaFocusControl");
                        final boolean isRemoteMusic = (Boolean) XposedHelpers.callMethod(
                                mediaFocusControl, "checkUpdateRemoteStateIfActive",
                                    STREAM_MUSIC);

                        if (voiceCapable && !isInComm && !activeMusic && !isRemoteMusic) {
                            param.setResult(STREAM_MUSIC);
                            if (DEBUG) log("getActiveStreamType: Forcing music stream");
                        }
                    }
                }
            });
        } catch(Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static class HandleChangeVolume extends XC_MethodHook {
        private WindowManager mWm;

        public HandleChangeVolume(Context context) {
            mWm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            if (mSwapVolumeKeys) {
                try {
                    if ((Integer) param.args[0] != 0) {
                        if (DEBUG) log("Original direction = " + param.args[0]);
                        int orientation = getDirectionFromOrientation();
                        param.args[0] = orientation * (Integer) param.args[0];
                        if (DEBUG) log("Modified direction = " + param.args[0]);
                    }
                } catch (Throwable t) {
                    XposedBridge.log(t);
                }
            }
        }

        private int getDirectionFromOrientation() {
            int rotation = mWm.getDefaultDisplay().getRotation();
            switch (rotation) {
                case Surface.ROTATION_0:
                    if (DEBUG) log("Rotation = 0");
                    return 1;
                case Surface.ROTATION_90:
                    if (DEBUG) log("Rotation = 90");
                    return -1;
                case Surface.ROTATION_180:
                    if (DEBUG) log("Rotation = 180");
                    return -1;
                case Surface.ROTATION_270:
                default:
                    if (DEBUG) log("Rotation = 270");
                    return 1;
            }
        }
    }
}
