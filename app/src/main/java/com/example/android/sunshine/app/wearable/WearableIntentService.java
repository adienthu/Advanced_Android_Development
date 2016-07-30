package com.example.android.sunshine.app.wearable;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;

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
    private static final String COUNT_KEY = "com.example.android.sunshine.app.count";

    private GoogleApiClient mGoogleApiClient;
    private long mCount;

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

        mCount = System.currentTimeMillis();
        Log.d(LOG_TAG, "sending count " + mCount);


        PutDataMapRequest dataMapRequest = PutDataMapRequest.create("/count");
        DataMap dataMap = dataMapRequest.getDataMap();
        dataMap.putLong(COUNT_KEY, mCount);
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