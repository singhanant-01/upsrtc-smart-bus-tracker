package com.anant.upsrtcsmartbustracker;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import java.util.HashMap;
import java.util.Map;

public class AdminDashboardActivity extends AppCompatActivity {

    private EditText driverUidInput;
    private EditText busNumberInput;
    private EditText routeIdInput;
    private EditText servicePatternInput;
    private EditText directionInput;

    private Button assignButton;

    private FirebaseAuth firebaseAuth;
    private DatabaseReference assignmentsReference;

    private static final String DATABASE_URL =
            "https://upsrtc-smart-bus-tracker-default-rtdb.asia-southeast1.firebasedatabase.app/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        applyWindowInsets();

        firebaseAuth = FirebaseAuth.getInstance();

        driverUidInput = findViewById(R.id.driverUidInput);
        busNumberInput = findViewById(R.id.busNumberInput);
        routeIdInput = findViewById(R.id.routeIdInput);
        servicePatternInput = findViewById(R.id.servicePatternInput);
        directionInput = findViewById(R.id.directionInput);

        assignButton = findViewById(R.id.assignBusButton);
        Button logoutButton = findViewById(R.id.adminLogoutButton);

        assignButton.setEnabled(false);

        assignmentsReference = FirebaseDatabase
                .getInstance(DATABASE_URL)
                .getReference("driverAssignments");

        verifyAdministrator();

        assignButton.setOnClickListener(v -> assignBus());

        logoutButton.setOnClickListener(v -> {
            firebaseAuth.signOut();

            Intent intent = new Intent(
                    AdminDashboardActivity.this,
                    AdminLoginActivity.class
            );

            startActivity(intent);
            finish();
        });
    }

    private void applyWindowInsets() {

        View root = findViewById(R.id.adminRootLayout);

        final int initialLeft = root.getPaddingLeft();
        final int initialTop = root.getPaddingTop();
        final int initialRight = root.getPaddingRight();
        final int initialBottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {

            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );

            view.setPadding(
                    initialLeft + insets.left,
                    initialTop + insets.top,
                    initialRight + insets.right,
                    initialBottom + insets.bottom
            );

            return windowInsets;
        });

        ViewCompat.requestApplyInsets(root);
    }

    private void verifyAdministrator() {

        FirebaseUser user = firebaseAuth.getCurrentUser();

        if (user == null) {
            openLogin();
            return;
        }

        user.getIdToken(true)
                .addOnSuccessListener(result -> {

                    Boolean isAdmin =
                            (Boolean) result.getClaims().get("admin");

                    if (Boolean.TRUE.equals(isAdmin)) {

                        assignButton.setEnabled(true);

                    } else {

                        firebaseAuth.signOut();

                        Toast.makeText(
                                this,
                                getString(R.string.admin_access_denied),
                                Toast.LENGTH_LONG
                        ).show();

                        openLogin();
                    }
                })
                .addOnFailureListener(e -> {

                    Toast.makeText(
                            this,
                            getString(R.string.verification_failed, e.getMessage()),
                            Toast.LENGTH_LONG
                    ).show();

                    firebaseAuth.signOut();
                    openLogin();
                });
    }

    private void openLogin() {

        Intent intent = new Intent(
                AdminDashboardActivity.this,
                AdminLoginActivity.class
        );

        startActivity(intent);
        finish();
    }

    private void assignBus() {

        String driverUID =
                driverUidInput.getText().toString().trim();

        String busNumber =
                busNumberInput.getText().toString().trim().toUpperCase();

        String routeId =
                routeIdInput.getText().toString().trim();

        String servicePatternId =
                servicePatternInput.getText().toString().trim();

        String displayedDirection =
                directionInput.getText().toString().trim();

        String direction;

        if (displayedDirection.equals(
                getString(R.string.gorakhpur_to_lucknow))
                || displayedDirection.equals("GORAKHPUR_TO_LUCKNOW")) {

            direction = "GORAKHPUR_TO_LUCKNOW";

        } else {
            Toast.makeText(
                    this,
                    getString(R.string.invalid_direction),
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (driverUID.isEmpty()
                || busNumber.isEmpty()
                || routeId.isEmpty()
                || servicePatternId.isEmpty()
                || direction.isEmpty()) {

            Toast.makeText(
                    this,
                    getString(R.string.fill_all_fields),
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        String safeBusNumber = busNumber
                .replace(".", "_")
                .replace("#", "_")
                .replace("$", "_")
                .replace("[", "_")
                .replace("]", "_")
                .replace("/", "_");

        assignButton.setEnabled(false);

        Map<String, Object> assignment = new HashMap<>();

        assignment.put("busNumber", busNumber);
        assignment.put("driverUID", driverUID);
        assignment.put("routeId", routeId);
        assignment.put("servicePatternId", servicePatternId);
        assignment.put("direction", direction);
        assignment.put("isAssigned", true);
        assignment.put("assignmentStatus", "pending");

        FirebaseUser currentUser = firebaseAuth.getCurrentUser();

        if (currentUser == null) {
            assignButton.setEnabled(true);
            openLogin();
            return;
        }

        assignment.put("assignedBy", currentUser.getUid());
        assignment.put("assignedAt", ServerValue.TIMESTAMP);
        assignment.put("updatedAt", ServerValue.TIMESTAMP);

        assignmentsReference
                .child(driverUID)
                .child(safeBusNumber)
                .setValue(assignment)
                .addOnSuccessListener(unused -> {

                    assignButton.setEnabled(true);

                    Toast.makeText(
                            this,
                            getString(R.string.bus_assigned_successfully),
                            Toast.LENGTH_LONG
                    ).show();

                    busNumberInput.setText("");

                })
                .addOnFailureListener(e -> {

                    assignButton.setEnabled(true);

                    Toast.makeText(
                            this,
                            getString(R.string.assignment_failed, e.getMessage()),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }
}
