/*
 * Copyright (C) 2014 The Android Open Source Project
 *
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

package com.example.android.sunshine.wearable;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.VectorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
    private static final String TAG = SunshineWatchFace.class.getSimpleName();
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
        static final String COLON_STRING = ":";
        static final String FORECAST_PATH = "/forecast";
        static final String WEATHERID_KEY = "com.example.android.sunshine.app.weatherid";
        static final String HIGH_TEMP_KEY = "com.example.android.sunshine.app.hightemp";
        static final String LOW_TEMP_KEY = "com.example.android.sunshine.app.lowtemp";

        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mHourTextPaint;
        Paint mColonPaint;
        Paint mMinuteTextPaint;
        Paint mAmPmPaint;
        Paint mDateTextPaint;
        Paint mLinePaint;
        Paint mHighTempTextPaint;
        Paint mLowTempTextPaint;
        Paint mIconPaint;
        boolean mAmbient;

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                initFormats();
                invalidate();
            }
        };

        float mTimeYOffset;
        float mColonWidth;
        float mLineHeight;
        float mCenterLineLength;
        float mIconXOffsetFromCenter;
        float mIconSize;

        Bitmap mIconBitmap;

        final int mLightTextAlpha = 192;

        Calendar mCalendar;
        Date mDate;
        SimpleDateFormat mDateFormat;
        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        String mAmString;
        String mPmString;

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFace.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        int mWeatherId = 0;
        String mHighTemp;
        String mLowTemp;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(false)
                    .build());
            Resources resources = SunshineWatchFace.this.getResources();

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mHourTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mColonPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mMinuteTextPaint = createRobotoLightTextPaint(resources.getColor(R.color.digital_text));

            mAmPmPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mDateTextPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mDateTextPaint.setAlpha(mLightTextAlpha);
            mDateTextPaint.setTextAlign(Paint.Align.CENTER);

            mLinePaint = createLinePaint();

            mHighTempTextPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mLowTempTextPaint = createRobotoLightTextPaint(resources.getColor(R.color.digital_text));
            mLowTempTextPaint.setAlpha(mLightTextAlpha);

            mIconSize = resources.getDimension(R.dimen.icon_size);
            mIconPaint = new Paint();

            mCalendar = Calendar.getInstance();
            mDate = new Date();
            initFormats();

            mAmString = resources.getString(R.string.digital_am);
            mPmString = resources.getString(R.string.digital_pm);

            mLineHeight = resources.getDimension(R.dimen.digital_line_height);

            mCenterLineLength = resources.getDimension(R.dimen.center_line_length);
            mIconXOffsetFromCenter = resources.getDimension(R.dimen.icon_xoffset_from_center);
        }

        private void initFormats() {
            mDateFormat = new SimpleDateFormat("EEE, MMM dd yyyy", Locale.ENGLISH);
            mDateFormat.setCalendar(mCalendar);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        private Paint createRobotoLightTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            final Typeface robotoLight = Typeface.createFromAsset(getAssets(), "fonts/Roboto-Light.ttf");
            paint.setTypeface(robotoLight);
            paint.setAntiAlias(true);
            return paint;
        }

        private Paint createLinePaint() {
            Paint paint = new Paint();
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(1);
            return paint;
        }

        private Bitmap createScaledBitmapForResource(int resId) {
            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), resId);
            float scale = mIconSize / (float)bitmap.getWidth();
            if (scale != 1.0f) {
                bitmap = Bitmap.createScaledBitmap(
                        bitmap,
                        (int)(bitmap.getWidth() * scale),
                        (int)(bitmap.getHeight() * scale),
                        true);
            }
            return bitmap;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                mGoogleApiClient.connect();

                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                initFormats();
            } else {
                unregisterReceiver();

                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
//                    Wearable.CapabilityApi.removeLocalCapability(mGoogleApiClient, FORECAST_CAPABILITY);
                    mGoogleApiClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFace.this.getResources();
            boolean isRound = insets.isRound();

            mTimeYOffset = resources.getDimension(isRound
                    ? R.dimen.digital_y_offset_round : R.dimen.digital_y_offset);

            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
            mHourTextPaint.setTextSize(textSize);
            mMinuteTextPaint.setTextSize(textSize);
            mColonPaint.setTextSize(textSize);

            float amPmTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_am_pm_text_size_round : R.dimen.digital_am_pm_text_size);
            mAmPmPaint.setTextSize(amPmTextSize);

            float dateTextSize = resources.getDimension(isRound
                    ? R.dimen.date_text_size_round : R.dimen.date_text_size);
            mDateTextPaint.setTextSize(dateTextSize);

            float temperatureTextSize = resources.getDimension(isRound
                    ? R.dimen.temperature_text_size_round : R.dimen.temperature_text_size);
            mHighTempTextPaint.setTextSize(temperatureTextSize);
            mLowTempTextPaint.setTextSize(temperatureTextSize);

            mColonWidth = mColonPaint.measureText(COLON_STRING);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        private Bitmap createAmbientBitmap(VectorDrawable vectorDrawable) {
            Bitmap bitmap = Bitmap.createBitmap((int)mIconSize,
                    (int)mIconSize, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            vectorDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            vectorDrawable.draw(canvas);
            return bitmap;
        }

        private void updateIcon() {
            final int resourceId = Utility.getIconResourceForWeatherCondition(mWeatherId, mAmbient);
            if (resourceId == -1) return;

            if (mAmbient) {
                VectorDrawable vectorDrawable = (VectorDrawable) getDrawable(resourceId);
                mIconBitmap = createAmbientBitmap(vectorDrawable);
            }else {
                mIconBitmap = createScaledBitmapForResource(resourceId);
            }
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mHourTextPaint.setAntiAlias(!inAmbientMode);
                    mColonPaint.setAntiAlias(!inAmbientMode);
                    mMinuteTextPaint.setAntiAlias(!inAmbientMode);
                    mAmPmPaint.setAntiAlias(!inAmbientMode);
                    mDateTextPaint.setAntiAlias(!inAmbientMode);
                    mHighTempTextPaint.setAntiAlias(!inAmbientMode);
                    mLowTempTextPaint.setAntiAlias(!inAmbientMode);
                    mIconPaint.setAntiAlias(!inAmbientMode);
                }

                updateIcon();

                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);
            boolean is24Hour = DateFormat.is24HourFormat(SunshineWatchFace.this);

            // Draw the time. We calculate the x offset dynamically since we need to center the string.
            // For this we compute the total width of the text, subtract it from the screen width and half the difference.
            String hourString;
            if (is24Hour) {
                hourString = String.format(Locale.ENGLISH, "%02d", mCalendar.get(Calendar.HOUR_OF_DAY));
            }else {
                int hour = mCalendar.get(Calendar.HOUR);
                if (hour == 0)
                    hour = 12;
                hourString = String.valueOf(hour);
            }
            float hourWidth = mHourTextPaint.measureText(hourString);

            String minuteString = String.format(Locale.ENGLISH, "%02d", mCalendar.get(Calendar.MINUTE));
            float minuteWidth = mMinuteTextPaint.measureText(minuteString);

            String amOrPmString;
            float amPmWidth;
            if (is24Hour) {
                amOrPmString = "";
                amPmWidth = 0;
            }else {
                amOrPmString = (mCalendar.get(Calendar.AM_PM) == Calendar.AM) ? mAmString : mPmString;
                amPmWidth = mAmPmPaint.measureText(amOrPmString);
            }

            float totalTimeWidth = hourWidth + mColonWidth + minuteWidth + amPmWidth;

            float xTime = ((float)bounds.width() - totalTimeWidth) / 2.0f;
            canvas.drawText(hourString, xTime, mTimeYOffset, mHourTextPaint);
            xTime += hourWidth;
            canvas.drawText(COLON_STRING, xTime, mTimeYOffset, mColonPaint);
            xTime += mColonWidth;
            canvas.drawText(minuteString, xTime, mTimeYOffset, mMinuteTextPaint);
            if (!is24Hour) {
                xTime += minuteWidth;
                canvas.drawText(amOrPmString, xTime, mTimeYOffset, mAmPmPaint);
            }

            // Draw the date
            String dateString = mDateFormat.format(mDate).toUpperCase();
            float xDate = bounds.centerX();
            float yDate = mTimeYOffset + mLineHeight;
            canvas.drawText(dateString, xDate, yDate, mDateTextPaint);

            // Draw the line
            float xLineStart = (float)bounds.centerX() - mCenterLineLength/2f;
            float yLine = mTimeYOffset + mLineHeight * 1.75f;
            canvas.drawLine(xLineStart, yLine, xLineStart + mCenterLineLength, yLine, mLinePaint);

            // Draw the temperature
            if (mHighTemp!=null && mLowTemp!=null) {
                float xTemperature = xLineStart;
                float yTemperature = mTimeYOffset + mLineHeight * 3.25f;
                String highTemperature = mHighTemp;
                canvas.drawText(highTemperature, xTemperature, yTemperature, mHighTempTextPaint);
                xTemperature += mHighTempTextPaint.measureText(highTemperature + " ");
                String lowTemperature = mLowTemp;
                canvas.drawText(lowTemperature, xTemperature, yTemperature, mLowTempTextPaint);
            }


            // Draw the icon
            if (mIconBitmap != null) {
                float xIcon = xLineStart - mIconXOffsetFromCenter;
                float yIcon = mTimeYOffset + mLineHeight * 2.0f;
                canvas.drawBitmap(mIconBitmap, xIcon, yIcon, mIconPaint);
            }
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Log.d(TAG, "onConnected: " + bundle);
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);

            PendingResult<DataItemBuffer> dataItems = Wearable.DataApi.getDataItems(mGoogleApiClient);
            dataItems.setResultCallback(new ResultCallback<DataItemBuffer>() {
                @Override
                public void onResult(@NonNull DataItemBuffer dataItems) {
                    for (DataItem dataItem : dataItems) {
                        if (dataItem.getUri().getPath().equals(FORECAST_PATH)) {
                            DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();
                            mWeatherId = dataMap.getInt(WEATHERID_KEY);
                            mHighTemp = dataMap.getString(HIGH_TEMP_KEY);
                            mLowTemp = dataMap.getString(LOW_TEMP_KEY);
                            updateIcon();
                            invalidate();
                        }
                    }
                    dataItems.release();
                }
            });
        }

        @Override
        public void onConnectionSuspended(int cause) {
            Log.d(TAG, "onConnectionSuspended: " + cause);
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult result) {
            Log.d(TAG, "onConnectionFailed: " + result);
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            Log.d(TAG, "onDataChanged: " + dataEventBuffer);
            for (DataEvent dataEvent : dataEventBuffer) {
                DataItem dataItem = dataEvent.getDataItem();
                if (dataItem.getUri().getPath().equals(FORECAST_PATH)) {
                    DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();
                    mWeatherId = dataMap.getInt(WEATHERID_KEY);
                    mHighTemp = dataMap.getString(HIGH_TEMP_KEY);
                    mLowTemp = dataMap.getString(LOW_TEMP_KEY);
                    updateIcon();
                    invalidate();
                }
            }
        }
    }
}
