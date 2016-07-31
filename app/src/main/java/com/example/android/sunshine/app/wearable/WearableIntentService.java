package com.example.android.sunshine.app.wearable;

import android.app.IntentService;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;

import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.concurrent.TimeUnit;

public class WearableIntentService extends IntentService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String LOG_TAG = WearableIntentService.class.getSimpleName();

    private static final String FORECAST_PATH = "/forecast";
    private static final String WEATHERID_KEY = "com.example.android.sunshine.app.weatherid";
    private static final String HIGH_TEMP_KEY = "com.example.android.sunshine.app.hightemp";
    private static final String LOW_TEMP_KEY = "com.example.android.sunshine.app.lowtemp";

    private static final String[] FORECAST_COLUMNS = {
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP
    };

    // these indices must match the projection
    private static final int INDEX_WEATHER_ID = 0;
    private static final int INDEX_MAX_TEMP = 1;
    private static final int INDEX_MIN_TEMP = 2;

    private GoogleApiClient mGoogleApiClient;

    public WearableIntentService() {
        super("WearableIntentService");
    }

    public WearableIntentService(String name) {
        super(name);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    @Override
    public void onDestroy() {
        mGoogleApiClient.disconnect();
        super.onDestroy();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(LOG_TAG, "onHandleIntent");

        if (!mGoogleApiClient.isConnected()) {
            mGoogleApiClient.blockingConnect(30, TimeUnit.SECONDS);
            if (!mGoogleApiClient.isConnected())
                return;
        }

        // Get today's data from the ContentProvider
        String location = Utility.getPreferredLocation(this);
        Uri weatherForLocationUri = WeatherContract.WeatherEntry.buildWeatherLocationWithStartDate(
                location, System.currentTimeMillis());
        Cursor data = getContentResolver().query(weatherForLocationUri, FORECAST_COLUMNS, null,
                null, WeatherContract.WeatherEntry.COLUMN_DATE + " ASC");
        if (data == null) {
            return;
        }
        if (!data.moveToFirst()) {
            data.close();
            return;
        }

        // Extract the weather data from the Cursor
        int weatherId = data.getInt(INDEX_WEATHER_ID);
        double maxTemp = data.getDouble(INDEX_MAX_TEMP);
        double minTemp = data.getDouble(INDEX_MIN_TEMP);
        String formattedMaxTemperature = Utility.formatTemperature(this, maxTemp);
        String formattedMinTemperature = Utility.formatTemperature(this, minTemp);
        data.close();

        PutDataMapRequest dataMapRequest = PutDataMapRequest.create(FORECAST_PATH);
        DataMap dataMap = dataMapRequest.getDataMap();
        dataMap.putInt(WEATHERID_KEY, weatherId);
        dataMap.putString(HIGH_TEMP_KEY, formattedMaxTemperature);
        dataMap.putString(LOW_TEMP_KEY, formattedMinTemperature);
//        dataMapRequest.setUrgent();
        PutDataRequest dataRequest = dataMapRequest.asPutDataRequest();
        dataRequest.setUrgent();
        PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi.putDataItem(mGoogleApiClient, dataRequest);
        pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
            @Override
            public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                if (dataItemResult.getStatus().isSuccess()) {
//                    DataMap dataMap1 = DataMapItem.fromDataItem(dataItemResult.getDataItem()).getDataMap();
//                    Log.d(LOG_TAG, "Count set to : " + dataMap1.getInt(COUNT_KEY));
                    Log.d(LOG_TAG, "Data item set");
                }else {
                    Log.d(LOG_TAG, "Data item could not be set - " + dataItemResult.getStatus().getStatusMessage());
                }
            }
        });
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(LOG_TAG, "Connected to Google Play Services");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(LOG_TAG, "Connection to Google Play Services suspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(LOG_TAG, "Connection to Google Play Services failed: " + connectionResult);
    }
}