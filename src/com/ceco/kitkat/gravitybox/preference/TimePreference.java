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
package com.ceco.kitkat.gravitybox.preference;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TimePicker;

public class TimePreference extends DialogPreference {
    private Calendar mCalendar;
    private TimePicker mPicker = null;

    public TimePreference(Context context) {
        this(context, null);
    }

    public TimePreference(Context ctxt, AttributeSet attrs) {
        this(ctxt, attrs, 0);
    }

    public TimePreference(Context ctxt, AttributeSet attrs, int defStyle) {
        super(ctxt, attrs, defStyle);

        setPositiveButtonText(android.R.string.ok);
        setNegativeButtonText(android.R.string.cancel);
        mCalendar = new GregorianCalendar();
    }

    @Override
    protected View onCreateDialogView() {
        mPicker = new TimePicker(getContext());
        mPicker.setIs24HourView(DateFormat.is24HourFormat(getContext()));
        return mPicker;
    }

    @Override
    protected void onBindDialogView(View v) {
        super.onBindDialogView(v);
        mPicker.setCurrentHour(mCalendar.get(Calendar.HOUR_OF_DAY));
        mPicker.setCurrentMinute(mCalendar.get(Calendar.MINUTE));
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            mCalendar.set(Calendar.HOUR_OF_DAY, mPicker.getCurrentHour());
            mCalendar.set(Calendar.MINUTE, mPicker.getCurrentMinute());

            setSummary(getSummary());
            if (callChangeListener(mCalendar.getTimeInMillis())) {
                persistLong(mCalendar.getTimeInMillis());
                notifyChanged();
            }
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        if (restoreValue) {
            mCalendar.setTimeInMillis(getPersistedLong(System.currentTimeMillis()));
        } else {
            mCalendar.setTimeInMillis(defaultValue == null ?
                    System.currentTimeMillis() : Long.parseLong((String) defaultValue));
            if (shouldPersist()) {
                persistLong(mCalendar.getTimeInMillis());
            }
        }
        setSummary(getSummary());
    }

    @Override
    public CharSequence getSummary() {
        if (mCalendar == null) {
            return null;
        }
        return DateFormat.getTimeFormat(getContext()).format(new Date(mCalendar.getTimeInMillis()));
    }
}
