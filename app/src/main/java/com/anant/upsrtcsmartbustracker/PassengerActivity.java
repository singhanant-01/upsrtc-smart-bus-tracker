package com.anant.upsrtcsmartbustracker;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.model.RectangularBounds;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class PassengerActivity extends AppCompatActivity {
    private FusedLocationProviderClient fusedLocationClient;
    private double passengerLatitude;
    private double passengerLongitude;
    private long passengerLocationTime;
    private float passengerLocationAccuracy;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private PlacesClient placesClient;
    private AutoCompleteTextView fromInput, toInput;

    // Selected starting location
    private String fromPlaceId;
    private double fromLatitude, fromLongitude;

    // Selected destination
    private String toPlaceId;
    private double toLatitude, toLongitude;
    private AutocompleteSessionToken fromSessionToken;
    private AutocompleteSessionToken toSessionToken;

    private List<AutocompletePrediction> fromPredictions = new ArrayList<>();
    private List<AutocompletePrediction> toPredictions = new ArrayList<>();
    private DatabaseReference activeTripsReference;
    private ValueEventListener activeTripsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!com.google.android.libraries.places.api.Places.isInitialized()) {
            com.google.android.libraries.places.api.Places.initializeWithNewPlacesApiEnabled(
                    getApplicationContext(),
                    BuildConfig.PLACES_API_KEY
            );
        }
        placesClient = Places.createClient(this);

        EdgeToEdge.enable(this);

        setContentView(R.layout.activity_passenger);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Boolean fineLocation = result.get(Manifest.permission.ACCESS_FINE_LOCATION);
                    Boolean coarseLocation = result.get(Manifest.permission.ACCESS_COARSE_LOCATION);

                    if (Boolean.TRUE.equals(fineLocation) || Boolean.TRUE.equals(coarseLocation)) {
                        getPassengerLocation();
                    } else {
                        Toast.makeText(this,
                                "Location permission is required to find nearby buses.",
                                Toast.LENGTH_LONG).show();
                    }
                }
        );
        fromInput = findViewById(R.id.fromInput);
        toInput = findViewById(R.id.toInput);
        setupAutocomplete(fromInput, true);
        setupAutocomplete(toInput, false);
        Button nearbyBusesButton = findViewById(R.id.nearbyBusesButton);

        nearbyBusesButton.setOnClickListener(v -> {
            requestLocationPermission();
        });

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main), (v, insets) -> {

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
    private void requestLocationPermission() {
        boolean fineLocationGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        boolean coarseLocationGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        if (fineLocationGranted || coarseLocationGranted) {
            getPassengerLocation();
        } else {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }
    private void getPassengerLocation() {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED) {

            requestLocationPermission();
            return;
        }

        CancellationTokenSource cancellationTokenSource =
                new CancellationTokenSource();

        fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.getToken()
        ).addOnSuccessListener(this, location -> {

            if (location != null) {

                passengerLocationTime = location.getTime();
                passengerLocationAccuracy = location.getAccuracy();
                long locationAge = System.currentTimeMillis() - passengerLocationTime;

                if (locationAge > 30_000 || locationAge < 0) {
                    Toast.makeText(
                            this,
                            "Location is outdated. Please try again.",
                            Toast.LENGTH_LONG
                    ).show();
                    return;
                }

                if (passengerLocationAccuracy > 100) {
                    Toast.makeText(
                            this,
                            "GPS accuracy is poor: " + passengerLocationAccuracy + " metres. Please try again.",
                            Toast.LENGTH_LONG
                    ).show();
                    return;
                }
                passengerLatitude = location.getLatitude();
                passengerLongitude = location.getLongitude();
                startListeningForActiveTrips();

                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("GPS Location Details")
                        .setMessage(
                                "Latitude: " + passengerLatitude +
                                        "\nLongitude: " + passengerLongitude +
                                        "\nAccuracy: " + passengerLocationAccuracy + " metres" +
                                        "\nLocation time: " + passengerLocationTime
                        )
                        .setPositiveButton("OK", null)
                        .show();

            } else {

                Toast.makeText(
                        this,
                        "Unable to get fresh location. Please enable GPS and try again.",
                        Toast.LENGTH_LONG
                ).show();
            }

        }).addOnFailureListener(this, e -> {

            Toast.makeText(
                    this,
                    "Location error: " + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();

        });
    }
    private void startListeningForActiveTrips() {

        // Remove any previous listener to avoid duplicate subscriptions.
        if (activeTripsReference != null && activeTripsListener != null) {
            activeTripsReference.removeEventListener(activeTripsListener);
        }

        activeTripsReference = FirebaseDatabase.getInstance()
                .getReference("activeTrips");

        activeTripsListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {

                for (DataSnapshot driverSnapshot : snapshot.getChildren()) {

                    for (DataSnapshot busSnapshot : driverSnapshot.getChildren()) {

                        String busNumber = busSnapshot.child("busNumber")
                                .getValue(String.class);

                        Boolean isActive = busSnapshot.child("isActive")
                                .getValue(Boolean.class);

                        String route = busSnapshot.child("route")
                                .getValue(String.class);

                        Object latitudeObject = busSnapshot.child("latitude")
                                .getValue();

                        Object longitudeObject = busSnapshot.child("longitude")
                                .getValue();

                        Object timestampObject = busSnapshot.child("timestamp")
                                .getValue();

                        if (!(latitudeObject instanceof Number)
                                || !(longitudeObject instanceof Number)
                                || !(timestampObject instanceof Number)) {
                            Log.w("BUS_DISCOVERY",
                                    "Skipping incomplete bus record: " + busNumber);
                            continue;
                        }

                        double latitude =
                                ((Number) latitudeObject).doubleValue();

                        double longitude =
                                ((Number) longitudeObject).doubleValue();

                        long timestamp =
                                ((Number) timestampObject).longValue();

                        Log.d("BUS_DISCOVERY",
                                "Bus: " + busNumber
                                        + " | Active: " + isActive
                                        + " | Route: " + route
                                        + " | Latitude: " + latitude
                                        + " | Longitude: " + longitude
                                        + " | Timestamp: " + timestamp);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e("BUS_DISCOVERY",
                        "Firebase read failed: " + error.getMessage());
            }
        };

        activeTripsReference.addValueEventListener(activeTripsListener);
    }
    private void setupAutocomplete(AutoCompleteTextView input, boolean isFrom) {

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                new ArrayList<>()
        );

        input.setAdapter(adapter);
        input.setOnItemClickListener((parent, view, position, id) -> {

            List<AutocompletePrediction> predictions =
                    isFrom ? fromPredictions : toPredictions;

            if (position >= predictions.size()) {
                return;
            }

            AutocompletePrediction prediction = predictions.get(position);
            String placeId = prediction.getPlaceId();

            AutocompleteSessionToken token =
                    isFrom ? fromSessionToken : toSessionToken;

            List<Place.Field> placeFields = new ArrayList<>();
            placeFields.add(Place.Field.ID);
            placeFields.add(Place.Field.DISPLAY_NAME);
            placeFields.add(Place.Field.LOCATION);

            FetchPlaceRequest request =
                    FetchPlaceRequest.builder(placeId, placeFields)
                            .setSessionToken(token)
                            .build();

            placesClient.fetchPlace(request)
                    .addOnSuccessListener(response -> {

                        Place place = response.getPlace();
                        LatLng location = place.getLocation();

                        if (location == null) {
                            Toast.makeText(
                                    this,
                                    "Location coordinates not found",
                                    Toast.LENGTH_SHORT
                            ).show();
                            return;
                        }

                        if (isFrom) {
                            fromPlaceId = place.getId();
                            fromLatitude = location.latitude;
                            fromLongitude = location.longitude;
                            fromSessionToken = null;
                        } else {
                            toPlaceId = place.getId();
                            toLatitude = location.latitude;
                            toLongitude = location.longitude;
                            toSessionToken = null;
                        }

                        Toast.makeText(
                                this,
                                "Location selected successfully",
                                Toast.LENGTH_SHORT
                        ).show();
                    })
                    .addOnFailureListener(exception -> {
                        Toast.makeText(
                                this,
                                "Could not fetch location: " + exception.getMessage(),
                                Toast.LENGTH_SHORT
                        ).show();
                    });
        });

        input.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {

                String query = editable.toString().trim();

                if (query.length() < 3) {
                    adapter.clear();
                    return;
                }

                AutocompleteSessionToken token;

                if (isFrom) {
                    if (fromSessionToken == null) {
                        fromSessionToken = AutocompleteSessionToken.newInstance();
                    }
                    token = fromSessionToken;
                } else {
                    if (toSessionToken == null) {
                        toSessionToken = AutocompleteSessionToken.newInstance();
                    }
                    token = toSessionToken;
                }

                FindAutocompletePredictionsRequest request =
                        FindAutocompletePredictionsRequest.builder()
                                .setQuery(query)
                                .setCountries(Collections.singletonList("IN"))
                                .setRegionCode("IN")
                                .setLocationBias(
                                        RectangularBounds.newInstance(
                                                new LatLng(23.8, 77.0),
                                                new LatLng(30.5, 84.7)
                                        )
                                )
                                .setSessionToken(token)
                                .build();

                placesClient.findAutocompletePredictions(request)
                        .addOnSuccessListener(response -> {

                            // Ignore outdated responses
                            if (!input.getText().toString().trim().equals(query)) {
                                return;
                            }

                            List<AutocompletePrediction> predictions =
                                    response.getAutocompletePredictions();

                            if (isFrom) {
                                fromPredictions.clear();
                                fromPredictions.addAll(predictions);
                            } else {
                                toPredictions.clear();
                                toPredictions.addAll(predictions);
                            }

                            adapter.clear();

                            for (AutocompletePrediction prediction : predictions) {
                                adapter.add(prediction.getFullText(null).toString());
                            }

                            adapter.notifyDataSetChanged();

                            if (!predictions.isEmpty() && input.hasFocus()) {
                                input.showDropDown();
                            }
                        })
                        .addOnFailureListener(exception -> {

                            Toast.makeText(
                                    PassengerActivity.this,
                                    "Places search failed: " + exception.getMessage(),
                                    Toast.LENGTH_SHORT
                            ).show();

                        });
            }
        });
    }
}
