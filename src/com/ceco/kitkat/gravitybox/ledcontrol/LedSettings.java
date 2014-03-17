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

import java.util.HashSet;
import java.util.Set;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

public class LedSettings {

    public static final String PREF_KEY_LOCKED = "uncLocked";

    private Context mContext;
    private String mPackageName;
    private boolean mEnabled;
    private boolean mOngoing;
    private int mLedOnMs;
    private int mLedOffMs;
    private int mColor;
    private boolean mSoundOverride;
    private Uri mSoundUri;
    private boolean mSoundOnlyOnce;
    private boolean mInsistent;
    private boolean mVibrateOverride;
    private String mVibratePatternStr;
    private long[] mVibratePattern;

    public static LedSettings deserialize(Context context, String packageName) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(
                    "ledcontrol", Context.MODE_WORLD_READABLE);
            Set<String> dataSet = prefs.getStringSet(packageName, null);
            if (dataSet == null) {
                return new LedSettings(context, packageName);
            }
            return deserialize(context, packageName, dataSet);
        } catch (Throwable t) {
            t.printStackTrace();
            return new LedSettings(context, packageName);
        }
    }

    public static LedSettings deserialize(Set<String> dataSet) {
        return deserialize(null, null, dataSet);
    }

    private static LedSettings deserialize(Context context, String packageName, Set<String> dataSet) {
        LedSettings ls = new LedSettings(context, packageName);
        if (dataSet == null) {
            return ls;
        }
        for (String val : dataSet) {
            String[] data = val.split(":", 2);
            if (data[0].equals("enabled")) {
                ls.setEnabled(Boolean.valueOf(data[1]));
            } else if (data[0].equals("ongoing")) {
                ls.setOngoing(Boolean.valueOf(data[1]));
            } else if (data[0].equals("ledOnMs")) {
                ls.setLedOnMs(Integer.valueOf(data[1]));
            } else if (data[0].equals("ledOffMs")) {
                ls.setLedOffMs(Integer.valueOf(data[1]));
            } else if (data[0].equals("color")) {
                ls.setColor(Integer.valueOf(data[1]));
            } else if (data[0].equals("soundOverride")) {
                ls.setSoundOverride(Boolean.valueOf(data[1]));
            } else if (data[0].equals("sound")) {
                ls.setSoundUri(Uri.parse(data[1]));
            } else if (data[0].equals("soundOnlyOnce")) {
                ls.setSoundOnlyOnce(Boolean.valueOf(data[1]));
            } else if (data[0].equals("insistent")) {
                ls.setInsistent(Boolean.valueOf(data[1]));
            } else if (data[0].equals("vibrateOverride")) {
                ls.setVibrateOverride(Boolean.valueOf(data[1]));
            } else if (data[0].equals("vibratePattern")) {
                ls.setVibratePatternFromString(data[1]);
            }
        }
        return ls;
    }

    private LedSettings(Context context, String packageName) { 
        mContext = context;
        mPackageName = packageName;
        mEnabled = false;
        mOngoing = false;
        mLedOnMs = 1000;
        mLedOffMs = 5000;
        mColor = 0xffffffff;
        mSoundOverride = false;
        mSoundUri = null;
        mSoundOnlyOnce = false;
        mInsistent = false;
        mVibrateOverride = false;
        mVibratePatternStr = null;
        mVibratePattern = null;
    }

    protected void setEnabled(boolean enabled) {
        mEnabled = enabled;
    }

    protected void setOngoing(boolean ongoing) {
        mOngoing = ongoing;
    }

    protected void setLedOnMs(int ms) {
        mLedOnMs = ms;
    }

    protected void setLedOffMs(int ms) {
        mLedOffMs = ms;
    }

    protected void setColor(int color) {
        mColor = color;
    }

    protected void setSoundOverride(boolean override) {
        mSoundOverride = override;
    }

    protected void setSoundUri(Uri soundUri) {
        mSoundUri = soundUri;
    }

    protected void setSoundOnlyOnce(boolean onlyOnce) {
        mSoundOnlyOnce = onlyOnce;
    }

    protected void setInsistent(boolean insistent) {
        mInsistent = insistent;
    }

    protected void setVibrateOverride(boolean override) {
        mVibrateOverride = override;
    }

    protected static long[] parseVibratePatternString(String patternStr) throws Exception {
        String[] vals = patternStr.split(",");
        long[] pattern = new long[vals.length];
        for (int i=0; i<pattern.length; i++) {
            pattern[i] = Long.valueOf(vals[i]);
        }
        return pattern;
    }

    protected void setVibratePatternFromString(String pattern) {
        mVibratePatternStr = pattern == null || pattern.isEmpty() ?
                null : pattern;
        mVibratePattern = null;
        if (mVibratePatternStr != null) {
            try {
                mVibratePattern = parseVibratePatternString(mVibratePatternStr);
            } catch (Exception e) {
                mVibratePatternStr = null;
            }
        }
    }

    public String getPackageName() {
        return mPackageName;
    }

    public boolean getEnabled() {
        return mEnabled;
    }

    public boolean getOngoing() {
        return mOngoing;
    }

    public int getLedOnMs() {
        return mLedOnMs;
    }

    public int getLedOffMs() {
        return mLedOffMs;
    }

    public int getColor() {
        return mColor;
    }

    public boolean getSoundOverride() {
        return mSoundOverride;
    }

    public Uri getSoundUri() {
        return mSoundUri;
    }

    public boolean getSoundOnlyOnce() {
        return mSoundOnlyOnce;
    }

    public boolean getInsistent() {
        return mInsistent;
    }

    public boolean getVibrateOverride() {
        return mVibrateOverride;
    }

    public String getVibratePatternAsString() {
        return mVibratePatternStr;
    }

    public long[] getVibratePattern() {
        return mVibratePattern;
    }

    protected void serialize() {
        try {
            Set<String> dataSet = new HashSet<String>();
            dataSet.add("enabled:" + mEnabled);
            dataSet.add("ongoing:" + mOngoing);
            dataSet.add("ledOnMs:" + mLedOnMs);
            dataSet.add("ledOffMs:" + mLedOffMs);
            dataSet.add("color:" + mColor);
            dataSet.add("soundOverride:" + mSoundOverride);
            if (mSoundUri != null) {
                dataSet.add("sound:" + mSoundUri.toString());
            }
            dataSet.add("soundOnlyOnce:" + mSoundOnlyOnce);
            dataSet.add("insistent:" + mInsistent);
            dataSet.add("vibrateOverride:" + mVibrateOverride);
            if (mVibratePatternStr != null) {
                dataSet.add("vibratePattern:" + mVibratePatternStr);
            }
            SharedPreferences prefs = mContext.getSharedPreferences(
                    "ledcontrol", Context.MODE_WORLD_READABLE);
            prefs.edit().putStringSet(mPackageName, dataSet).commit();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public String toString() {
        String buf = "[" + mPackageName + "," + mEnabled + "," + mColor + "," + mLedOnMs + 
                "," + mLedOffMs + "," + mOngoing + ";" + mSoundOverride + ";" + 
                mSoundUri + ";" + mSoundOnlyOnce + ";" + mInsistent + "]";
        return buf;
    }
}
