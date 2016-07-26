package com.example.android.sunshine.wearable;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * A {@link WearableListenerService} listening for weather data.
 */
public class SunshineWatchFaceDataService extends WearableListenerService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String LOG_TAG = "DataService";
    private static final String COUNT_KEY = "com.example.android.sunshine.app.count";

    public SunshineWatchFaceDataService() {
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        super.onDataChanged(dataEventBuffer);
        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
            Log.d(LOG_TAG, "onDataChanged: " + dataEventBuffer);
        }

        for (DataEvent dataEvent : dataEventBuffer) {
            DataItem dataItem = dataEvent.getDataItem();
            if (dataItem.getUri().getPath().equals("/count")) {
                DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();
                if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
                    Log.d(LOG_TAG, "count: " + dataMap.getInt(COUNT_KEY));
                }
            }
        }
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);

        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
            Log.d(LOG_TAG, "onMessageReceived: " + messageEvent);
        }

        if (!messageEvent.getPath().equals("/count")) {
            return;
        }

        byte[] rawData = messageEvent.getData();
        DataMap dataMap = DataMap.fromByteArray(rawData);
        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
            Log.d(LOG_TAG, "count: " + dataMap.getInt(COUNT_KEY));
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }
}
