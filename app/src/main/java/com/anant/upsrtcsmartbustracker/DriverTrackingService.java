
package com.anant.upsrtcsmartbustracker;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.util.HashMap;
import java.util.Map;

public class DriverTrackingService extends Service {

    private static final String TAG = "DriverTrackingService";

    private static final String DATABASE_URL =
            "https://upsrtc-smart-bus-tracker-default-rtdb.asia-southeast1.firebasedatabase.app/";

    private static final String CHANNEL_ID = "driver_tracking_channel";
    private static final int NOTIFICATION_ID = 101;

    public static final String ACTION_START =
            "com.anant.upsrtcsmartbustracker.START_TRACKING";

    public static final String ACTION_STOP =
            "com.anant.upsrtcsmartbustracker.STOP_TRACKING";

    private FusedLocationProviderClient fusedLocationClient;

    private FirebaseAuth firebaseAuth;
    private DatabaseReference activeTripsReference;

    private SharedPreferences preferences;

    private LocationCallback locationCallback;

    private boolean trackingStarted = false;
    private boolean stopping = false;

    @Override
    public void onCreate() {
        super.onCreate();

        preferences = getSharedPreferences(
                "BusTripData",
                MODE_PRIVATE
        );

        firebaseAuth = FirebaseAuth.getInstance();

        FirebaseDatabase database =
                FirebaseDatabase.getInstance(DATABASE_URL);

        activeTripsReference = database.getReference("activeTrips");

        fusedLocationClient =
                LocationServices.getFusedLocationProviderClient(this);

        createNotificationChannel();

        Log.d(TAG, "Driver tracking service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        String action = intent == null ? null : intent.getAction();

        if (ACTION_STOP.equals(action)) {

            markTripInactiveAndStop();

            return START_NOT_STICKY;
        }

        if (!preferences.getBoolean("trip_active", false)) {

            Log.w(TAG, "No active trip found. Stopping service.");

            stopSelf();

            return START_NOT_STICKY;
        }

        startTracking();

        return START_STICKY;
    }

    private void startTracking() {

        if (trackingStarted) {
            return;
        }

        if (stopping) {
            return;
        }

        String busNumber =
                preferences.getString("bus_number", "");

        if (busNumber.isEmpty()) {

            Log.e(TAG, "Bus number is missing");

            stopSelf();

            return;
        }

        FirebaseUser user = firebaseAuth.getCurrentUser();

        if (user == null) {

            Log.e(TAG, "Firebase user is not authenticated");

            stopSelf();

            return;
        }

        // Check location permission before starting a location
        // foreground service.
        if (!hasLocationPermission()) {

            Log.e(TAG, "Location permission is missing");

            stopSelf();

            return;
        }

        Notification notification = buildNotification(busNumber);

        try {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                );

            } else {

                startForeground(
                        NOTIFICATION_ID,
                        notification
                );
            }

        } catch (SecurityException | IllegalStateException error) {

            Log.e(TAG, "Unable to start foreground service", error);

            stopSelf();

            return;
        }

        LocationRequest locationRequest =
                new LocationRequest.Builder(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        5000
                )
                        .setMinUpdateIntervalMillis(3000)
                        .build();

        locationCallback = new LocationCallback() {

            @Override
            public void onLocationResult(LocationResult result) {

                if (result == null || stopping) {
                    return;
                }

                Location location = result.getLastLocation();

                if (location == null) {
                    return;
                }

                uploadLocation(location);
            }
        };

