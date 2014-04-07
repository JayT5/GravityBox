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

import java.util.HashSet;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class PermissionGranter {
    public static final String TAG = "GB:PermissionGranter";
    public static final boolean DEBUG = false;

    private static final String CLASS_PACKAGE_MANAGER_SERVICE = "com.android.server.pm.PackageManagerService";
    private static final String CLASS_PACKAGE_PARSER_PACKAGE = "android.content.pm.PackageParser.Package";

    private static final String PERM_ACCESS_SURFACE_FLINGER = "android.permission.ACCESS_SURFACE_FLINGER";
    private static final String PERM_WRITE_SETTINGS = "android.permission.WRITE_SETTINGS";
    private static final String PERM_CAMERA = "android.permission.CAMERA";
    private static final String PERM_CHANGE_NETWORK_STATE = "android.permission.CHANGE_NETWORK_STATE";
    private static final String PERM_MODIFY_AUDIO_SETTINGS = "android.permission.MODIFY_AUDIO_SETTINGS";
    private static final String PERM_CAPTURE_AUDIO_OUTPUT = "android.permission.CAPTURE_AUDIO_OUTPUT";
    private static final String PERM_READ_DREAM_STATE = "android.permission.READ_DREAM_STATE";

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public static void initZygote() {
        try {
            final Class<?> pmServiceClass = XposedHelpers.findClass(CLASS_PACKAGE_MANAGER_SERVICE, null);

            XposedHelpers.findAndHookMethod(pmServiceClass, "grantPermissionsLPw",
                    CLASS_PACKAGE_PARSER_PACKAGE, boolean.class, new XC_MethodHook() {
                @SuppressWarnings("unchecked")
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    final String pkgName = (String) XposedHelpers.getObjectField(param.args[0], "packageName");

                    // GravityBox
                    if (GravityBox.PACKAGE_NAME.equals(pkgName)) {
                        final Object extras = XposedHelpers.getObjectField(param.args[0], "mExtras");
                        final HashSet<String> grantedPerms = 
                                (HashSet<String>) XposedHelpers.getObjectField(extras, "grantedPermissions");
                        final Object settings = XposedHelpers.getObjectField(param.thisObject, "mSettings");
                        final Object permissions = XposedHelpers.getObjectField(settings, "mPermissions");

                        // Add android.permission.ACCESS_SURFACE_FLINGER needed by screen recorder
                        if (!grantedPerms.contains(PERM_ACCESS_SURFACE_FLINGER)) {
                            final Object pAccessSurfaceFlinger = XposedHelpers.callMethod(permissions, "get",
                                    PERM_ACCESS_SURFACE_FLINGER);
                            grantedPerms.add(PERM_ACCESS_SURFACE_FLINGER);
                            int[] gpGids = (int[]) XposedHelpers.getObjectField(extras, "gids");
                            int[] bpGids = (int[]) XposedHelpers.getObjectField(pAccessSurfaceFlinger, "gids");
                            gpGids = (int[]) XposedHelpers.callStaticMethod(param.thisObject.getClass(), 
                                    "appendInts", gpGids, bpGids);

                            if (DEBUG) log("Permission added: " + pAccessSurfaceFlinger);
                        }

                        // Add android.permission.WRITE_SETTINGS needed by screen recorder (toggle Show Touches on/off)
                        if (!grantedPerms.contains(PERM_WRITE_SETTINGS)) {
                            final Object pWriteSettings = XposedHelpers.callMethod(permissions, "get",
                                    PERM_WRITE_SETTINGS);
                            grantedPerms.add(PERM_WRITE_SETTINGS);
                            int[] gpGids = (int[]) XposedHelpers.getObjectField(extras, "gids");
                            int[] bpGids = (int[]) XposedHelpers.getObjectField(pWriteSettings, "gids");
                            gpGids = (int[]) XposedHelpers.callStaticMethod(param.thisObject.getClass(), 
                                    "appendInts", gpGids, bpGids);

                            if (DEBUG) log(pkgName + ": Permission added: " + pWriteSettings);
                        }

                        // Add android.permission.MODIFY_AUDIO_SETTINGS needed by screen recorder
                        if (!grantedPerms.contains(PERM_MODIFY_AUDIO_SETTINGS)) {
                            final Object pAccessAudioSettings = XposedHelpers.callMethod(permissions, "get",
                                    PERM_MODIFY_AUDIO_SETTINGS);
                            grantedPerms.add(PERM_MODIFY_AUDIO_SETTINGS);
                            int[] gpGids = (int[]) XposedHelpers.getObjectField(extras, "gids");
                            int[] bpGids = (int[]) XposedHelpers.getObjectField(pAccessAudioSettings, "gids");
                            gpGids = (int[]) XposedHelpers.callStaticMethod(param.thisObject.getClass(), 
                                    "appendInts", gpGids, bpGids);

                            if (DEBUG) log(pkgName + ": Permission added: " + pAccessAudioSettings);
                        }

                        // Add android.permission.CAPTURE_AUDIO_OUTPUT needed by screen recorder
                        if (!grantedPerms.contains(PERM_CAPTURE_AUDIO_OUTPUT)) {
                            final Object pCaptureAudioOutput = XposedHelpers.callMethod(permissions, "get",
                                    PERM_CAPTURE_AUDIO_OUTPUT);
                            grantedPerms.add(PERM_CAPTURE_AUDIO_OUTPUT);
                            int[] gpGids = (int[]) XposedHelpers.getObjectField(extras, "gids");
                            int[] bpGids = (int[]) XposedHelpers.getObjectField(pCaptureAudioOutput, "gids");
                            gpGids = (int[]) XposedHelpers.callStaticMethod(param.thisObject.getClass(), 
                                    "appendInts", gpGids, bpGids);

                            if (DEBUG) log(pkgName + ": Permission added: " + pCaptureAudioOutput);
                        }

                        if (DEBUG) {
                            log("List of permissions: ");
                            for (String perm : grantedPerms) {
                                log(pkgName + ": " + perm);
                            }
                        }
                    }

                    // SystemUI
                    if (pkgName.equals("com.android.systemui")) {
                        final Object extras = XposedHelpers.getObjectField(param.args[0], "mExtras");
                        final Object sharedUser = XposedHelpers.getObjectField(extras, "sharedUser");
                        final HashSet<String> grantedPerms = 
                                (HashSet<String>) XposedHelpers.getObjectField(sharedUser, "grantedPermissions");
                        final Object settings = XposedHelpers.getObjectField(param.thisObject, "mSettings");
                        final Object permissions = XposedHelpers.getObjectField(settings, "mPermissions");

                        // Add android.permission.CAMERA needed by camera tile
                        if (!grantedPerms.contains(PERM_CAMERA)) {
                            final Object pCamera = XposedHelpers.callMethod(permissions, "get",
                                    PERM_CAMERA);
                            grantedPerms.add(PERM_CAMERA);
                            int[] gpGids = (int[]) XposedHelpers.getObjectField(sharedUser, "gids");
                            int[] bpGids = (int[]) XposedHelpers.getObjectField(pCamera, "gids");
                            gpGids = (int[]) XposedHelpers.callStaticMethod(param.thisObject.getClass(), 
                                    "appendInts", gpGids, bpGids);

                            if (DEBUG) log(pkgName + ": Permission added: " + pCamera);
                        }

                        // Add android.permission.CHANGE_NETWORK_STATE needed by Usb Tethering Tile
                        if (!grantedPerms.contains(PERM_CHANGE_NETWORK_STATE)) {
                            final Object pCns = XposedHelpers.callMethod(permissions, "get",
                                    PERM_CHANGE_NETWORK_STATE);
                            grantedPerms.add(PERM_CHANGE_NETWORK_STATE);
                            int[] gpGids = (int[]) XposedHelpers.getObjectField(sharedUser, "gids");
                            int[] bpGids = (int[]) XposedHelpers.getObjectField(pCns, "gids");
                            gpGids = (int[]) XposedHelpers.callStaticMethod(param.thisObject.getClass(), 
                                    "appendInts", gpGids, bpGids);

                            if (DEBUG) log(pkgName + ": Permission added: " + pCns);
                        }
                    }

                    // Dialer
                    if (pkgName.equals("com.google.android.dialer") || pkgName.equals("com.android.dialer")) {
                        final Object extras = XposedHelpers.getObjectField(param.args[0], "mExtras");
                        final HashSet<String> grantedPerms = 
                                (HashSet<String>) XposedHelpers.getObjectField(extras, "grantedPermissions");
                        final Object settings = XposedHelpers.getObjectField(param.thisObject, "mSettings");
                        final Object permissions = XposedHelpers.getObjectField(settings, "mPermissions");

                        // Add android.permission.READ_DREAM_STATE needed by non-intrusive call feature
                        if (!grantedPerms.contains(PERM_READ_DREAM_STATE)) {
                            final Object perm = XposedHelpers.callMethod(permissions, "get",
                                    PERM_READ_DREAM_STATE);
                            grantedPerms.add(PERM_READ_DREAM_STATE);
                            int[] gpGids = (int[]) XposedHelpers.getObjectField(extras, "gids");
                            int[] bpGids = (int[]) XposedHelpers.getObjectField(perm, "gids");
                            gpGids = (int[]) XposedHelpers.callStaticMethod(param.thisObject.getClass(), 
                                    "appendInts", gpGids, bpGids);

                            if (DEBUG) log(pkgName + ": Permission added: " + perm);
                        }
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
