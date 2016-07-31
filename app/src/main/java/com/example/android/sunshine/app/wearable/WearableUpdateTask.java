package com.example.android.sunshine.app.wearable;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.util.Log;

import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.concurrent.TimeUnit;

public class WearableUpdateTask extends AsyncTask<Void, Void, Void>{
    private static final String LOG_TAG = WearableUpdateTask.class.getSimpleName();
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

    private Context mContext;

    public WearableUpdateTask(Context context) {
        mContext = context;
    }

    @Override
    protected Void doInBackground(Void... params) {
        GoogleApiClient apiClient = new GoogleApiClient.Builder(mContext)
                .addApi(Wearable.API)
                .build();

        if (!apiClient.isConnected()) {
            apiClient.blockingConnect(30, TimeUnit.SECONDS);
            if (!apiClient.isConnected())
                return null;
        }

        // Get today's data from the ContentProvider
        String location = Utility.getPreferredLocation(mContext);
        Uri weatherForLocationUri = WeatherContract.WeatherEntry.buildWeatherLocationWithStartDate(
                location, System.currentTimeMillis());
        Cursor data = mContext.getContentResolver().query(weatherForLocationUri, FORECAST_COLUMNS, null,
                null, WeatherContract.WeatherEntry.COLUMN_DATE + " ASC");
        if (data == null) {
            return null;
        }
        if (!data.moveToFirst()) {
            data.close();
            return null;
        }

        // Extract the weather data from the Cursor
        int weatherId = data.getInt(INDEX_WEATHER_ID);
        double maxTemp = data.getDouble(INDEX_MAX_TEMP);
        double minTemp = data.getDouble(INDEX_MIN_TEMP);
        String formattedMaxTemperature = Utility.formatTemperature(mContext, maxTemp);
        String formattedMinTemperature = Utility.formatTemperature(mContext, minTemp);
        data.close();

        PutDataMapRequest dataMapRequest = PutDataMapRequest.create(FORECAST_PATH);
        DataMap dataMap = dataMapRequest.getDataMap();
        dataMap.putInt(WEATHERID_KEY, weatherId);
        dataMap.putString(HIGH_TEMP_KEY, formattedMaxTemperature);
        dataMap.putString(LOW_TEMP_KEY, formattedMinTemperature);

        PutDataRequest dataRequest = dataMapRequest.asPutDataRequest();
        dataRequest.setUrgent();
        PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi.putDataItem(apiClient, dataRequest);
        pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
            @Override
            public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                if (dataItemResult.getStatus().isSuccess()) {
                    Log.d(LOG_TAG, "Data item set");
                }else {
                    Log.d(LOG_TAG, "Data item could not be set - " + dataItemResult.getStatus().getStatusMessage());
                }
            }
        });

        apiClient.disconnect();
        return null;
    }
}
