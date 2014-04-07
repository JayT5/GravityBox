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

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.app.Fragment;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.PowerManager;
import android.os.Vibrator;
import android.os.PowerManager.WakeLock;
import android.view.View;
import android.widget.ImageView;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.Unhook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModDialer {
    private static final String TAG = "GB:ModDialer";
    public static final List<String> PACKAGE_NAMES = new ArrayList<String>(Arrays.asList(
        "com.google.android.dialer", "com.android.dialer"));

    private static final String CLASS_CALL_CARD_FRAGMENT = "com.android.incallui.CallCardFragment";
    private static final String CLASS_DIALTACTS_ACTIVITY = "com.android.dialer.DialtactsActivity";
    private static final String CLASS_DIALTACTS_ACTIVITY_GOOGLE = 
            "com.google.android.dialer.extensions.GoogleDialtactsActivity";
    private static final String CLASS_IN_CALL_PRESENTER = "com.android.incallui.InCallPresenter";
    private static final String ENUM_IN_CALL_STATE = "com.android.incallui.InCallPresenter$InCallState";
    private static final String CLASS_CALL_LIST = "com.android.incallui.CallList";
    private static final String CLASS_CALL_CMD_CLIENT = "com.android.incallui.CallCommandClient";
    private static final String CLASS_CALL_BUTTON_FRAGMENT = "com.android.incallui.CallButtonFragment";
    private static final String CLASS_ANSWER_FRAGMENT = "com.android.incallui.AnswerFragment";
    private static final String CLASS_STATUSBAR_NOTIFIER = "com.android.incallui.StatusBarNotifier";
    private static final String CLASS_CALL = "com.android.services.telephony.common.Call";
    private static final String CLASS_CONTACT_CACHE_ENTRY = "com.android.incallui.ContactInfoCache.ContactCacheEntry";
    private static final boolean DEBUG = false;

    private static final int CALL_STATE_ACTIVE = 2;
    private static final int CALL_STATE_INCOMING = 3;
    private static final int CALL_STATE_WAITING = 4;

    private static final String ACTION_DISMISS_INCOMING_CALL = 
            "gravitybox.dialer.intent.action.DISMISS_INCOMING_CALL";
    private static final String ACTION_ANSWER_INCOMING_CALL = 
            "gravitybox.dialer.intent.action.ANSWER_INCOMING_CALL";

    private static XSharedPreferences mPrefsPhone;
    private static int mFlipAction = GravityBoxSettings.PHONE_FLIP_ACTION_NONE;
    private static Set<String> mCallVibrations;
    private static Context mContext;
    private static SensorManager mSensorManager;
    private static boolean mSensorListenerAttached = false;
    private static Object mIncomingCall;
    private static Class<?> mClassCallCmdClient;
    private static Object mPreviousCallState;
    private static Vibrator mVibrator;
    private static Handler mHandler;
    private static WakeLock mWakeLock;
    private static Unhook mSetFullscreenIntentHook;
    private static boolean mNonIntrusiveIncomingCall;
    private static Unhook mNotifBuildHook;
    private static boolean mBroadcastReceiverRegistered;
    private static Class<?> mClassInCallPresenter;
    private static boolean mNonIntrusiveIncomingCallBlocked;
    private static boolean mIsCallUiInBackground;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static PhoneSensorEventListener mPhoneSensorEventListener = 
            new PhoneSensorEventListener(new PhoneSensorEventListener.ActionHandler() {
        @Override
        public void onFaceUp() {
            if (DEBUG) log("PhoneSensorEventListener.onFaceUp");
            // do nothing
        }

        @Override
        public void onFaceDown() {
            if (DEBUG) log("PhoneSensorEventListener.onFaceDown");

            try {
                switch (mFlipAction) {
                    case GravityBoxSettings.PHONE_FLIP_ACTION_MUTE:
                        if (DEBUG) log("Muting call");
                        silenceRinger();
                        break;
                    case GravityBoxSettings.PHONE_FLIP_ACTION_DISMISS:
                        if (DEBUG) log("Rejecting call");
                        rejectCall(mIncomingCall);
                        break;
                    case GravityBoxSettings.PHONE_FLIP_ACTION_NONE:
                    default:
                        // do nothing
                }
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        }
    });

    private static void attachSensorListener() {
        if (mSensorManager == null || 
                mSensorListenerAttached ||
                mFlipAction == GravityBoxSettings.PHONE_FLIP_ACTION_NONE) return;

        mPhoneSensorEventListener.reset();
        mSensorManager.registerListener(mPhoneSensorEventListener, 
                mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), 
                SensorManager.SENSOR_DELAY_NORMAL);
        mSensorListenerAttached = true;

        if (DEBUG) log("Sensor listener attached");
    }

    private static void detachSensorListener() {
        if (mSensorManager == null || !mSensorListenerAttached) return;

        mSensorManager.unregisterListener(mPhoneSensorEventListener);
        mSensorListenerAttached = false;

        if (DEBUG) log("Sensor listener detached");
    }

    private static void silenceRinger() {
        try {
            final Class<?> classSm = XposedHelpers.findClass("android.os.ServiceManager", null);
            final Class<?> classITelephony = XposedHelpers.findClass(
                    "com.android.internal.telephony.ITelephony.Stub", null);
            final Object ts = XposedHelpers.callStaticMethod(classSm, "checkService", Context.TELEPHONY_SERVICE);            
            final Object its = XposedHelpers.callStaticMethod(classITelephony, "asInterface", ts);
            XposedHelpers.callMethod(its, "silenceRinger");
        } catch(Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void rejectCall(Object call) {
        if (call == null || 
                mClassCallCmdClient == null) return;

        try {
            final Object callCmdClient = 
                    XposedHelpers.callStaticMethod(mClassCallCmdClient, "getInstance");
            if (callCmdClient != null) {
                Class<?>[] pArgs = new Class<?>[] { call.getClass(), boolean.class, String.class };
                XposedHelpers.callMethod(callCmdClient, "rejectCall", pArgs,
                        call, false, null);
                if (DEBUG) log("Call rejected");
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void answerCall(Context context, Object call) {
        if (context == null || call == null || 
                mClassCallCmdClient == null ||
                mClassInCallPresenter == null) return;

        try { 
            final Object callCmdClient = 
                    XposedHelpers.callStaticMethod(mClassCallCmdClient, "getInstance");
            final Object inCallPresenter = 
                    XposedHelpers.callStaticMethod(mClassInCallPresenter, "getInstance");
            final Class<? extends Enum> enumInCallState = (Class<? extends Enum>)
                    XposedHelpers.findClass(ENUM_IN_CALL_STATE, context.getClassLoader());
            final int callId = (Integer) XposedHelpers.callMethod(mIncomingCall, "getCallId");
            final Object sbNotifier = XposedHelpers.getObjectField(inCallPresenter, "mStatusBarNotifier");
            Intent intent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            context.sendBroadcast(intent);
            XposedHelpers.callMethod(callCmdClient, "answerCall", callId);
            XposedHelpers.callMethod(sbNotifier, "cancelInCall");
            mNonIntrusiveIncomingCallBlocked = true;
            XposedHelpers.callMethod(sbNotifier, "updateNotificationAndLaunchIncomingCallUi",
                    Enum.valueOf(enumInCallState, "INCALL"),
                    XposedHelpers.getObjectField(inCallPresenter, "mCallList"));
            if (DEBUG) log("Call answered");
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void vibrate(int v1, int p1, int v2) {
        if (mVibrator == null) return;

        long[] pattern = new long[] { 0, v1, p1, v2 };
        mVibrator.vibrate(pattern, -1);
    }

    private static Runnable mPeriodicVibrator = new Runnable() {
        @Override
        public void run() {
            if (mWakeLock != null) {
                if (mWakeLock.isHeld()) {
                    mWakeLock.release();
                }
                mWakeLock.acquire(61000);
                if (DEBUG) log("Partial Wake Lock timeout extended");
            }
            vibrate(50, 0, 0);
            mHandler.postDelayed(this, 60000);
        }
    };

    private static void refreshPhonePrefs() {
        if (mPrefsPhone != null) {
            mPrefsPhone.reload();
            mCallVibrations = mPrefsPhone.getStringSet(
                    GravityBoxSettings.PREF_KEY_CALL_VIBRATIONS, new HashSet<String>());
            if (DEBUG) log("mCallVibrations = " + mCallVibrations.toString());

            mFlipAction = GravityBoxSettings.PHONE_FLIP_ACTION_NONE;
            try {
                mFlipAction = Integer.valueOf(mPrefsPhone.getString(
                        GravityBoxSettings.PREF_KEY_PHONE_FLIP, "0"));
                if (DEBUG) log("mFlipAction = " + mFlipAction);
            } catch (NumberFormatException e) {
                XposedBridge.log(e);
            }

            mNonIntrusiveIncomingCall = mPrefsPhone.getBoolean(
                    GravityBoxSettings.PREF_KEY_PHONE_NONINTRUSIVE_INCOMING_CALL, false);
        }
    }

    private static BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) log("Broadcast received: " + intent);
            if (intent.getAction().equals(ACTION_ANSWER_INCOMING_CALL)) {
                answerCall(context, mIncomingCall);
            } else if (intent.getAction().equals(ACTION_DISMISS_INCOMING_CALL)) {
                rejectCall(mIncomingCall);
            }
        }
    };

    private static boolean isDayDreaming() {
        try {
            Class<?> serviceManagerClass = XposedHelpers.findClass("android.os.ServiceManager", null);
            Object dmService = XposedHelpers.callStaticMethod(serviceManagerClass, "getService", "dreams");
            Class<?> dreamManagerStub = XposedHelpers.findClass("android.service.dreams.IDreamManager.Stub", null);
            Object dreamManager = XposedHelpers.callStaticMethod(dreamManagerStub, "asInterface", dmService);
            boolean isDreaming = (Boolean) XposedHelpers.callMethod(dreamManager, "isDreaming");
            if (DEBUG) log("isDayDreaming: " + isDreaming);
            return isDreaming;
        } catch (Throwable t) {
            if (DEBUG) XposedBridge.log(t);
            return false;
        }
    }

    public static void init(final XSharedPreferences prefs, ClassLoader classLoader, final String packageName) {
        mPrefsPhone = prefs;

        try {
            final Class<?> classAnswerFragment = XposedHelpers.findClass(CLASS_ANSWER_FRAGMENT, classLoader);
            final Class<?> classCallButtonFragment = XposedHelpers.findClass(CLASS_CALL_BUTTON_FRAGMENT, classLoader); 

            XposedHelpers.findAndHookMethod(classCallButtonFragment, "setEnabled", boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    final View view = ((Fragment)param.thisObject).getView();
                    final String fscPref = prefs.getString(
                            GravityBoxSettings.PREF_KEY_CALLER_FULLSCREEN_PHOTO, "disabled");
                    if (view != null && !(Boolean)param.args[0] && !fscPref.equals("disabled")) {
                        final int visibility = fscPref.equals("partial") ? View.INVISIBLE : View.GONE;
                        view.setVisibility(visibility);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(classAnswerFragment, "showAnswerUi", boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!(Boolean) param.args[0]) return;

                    refreshPhonePrefs();
                    final View v = ((Fragment) param.thisObject).getView();
                    int color = prefs.getString(GravityBoxSettings.PREF_KEY_CALLER_FULLSCREEN_PHOTO, 
                            "disabled").equals("disabled") ? Color.BLACK : 0; 
                    v.setBackgroundColor(color);
                    if (DEBUG) log("AnswerFragment showAnswerUi: background color set");
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }

        try {
            final Class<?> classCallCardFragment = XposedHelpers.findClass(CLASS_CALL_CARD_FRAGMENT, classLoader);

            XposedHelpers.findAndHookMethod(classCallCardFragment, "setDrawableToImageView",
                    ImageView.class, Drawable.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!prefs.getBoolean(
                            GravityBoxSettings.PREF_KEY_CALLER_UNKNOWN_PHOTO_ENABLE, false)) return;

                    boolean shouldShowUnknownPhoto = param.args[1] == null;
                    if (param.args[1] != null) {
                        final Fragment frag = (Fragment) param.thisObject;
                        final Resources res = frag.getResources();
                        Drawable picUnknown = res.getDrawable(res.getIdentifier("picture_unknown", "drawable",
                                        res.getResourcePackageName(frag.getId())));
                        shouldShowUnknownPhoto = ((Drawable)param.args[1]).getConstantState().equals(
                                                    picUnknown.getConstantState());
                    }

                    if (shouldShowUnknownPhoto) {
                        final Context context = ((View) param.args[0]).getContext();
                        final Context gbContext = context.createPackageContext(GravityBox.PACKAGE_NAME, 0);
                        final String path = gbContext.getFilesDir() + "/caller_photo";
                        File f = new File(path);
                        if (f.exists() && f.canRead()) {
                            Bitmap b = BitmapFactory.decodeFile(path);
                            if (b != null) {
                                param.args[1] = new BitmapDrawable(context.getResources(), b);
                                if (DEBUG) log("Unknow caller photo set");
                            }
                        }
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }

        try {
            final Class<?> classDialtactsActivity = XposedHelpers.findClass(CLASS_DIALTACTS_ACTIVITY, classLoader);

            XposedHelpers.findAndHookMethod(classDialtactsActivity, "displayFragment", Intent.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    refreshPhonePrefs();
                    if (!prefs.getBoolean(GravityBoxSettings.PREF_KEY_DIALER_SHOW_DIALPAD, false)) return;

                    Object dpFrag = XposedHelpers.getObjectField(param.thisObject, "mDialpadFragment");
                    if (dpFrag != null) {
                        final String realClassName = param.thisObject.getClass().getName();
                        if (realClassName.equals(CLASS_DIALTACTS_ACTIVITY)) {
                            XposedHelpers.callMethod(param.thisObject, "showDialpadFragment", false);
                            if (DEBUG) log("showDialpadFragment() called within " + realClassName);
                        } else if (realClassName.equals(CLASS_DIALTACTS_ACTIVITY_GOOGLE)) {
                            final Class<?> superc = param.thisObject.getClass().getSuperclass();
                            Method m = XposedHelpers.findMethodExact(superc, "showDialpadFragment", boolean.class);
                            m.invoke(param.thisObject, false);
                            if (DEBUG) log("showDialpadFragment() called within " + realClassName);
                        }
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }

        try {
            mClassInCallPresenter = XposedHelpers.findClass(CLASS_IN_CALL_PRESENTER, classLoader);
            final Class<? extends Enum> enumInCallState = (Class<? extends Enum>)
                    XposedHelpers.findClass(ENUM_IN_CALL_STATE, classLoader);
            mClassCallCmdClient = XposedHelpers.findClass(CLASS_CALL_CMD_CLIENT, classLoader);

            XposedBridge.hookAllMethods(mClassInCallPresenter, "setUp", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    mContext = (Context) param.args[0];
                    if (mSensorManager == null) {
                        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
                        if (DEBUG) log("InCallPresenter.setUp(); mSensorManager created");
                    }
                    if (mVibrator == null) {
                        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
                        if (DEBUG) log("InCallPresenter.setUp(); mVibrator created");
                    }
                    if (mHandler == null) {
                        mHandler = new Handler();
                    }
                    if (mWakeLock == null) {
                        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
                        mWakeLock  = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
                    }
                    mPreviousCallState = null;
                }
            });

            XposedBridge.hookAllMethods(mClassInCallPresenter, "onIncomingCall", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Integer state = (Integer) XposedHelpers.callMethod(param.args[0], "getState");
                    if (DEBUG) log("onIncomingCall: state = " + state);
                    if (state == CALL_STATE_INCOMING) {
                        mIncomingCall = param.args[0];
                        attachSensorListener();
                        if (mIsCallUiInBackground) {
                            final Object callCmdClient = 
                                    XposedHelpers.callStaticMethod(mClassCallCmdClient, "getInstance");
                            if (callCmdClient != null) {
                                XposedHelpers.callMethod(callCmdClient, "setSystemBarNavigationEnabled", true);
                            }
                        }
                    } else if (state == CALL_STATE_WAITING &&
                            mCallVibrations.contains(GravityBoxSettings.CV_WAITING)) {
                        vibrate(200, 300, 500);
                        if (mIsCallUiInBackground) {
                            final Object callCmdClient = 
                                    XposedHelpers.callStaticMethod(mClassCallCmdClient, "getInstance");
                            if (callCmdClient != null) {
                                XposedHelpers.callMethod(callCmdClient, "setSystemBarNavigationEnabled", true);
                            }
                        }
                    }
                }
            });

            XposedBridge.hookAllMethods(mClassInCallPresenter, "onDisconnect", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (mCallVibrations.contains(GravityBoxSettings.CV_DISCONNECTED)) {
                        if (DEBUG) log("Call disconnected; executing vibrate on call disconnected");
                        vibrate(50, 100, 50);
                    }
                }
            });

            XposedHelpers.findAndHookMethod(mClassInCallPresenter, "getPotentialStateFromCallList",
                    CLASS_CALL_LIST, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object state = param.getResult();
                    if (DEBUG) log("InCallPresenter.getPotentialStateFromCallList(); InCallState = " + state);
                    if (mPreviousCallState == null || 
                            (mPreviousCallState == Enum.valueOf(enumInCallState, "NO_CALLS") &&
                                state != Enum.valueOf(enumInCallState, "NO_CALLS"))) {
                        refreshPhonePrefs();
                    }

                    if (state != Enum.valueOf(enumInCallState, "INCOMING")) {
                        mIncomingCall = null;
                        detachSensorListener();
                    } 

                    if (state == Enum.valueOf(enumInCallState, "INCALL")) {
                        Object activeCall = XposedHelpers.callMethod(param.args[0], "getActiveCall");
                        if (activeCall != null) {
                            final int callState = (Integer) XposedHelpers.callMethod(activeCall, "getState");
                            if (DEBUG) log("Call state is: " + callState);
                            final boolean activeOutgoing = (callState == CALL_STATE_ACTIVE &&
                                    mPreviousCallState == Enum.valueOf(enumInCallState, "OUTGOING"));
                            if (activeOutgoing) {
                                if (mCallVibrations.contains(GravityBoxSettings.CV_CONNECTED)) {
                                    if (DEBUG) log("Outgoing call connected; executing vibrate on call connected");
                                    vibrate(100, 0, 0);
                                }
                                if (mCallVibrations.contains(GravityBoxSettings.CV_PERIODIC) &&
                                        mHandler != null) {
                                    if (DEBUG) log("Outgoing call connected; starting periodic vibrations");
                                    mHandler.postDelayed(mPeriodicVibrator, 45000);
                                    if (mWakeLock != null) {
                                        mWakeLock.acquire(46000);
                                        if (DEBUG) log("Partial Wake Lock acquired");
                                    }
                                }
                            }
                        }
                    } else if (state == Enum.valueOf(enumInCallState, "NO_CALLS")) {
                        if (mHandler != null) {
                            mHandler.removeCallbacks(mPeriodicVibrator);
                        }
                        if (mWakeLock != null && mWakeLock.isHeld()) {
                            mWakeLock.release();
                            if (DEBUG) log("Partial Wake Lock released");
                        }
                    }

                    mPreviousCallState = state;
                }
            });
        } catch(Throwable t) {
            XposedBridge.log(t);
        }

        try {
            final Class<?> classStatusbarNotifier = XposedHelpers.findClass(CLASS_STATUSBAR_NOTIFIER, classLoader);
            final Class<?> classCallList = XposedHelpers.findClass(CLASS_CALL_LIST, classLoader);

            XposedHelpers.findAndHookMethod(classStatusbarNotifier, "configureFullScreenIntent",
                    Notification.Builder.class, PendingIntent.class, CLASS_CALL, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    refreshPhonePrefs();
                    mIsCallUiInBackground = false;
                    if (!mNonIntrusiveIncomingCall || isDayDreaming()) return;
                    if (mNonIntrusiveIncomingCallBlocked) {
                        mNonIntrusiveIncomingCallBlocked = false;
                        return;
                    }

                    Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                    PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                    KeyguardManager kg = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
                    if (pm.isScreenOn() && !kg.isKeyguardLocked()) {
                        if (DEBUG) log("Enforcing non-intrusive call notification");
                        mSetFullscreenIntentHook = XposedHelpers.findAndHookMethod(Notification.Builder.class,
                                "setFullScreenIntent", PendingIntent.class, boolean.class, 
                                    XC_MethodReplacement.DO_NOTHING);
                        mIsCallUiInBackground = true; 
                    }
                }
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (mSetFullscreenIntentHook != null) {
                        mSetFullscreenIntentHook.unhook();
                        mSetFullscreenIntentHook = null;
                    }
                }
            });

            XposedHelpers.findAndHookMethod(classStatusbarNotifier, "buildAndSendNotification",
                    CLASS_CALL, CLASS_CONTACT_CACHE_ENTRY, boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    if (!mNonIntrusiveIncomingCall) return;

                    final Object callList = XposedHelpers.callStaticMethod(classCallList, "getInstance");
                    final Object call = XposedHelpers.callMethod(param.thisObject, "getCallToShow", callList);
                    final int callId = (Integer) XposedHelpers.callMethod(call, "getCallId");
                    final int origCallId = (Integer) XposedHelpers.callMethod(param.args[0], "getCallId");
                    if (call == null || callId != origCallId) {
                        if (DEBUG) log("call is null or call ID != original call ID");
                        return;
                    }

                    final int state = (Integer) XposedHelpers.callMethod(param.args[0], "getState");
                    if (state == CALL_STATE_INCOMING) {
                        final Context context = (Context) XposedHelpers.getObjectField(
                                param.thisObject, "mContext");
                        final Resources res = context.getResources();
                        if (!mBroadcastReceiverRegistered) {
                            IntentFilter intentFilter = new IntentFilter();
                            intentFilter.addAction(ACTION_ANSWER_INCOMING_CALL);
                            intentFilter.addAction(ACTION_DISMISS_INCOMING_CALL);
                            context.registerReceiver(mBroadcastReceiver, intentFilter);
                            mBroadcastReceiverRegistered = true;
                        }
                        mNotifBuildHook = XposedHelpers.findAndHookMethod(
                                classStatusbarNotifier, "getNotificationBuilder", new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param2) throws Throwable {
                                if (DEBUG) log("getNotificationBuilder: adding actions");
                                Notification.Builder b = (Notification.Builder) param2.getResult();
                                // Answer action
                                int iconId = res.getIdentifier(
                                        "stat_sys_phone_call", "drawable", "com.android.dialer");
                                String label = context.getString(res.getIdentifier(
                                        "description_target_answer", "string", "com.android.dialer"));
                                Intent iAnswer = new Intent(ACTION_ANSWER_INCOMING_CALL);
                                PendingIntent piAnswer = PendingIntent.getBroadcast(context, 0, iAnswer, 0);
                                b.addAction(iconId, label, piAnswer);
                                // Reject action
                                iconId = res.getIdentifier(
                                        "stat_sys_phone_call_end", "drawable", "com.android.dialer");
                                label = context.getString(res.getIdentifier(
                                        "description_target_decline", "string", "com.android.dialer"));
                                Intent iDismiss = new Intent(ACTION_DISMISS_INCOMING_CALL);
                                PendingIntent piDismiss = PendingIntent.getBroadcast(context, 0, iDismiss, 0);
                                b.addAction(iconId, label, piDismiss);
                                // set ticker
                                final boolean isConference = 
                                        (Boolean) XposedHelpers.callMethod(call, "isConferenceCall");
                                final String contentTitle = (String) XposedHelpers.callMethod(
                                        param.thisObject, "getContentTitle", param.args[1], isConference);
                                b.setTicker(contentTitle);
                            }
                        });
                    }
                }
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (mNotifBuildHook != null) {
                        mNotifBuildHook.unhook();
                        mNotifBuildHook = null;
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
