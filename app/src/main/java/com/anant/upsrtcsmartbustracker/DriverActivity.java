package com.anant.upsrtcsmartbustracker;
import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DriverActivity extends AppCompatActivity {

    private static final String TAG = "DriverActivity";
    private static final int LOCATION_PERMISSION_REQUEST = 100;

    private static final String DATABASE_URL =
            "https://upsrtc-smart-bus-tracker-default-rtdb.asia-southeast1.firebasedatabase.app/";

    private FirebaseAuth firebaseAuth;

    private DatabaseReference assignmentsReference;
    private DatabaseReference routesReference;
    private DatabaseReference confirmationsReference;

    private Spinner routeSpinner;
    private Button confirmAssignmentButton;
    private Button startTripButton;
    private TextView assignedRouteText;
    private TextView activeRouteText;
    private TextView activeBusText;

    private final List<DriverAssignment> assignments = new ArrayList<>();

    private DriverAssignment selectedAssignment;

    private boolean isAuthenticated = false;
    private boolean isTripActive = false;
    private boolean isAssignmentConfirmed = false;
    private boolean isLoadingAssignments = false;

    private SharedPreferences preferences;

    private static class DriverAssignment {

        String busNumber;
        String routeId;
        String servicePatternId;
        String direction;
        String assignmentStatus;
        boolean isAssigned;

        DriverAssignment(
                String busNumber,
                String routeId,
                String servicePatternId,
                String direction,
                String assignmentStatus,
                boolean isAssigned
        ) {
            this.busNumber = busNumber;
            this.routeId = routeId;
            this.servicePatternId = servicePatternId;
            this.direction = direction;
            this.assignmentStatus = assignmentStatus;
            this.isAssigned = isAssigned;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_driver);

        preferences = getSharedPreferences("BusTripData", MODE_PRIVATE);

        initializeViews();
        initializeFirebase();
        initializeWindowInsets();

        setupAssignmentSpinner();
        setupButtons();

        restoreActiveTrip();

        authenticateDriver();
    }

    private void initializeViews() {

        routeSpinner = findViewById(R.id.routeSpinner);
        confirmAssignmentButton =
                findViewById(R.id.confirmAssignmentButton);

        startTripButton = findViewById(R.id.startTripButton);

        assignedRouteText = findViewById(R.id.assignedRouteText);
        activeRouteText = findViewById(R.id.activeRouteText);
        activeBusText = findViewById(R.id.activeBusText);

        confirmAssignmentButton.setEnabled(false);
        startTripButton.setEnabled(false);
    }

    private void initializeFirebase() {

        firebaseAuth = FirebaseAuth.getInstance();

        FirebaseDatabase database =
                FirebaseDatabase.getInstance(DATABASE_URL);

        assignmentsReference = database.getReference("driverAssignments");
        routesReference = database.getReference("routes");
        confirmationsReference =
                database.getReference("assignmentConfirmations");
    }

    private void initializeWindowInsets() {

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main),
                (v, insets) -> {

                    Insets systemBars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                    );

                    v.setPadding(
                            systemBars.left,
                            systemBars.top,
                            systemBars.right,
                            systemBars.bottom
                    );

                    return insets;
                }
        );
    }


    private void authenticateDriver() {

        FirebaseUser currentUser = firebaseAuth.getCurrentUser();

        if (currentUser == null || currentUser.isAnonymous()) {

            Log.e(TAG, "No valid authenticated driver session");

            firebaseAuth.signOut();

            Toast.makeText(
                    this,
                    "Please log in using your driver email and password.",
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return;
        }

        Log.d(TAG, "Authenticated driver UID: " + currentUser.getUid());

        onDriverAuthenticated();
    }


    private void onDriverAuthenticated() {

        FirebaseUser user = firebaseAuth.getCurrentUser();

        if (user == null) {
            return;
        }

        isAuthenticated = true;

        Log.d(TAG, "Authenticated UID: " + user.getUid());

        if (isTripActive) {

            startTrackingService();

        } else {

            loadDriverAssignments();
        }
    }

    private void setupAssignmentSpinner() {

        routeSpinner.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {

                    @Override
                    public void onItemSelected(
                            android.widget.AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {

                        if (isLoadingAssignments || isTripActive) {
                            return;
                        }

                        if (position < 0 || position >= assignments.size()) {

                            selectedAssignment = null;
                            isAssignmentConfirmed = false;

                            confirmAssignmentButton.setEnabled(false);
                            startTripButton.setEnabled(false);

                            return;
                        }

                        selectedAssignment = assignments.get(position);

                        loadSelectedAssignmentDetails();
                    }

                    @Override
                    public void onNothingSelected(
                            android.widget.AdapterView<?> parent
                    ) {

                        selectedAssignment = null;
                        isAssignmentConfirmed = false;

                        confirmAssignmentButton.setEnabled(false);
                        startTripButton.setEnabled(false);
                    }
                }
        );
    }

    private void loadDriverAssignments() {

        FirebaseUser user = firebaseAuth.getCurrentUser();

        if (user == null || isTripActive) {
            return;
        }

        String driverUid = user.getUid();

        assignmentsReference.child(driverUid)
                .addListenerForSingleValueEvent(
                        new ValueEventListener() {

                            @Override
                            public void onDataChange(DataSnapshot snapshot) {

                                assignments.clear();

                                for (DataSnapshot busSnapshot :
                                        snapshot.getChildren()) {

                                    String busNumber =
                                            busSnapshot.child("busNumber")
                                                    .getValue(String.class);

                                    String routeId =
                                            busSnapshot.child("routeId")
                                                    .getValue(String.class);

                                    String servicePatternId =
                                            busSnapshot.child("servicePatternId")
                                                    .getValue(String.class);

                                    String direction =
                                            busSnapshot.child("direction")
                                                    .getValue(String.class);

                                    String status =
                                            busSnapshot.child("assignmentStatus")
                                                    .getValue(String.class);

                                    Boolean assigned =
                                            busSnapshot.child("isAssigned")
                                                    .getValue(Boolean.class);

                                    if (busNumber == null) {
                                        continue;
                                    }

                                    if (routeId == null
                                            || servicePatternId == null
                                            || direction == null) {

                                        Log.w(TAG,
                                                "Incomplete assignment: "
                                                        + busNumber);
                                        continue;
                                    }

                                    boolean isAssigned =
                                            assigned != null && assigned;

                                    if (isAssigned) {

                                        assignments.add(
                                                new DriverAssignment(
                                                        busNumber,
                                                        routeId,
                                                        servicePatternId,
                                                        direction,
                                                        status == null ? "" : status,
                                                        true
                                                )
                                        );
                                    }
                                }

                                displayAssignments();
                            }

                            @Override
                            public void onCancelled(DatabaseError error) {

                                Log.e(TAG,
                                        "Could not load assignments",
                                        error.toException());

                                Toast.makeText(
                                        DriverActivity.this,
                                        "Unable to load assigned buses.",
                                        Toast.LENGTH_LONG
                                ).show();
                            }
                        }
                );
    }

    private void displayAssignments() {

        List<String> busNumbers = new ArrayList<>();

        for (DriverAssignment assignment : assignments) {
            busNumbers.add(assignment.busNumber);
        }

        if (busNumbers.isEmpty()) {

            busNumbers.add("No assigned buses");

            routeSpinner.setEnabled(false);

            assignedRouteText.setText(
                    "No bus has been assigned to this driver yet."
            );

            assignedRouteText.setVisibility(View.VISIBLE);

            confirmAssignmentButton.setEnabled(false);
            startTripButton.setEnabled(false);

        } else {

            routeSpinner.setEnabled(true);

            assignedRouteText.setText(
                    "Select your assigned bus to view its route."
            );

            assignedRouteText.setVisibility(View.VISIBLE);
        }

        isLoadingAssignments = true;

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                busNumbers
        );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        routeSpinner.setAdapter(adapter);

        isLoadingAssignments = false;

        if (!assignments.isEmpty()) {

            routeSpinner.setSelection(0);

            selectedAssignment = assignments.get(0);

            loadSelectedAssignmentDetails();
        }
    }

    private void loadSelectedAssignmentDetails() {

        if (selectedAssignment == null || isTripActive) {
            return;
        }

        final DriverAssignment assignment = selectedAssignment;

        isAssignmentConfirmed = false;
        confirmAssignmentButton.setEnabled(false);
        startTripButton.setEnabled(false);

        String routeId = assignment.routeId;
        String patternId = assignment.servicePatternId;

        routesReference.child(routeId)
                .addListenerForSingleValueEvent(
                        new ValueEventListener() {

                            @Override
                            public void onDataChange(DataSnapshot routeSnapshot) {

                                if (selectedAssignment != assignment) {
                                    return;
                                }

                                if (!routeSnapshot.exists()) {

                                    assignedRouteText.setText(
                                            "Route details could not be found."
                                    );

                                    return;
                                }

                                String routeName =
                                        routeSnapshot.child("routeName")
                                                .getValue(String.class);

                                DataSnapshot patternSnapshot =
                                        routeSnapshot.child("servicePatterns")
                                                .child(patternId);

                                String patternName =
                                        patternSnapshot.child("patternName")
                                                .getValue(String.class);

                                String verificationStatus =
                                        patternSnapshot.child("verificationStatus")
                                                .getValue(String.class);

                                if (routeName == null) {
                                    routeName = routeId;
                                }

                                if (patternName == null) {
                                    patternName = patternId;
                                }

                                String details =
                                        "Bus: " + assignment.busNumber
                                                + "\nRoute: " + routeName
                                                + "\nService Pattern: " + patternName
                                                + "\nDirection: " + assignment.direction
                                                + "\nVerification: "
                                                + (verificationStatus == null
                                                ? "Not specified"
                                                : verificationStatus);

                                assignedRouteText.setText(details);
                                assignedRouteText.setVisibility(View.VISIBLE);

                                checkAssignmentConfirmation(assignment);
                            }

                            @Override
                            public void onCancelled(DatabaseError error) {

                                Log.e(TAG,
                                        "Route loading failed",
                                        error.toException());

                                assignedRouteText.setText(
                                        "Unable to load route details."
                                );
                            }
                        }
                );
    }

    private void checkAssignmentConfirmation(
            DriverAssignment assignment
    ) {

        FirebaseUser user = firebaseAuth.getCurrentUser();

        if (user == null) {
            return;
        }

        String driverUid = user.getUid();
        String safeBusNumber = sanitizeBusNumber(assignment.busNumber);

        confirmationsReference
                .child(driverUid)
                .child(safeBusNumber)
                .addListenerForSingleValueEvent(
                        new ValueEventListener() {

                            @Override
                            public void onDataChange(DataSnapshot snapshot) {

                                if (selectedAssignment != assignment
                                        || isTripActive) {
                                    return;
                                }

                                String confirmationStatus =
                                        snapshot.child("assignmentStatus")
                                                .getValue(String.class);

                                isAssignmentConfirmed =
                                        "confirmed".equals(confirmationStatus)
                                                && "pending".equals(
                                                assignment.assignmentStatus);

                                if (isAssignmentConfirmed) {

                                    confirmAssignmentButton.setEnabled(false);
                                    confirmAssignmentButton.setText(
                                            "Assignment Confirmed"
                                    );

                                    startTripButton.setEnabled(true);

                                    Toast.makeText(
                                            DriverActivity.this,
                                            "Assignment already confirmed.",
                                            Toast.LENGTH_SHORT
                                    ).show();

                                } else if ("pending".equals(
                                        assignment.assignmentStatus)) {

                                    confirmAssignmentButton.setText(
                                            "Confirm Assignment"
                                    );

                                    confirmAssignmentButton.setEnabled(true);
                                    startTripButton.setEnabled(false);

                                } else {

                                    confirmAssignmentButton.setEnabled(false);
                                    startTripButton.setEnabled(false);

                                    assignedRouteText.append(
                                            "\nAssignment is not pending."
                                    );
                                }
                            }

                            @Override
                            public void onCancelled(DatabaseError error) {

                                Log.e(TAG,
                                        "Confirmation check failed",
                                        error.toException());
                            }
                        }

                );
    }

    private void confirmSelectedAssignment() {

        if (selectedAssignment == null || !isAuthenticated) {
            return;
        }

        DriverAssignment assignment = selectedAssignment;

        if (!assignment.isAssigned
                || !"pending".equals(assignment.assignmentStatus)) {

            Toast.makeText(
                    this,
                    "This assignment cannot be confirmed.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        FirebaseUser user = firebaseAuth.getCurrentUser();

        if (user == null) {
            return;
        }

        String driverUid = user.getUid();

        String safeBusNumber =
                sanitizeBusNumber(assignment.busNumber);

        Map<String, Object> confirmation = new HashMap<>();

        confirmation.put("assignmentStatus", "confirmed");
        confirmation.put("confirmedAt", ServerValue.TIMESTAMP);

        confirmAssignmentButton.setEnabled(false);

        confirmationsReference
                .child(driverUid)
                .child(safeBusNumber)
                .setValue(confirmation)

                .addOnSuccessListener(unused -> {

                    if (selectedAssignment != assignment) {
                        return;
                    }

                    isAssignmentConfirmed = true;

                    confirmAssignmentButton.setText(
                            "Assignment Confirmed"
                    );

                    confirmAssignmentButton.setEnabled(false);
                    startTripButton.setEnabled(true);

                    Toast.makeText(
                            this,
                            "Assignment confirmed successfully.",
                            Toast.LENGTH_SHORT
                    ).show();
                })

                .addOnFailureListener(error -> {

                    Log.e(TAG,
                            "Assignment confirmation failed",
                            error);

                    confirmAssignmentButton.setEnabled(true);

                    Toast.makeText(
                            this,
                            "Confirmation failed: " + error.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void setupButtons() {

        confirmAssignmentButton.setOnClickListener(
                v -> confirmSelectedAssignment()
        );

        startTripButton.setOnClickListener(v -> {

            if (isTripActive) {
                endTrip();
            } else {
                startTrip();
            }
        });
    }

    private void startTrip() {

        if (!isAuthenticated || selectedAssignment == null) {

            Toast.makeText(
                    this,
                    "No assigned bus selected.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (!isAssignmentConfirmed) {

            Toast.makeText(
                    this,
                    "Please confirm your bus assignment first.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (!hasLocationPermission()) {
            checkLocationPermission();
            return;
        }

        DriverAssignment assignment = selectedAssignment;

        String busNumber = assignment.busNumber;
        String routeName = assignedRouteText.getText().toString();

        preferences.edit()
                .putBoolean("trip_active", true)
                .putString("bus_number", busNumber)
                .putString("selected_route", routeName)
                .putString("route_id", assignment.routeId)
                .putString("service_pattern_id",
                        assignment.servicePatternId)
                .putString("direction", assignment.direction)
                .apply();

        isTripActive = true;

        startTripButton.setText(R.string.end_trip);
        startTripButton.setEnabled(true);

        routeSpinner.setEnabled(false);
        confirmAssignmentButton.setEnabled(false);

        activeRouteText.setText(
                getString(R.string.active_route, routeName)
        );

        activeRouteText.setVisibility(View.VISIBLE);

        activeBusText.setText(
                getString(R.string.active_bus, busNumber)
        );

        activeBusText.setVisibility(View.VISIBLE);

        startTrackingService();

        Toast.makeText(
                this,
                R.string.trip_started,
                Toast.LENGTH_SHORT
        ).show();
    }

    private void startTrackingService() {

        if (!hasLocationPermission()) {
            Log.e(TAG, "Cannot start tracking service: location permission missing");
            return;
        }

        Intent serviceIntent =
                new Intent(this, DriverTrackingService.class);

        serviceIntent.setAction(DriverTrackingService.ACTION_START);

        try {

            ContextCompat.startForegroundService(this, serviceIntent);

            Log.d(TAG, "Driver tracking service start requested");

        } catch (Exception error) {

            Log.e(TAG, "Could not start driver tracking service", error);

            Toast.makeText(
                    this,
                    "Unable to start GPS tracking. Please try again.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void endTrip() {

        Intent serviceIntent =
                new Intent(this, DriverTrackingService.class);

        serviceIntent.setAction(DriverTrackingService.ACTION_STOP);

        try {

            startService(serviceIntent);

            Log.d(TAG, "Driver tracking service stop requested");

        } catch (Exception error) {

            Log.e(TAG, "Could not send stop request to tracking service", error);

            Toast.makeText(
                    this,
                    "Unable to end the trip. Please try again.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        isTripActive = false;

        startTripButton.setText(R.string.start_trip);

        activeRouteText.setVisibility(View.GONE);
        activeBusText.setVisibility(View.GONE);

        routeSpinner.setEnabled(!assignments.isEmpty());

        confirmAssignmentButton.setEnabled(false);
        startTripButton.setEnabled(false);

        if (selectedAssignment != null) {
            loadSelectedAssignmentDetails();
        }

        Toast.makeText(
                this,
                "Trip ending. Updating trip status...",
                Toast.LENGTH_SHORT
        ).show();

        // Do not clear SharedPreferences here.
        // DriverTrackingService still needs the saved bus number
        // to update the correct Firebase trip record.
    }

    private void restoreActiveTrip() {

        boolean tripActive =
                preferences.getBoolean("trip_active", false);

        if (!tripActive) {
            return;
        }

        String savedBusNumber =
                preferences.getString("bus_number", "");

        String savedRoute =
                preferences.getString("selected_route", "");

        if (savedBusNumber.isEmpty()) {
            preferences.edit().clear().apply();
            return;
        }

        isTripActive = true;

        startTripButton.setText(R.string.end_trip);
        startTripButton.setEnabled(true);

        routeSpinner.setEnabled(false);
        confirmAssignmentButton.setEnabled(false);

        activeRouteText.setText(
                getString(R.string.active_route, savedRoute)
        );

        activeRouteText.setVisibility(View.VISIBLE);

        activeBusText.setText(
                getString(R.string.active_bus, savedBusNumber)
        );

        activeBusText.setVisibility(View.VISIBLE);
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

    private void checkLocationPermission() {

        ActivityCompat.requestPermissions(
                this,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                LOCATION_PERMISSION_REQUEST
        );
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == LOCATION_PERMISSION_REQUEST) {

            if (hasLocationPermission()) {

                if (!isTripActive) {
                    startTrip();
                }

            } else {

                Toast.makeText(
                        this,
                        "Location permission is required to start a trip.",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
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

    @Override
    protected void onDestroy() {

        // GPS updates are now owned by DriverTrackingService.
        // Destroying this Activity must not stop the driver's trip.

        super.onDestroy();
    }
}