        try {

            fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
            ).addOnSuccessListener(unused -> {

                trackingStarted = true;

                Log.d(
                        TAG,
                        "GPS tracking started for bus: " + busNumber
                );

            }).addOnFailureListener(error -> {

                Log.e(TAG, "Unable to request location updates", error);

                stopTrackingAndService();
            });

        } catch (SecurityException error) {

            Log.e(TAG, "Location permission error", error);

            stopTrackingAndService();
        }
    }

    private void uploadLocation(Location location) {

        if (stopping) {
            return;
        }

        FirebaseUser user = firebaseAuth.getCurrentUser();

        String busNumber =
                preferences.getString("bus_number", "");

        String selectedRoute =
                preferences.getString("selected_route", "");

        if (user == null || busNumber.isEmpty()) {

            Log.e(TAG, "Cannot upload: missing user or bus number");

            return;
        }

        String safeBusNumber = sanitizeBusNumber(busNumber);

        Map<String, Object> busData = new HashMap<>();

        busData.put("busNumber", busNumber);
        busData.put("route", selectedRoute);

        busData.put("latitude", location.getLatitude());
        busData.put("longitude", location.getLongitude());

        busData.put("timestamp", ServerValue.TIMESTAMP);
        busData.put("isActive", true);

        String uploadPath =
                "activeTrips/" + user.getUid() + "/" + safeBusNumber;

        activeTripsReference
                .child(user.getUid())
                .child(safeBusNumber)
                .updateChildren(busData)

                .addOnSuccessListener(unused ->
                        Log.d(TAG, "Location uploaded: " + uploadPath)
                )

                .addOnFailureListener(error ->
                        Log.e(TAG, "Location upload failed", error)
                );
    }

    private void markTripInactiveAndStop() {

        if (stopping) {
            return;
        }

        stopping = true;

        FirebaseUser user = firebaseAuth.getCurrentUser();

        String busNumber =
                preferences.getString("bus_number", "");

        if (user == null || busNumber.isEmpty()) {

            Log.e(TAG, "Cannot mark trip inactive: missing user or bus number");

            stopTrackingAndService();

            return;
        }

        String safeBusNumber = sanitizeBusNumber(busNumber);

        Map<String, Object> updates = new HashMap<>();

        updates.put("isActive", false);
        updates.put("timestamp", ServerValue.TIMESTAMP);

        activeTripsReference
                .child(user.getUid())
                .child(safeBusNumber)
                .updateChildren(updates)

                .addOnSuccessListener(unused -> {

                    Log.d(
                            TAG,
                            "Trip marked inactive: " + busNumber
                    );

                    // Clear saved trip details only after Firebase
                    // confirms that the trip has ended.
                    preferences.edit().clear().apply();

                    stopTrackingAndService();
                })

                .addOnFailureListener(error -> {

                    Log.e(
                            TAG,
                            "Failed to mark trip inactive",
                            error
                    );

                    // Keep saved trip details if Firebase update fails.
                    stopTrackingAndService();
                });
    }

    private void stopTrackingAndService() {

        if (locationCallback != null) {

            fusedLocationClient.removeLocationUpdates(
                    locationCallback
            );

            locationCallback = null;
        }

        trackingStarted = false;

        stopForeground(true);

        stopSelf();

        Log.d(TAG, "GPS tracking service stopped");
    }

    private boolean hasLocationPermission() {

        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

                || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private String sanitizeBusNumber(String busNumber) {

        return busNumber
                .replace(".", "_")
                .replace("#", "_")
                .replace("$", "_")
                .replace("[", "_")
                .replace("]", "_")
                .replace("/", "_");
    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "Driver GPS Tracking",
                            NotificationManager.IMPORTANCE_LOW
                    );

            channel.setDescription(
                    "Shows when a driver's bus trip is being tracked."
            );

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String busNumber) {

        Intent notificationIntent =
                new Intent(this, DriverActivity.class);

        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        notificationIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE
                );

        return new NotificationCompat.Builder(this, CHANNEL_ID)

                .setContentTitle("Bus trip in progress")

                .setContentText(
                        "GPS tracking is active for bus " + busNumber
                )

                .setSmallIcon(R.mipmap.ic_launcher)

                .setContentIntent(pendingIntent)

                .setOngoing(true)

                .setCategory(NotificationCompat.CATEGORY_SERVICE)

                .setPriority(NotificationCompat.PRIORITY_LOW)

                .build();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {

        Log.d(
                TAG,
                "App task removed; GPS foreground service continues"
        );

        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {

        if (locationCallback != null) {

            fusedLocationClient.removeLocationUpdates(
                    locationCallback
            );

            locationCallback = null;
        }

        trackingStarted = false;

        Log.d(TAG, "Service destroyed");

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {

        return null;
    }
}
