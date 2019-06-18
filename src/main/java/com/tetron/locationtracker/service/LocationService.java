package com.tetron.locationtracker.service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.provider.CalendarContract;
import android.text.PrecomputedText;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.text.PrecomputedTextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.util.concurrent.TimeUnit;

public class LocationService  extends Service {
    public static final String TAG = "LocationService";

    private static final float LOCATION_TRACKING_MIN_DISTANCE = 15f;
    private static final float ACCEPTED_ACCURACY = 50f;

    private final IBinder mBinder = new LocalBinder();

    // fused location api
    private FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate");
        if (Build.VERSION.SDK_INT >= 26) {
            String CHANNEL_ID = "my_channel_01";
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Channel human readable title",
                    NotificationManager.IMPORTANCE_DEFAULT);

            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("")
                    .setContentText("").build();

            startForeground(1, notification);
        }


        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        createLocationCallback();
        createLocationRequest();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand");
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return START_NOT_STICKY;
        }

        getLastKnownLocation();
        startPeriodicLocationUpdate();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopPeriodicLocationUpdate();
        Log.i(TAG, "onDestroy - Estou sendo destruido ");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private void getLastKnownLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mFusedLocationClient.getLastLocation()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        Location lastLocation = task.getResult();
                        storeLocation(lastLocation);
                    }
                });
    }

    private void createLocationRequest() {
        mLocationRequest = new LocationRequest()
                .setInterval(1000)//TimeUnit.SECONDS.toMillis(PrecomputedText.Params.getAgentLocationTrackingUpdateInterval()))
                .setFastestInterval(5000)//TimeUnit.SECONDS.toMillis(PrecomputedTextCompat.Params.getAgentLocationTrackingUpdateInterval()))
                //.setExpirationDuration(TimeUnit.SECONDS.toMillis(10))
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private void createLocationCallback() {
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    storeLocation(location);
                }

                Log.i(TAG, "locationResult start");
                for (Location location1 : locationResult.getLocations()) {
                    Log.i(TAG, "locationResult location " + location1.getLatitude() + " " + location1.getLongitude() + " " + location1.getAccuracy() + " " + location1.getProvider());
                }
                Log.i(TAG, "locationResult stop");
            }
        };
    }

    private void startPeriodicLocationUpdate() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Log.i(TAG, "periodic update");
        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
    }

    private void stopPeriodicLocationUpdate() {
        mFusedLocationClient.removeLocationUpdates(mLocationCallback)
                .addOnCompleteListener(task -> Log.i(TAG, "removed location updates!"));
    }

    private void storeLocation(Location location) {
        Log.i(TAG, "periodic location " + location.getLatitude() + " " + location.getLongitude() + " " + location.getAccuracy() + " " + location.getProvider());
        if (location.getAccuracy() <= ACCEPTED_ACCURACY) {
            Log.i(TAG, "storeLocation: " + "saved!");
            /*AgentLocationVo agentLocation = new AgentLocationVo();
            agentLocation.setDate(DateHelper.getDate(location.getTime()));
            agentLocation.setLatitude(location.getLatitude());
            agentLocation.setLongitude(location.getLongitude());
            agentLocation.setAccuracy(location.getAccuracy());
            agentLocation.setSyncState(CalendarContract.SyncState.NOTUPLOADED);
            if (!AgentLocationDao.isAgentLocationExist(agentLocation))
                AgentLocationDao.insert(agentLocation);*/
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.i(TAG, "onTaskRemoved: ");
        stopSelf();
        //JobDispatcher.stopSendLocationJob(getApplicationContext()); // TODO: 04/07/17 not the best place for it
    }

    public static void start(Context context) {
        context.startService(new Intent(context, LocationService.class));
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, LocationService.class));
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        public LocationService getService() {
            // Return this instance of LocationService so clients can call public methods
            return LocationService.this;
        }
    }

    private float getBatteryLevel() {
        Intent batteryStatus = registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int batteryLevel = -1;
        int batteryScale = 1;
        if (batteryStatus != null) {
            batteryLevel = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, batteryLevel);
            batteryScale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, batteryScale);
        }
        return batteryLevel / (float) batteryScale * 100;
    }
}
