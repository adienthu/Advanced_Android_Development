package com.example.android.wearable;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * A {@link WearableListenerService} listening for weather data.
 */
public class SunshineWatchFaceDataService extends WearableListenerService {
    public SunshineWatchFaceDataService() {
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);
    }
}
