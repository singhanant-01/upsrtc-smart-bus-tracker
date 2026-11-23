
package com.anant.upsrtcsmartbustracker;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class DriverLoginActivity extends AppCompatActivity {
    private static final String DATABASE_URL =
            "https://upsrtc-smart-bus-tracker-default-rtdb.asia-southeast1.firebasedatabase.app/";

    private static final String DRIVER_EMAIL =
            "upsrts284622@gmail.com";

    private EditText emailInput;
    private EditText passwordInput;
    private Button loginButton;
    private CheckBox showPasswordCheckbox;
    private TextView statusText;


    private FirebaseAuth firebaseAuth;
    private DatabaseReference assignmentsReference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_login);

        firebaseAuth = FirebaseAuth.getInstance();

        assignmentsReference = FirebaseDatabase.getInstance(DATABASE_URL)
                .getReference("driverAssignments");

        emailInput = findViewById(R.id.driverEmailInput);
        passwordInput = findViewById(R.id.driverPasswordInput);
        loginButton = findViewById(R.id.driverLoginButton);
        showPasswordCheckbox = findViewById(R.id.showPasswordCheckbox);
        statusText = findViewById(R.id.driverLoginStatus);


        emailInput.setText(DRIVER_EMAIL);

        // Login button
        loginButton.setOnClickListener(v -> loginDriver());




        // Show or hide password
        showPasswordCheckbox.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {

                    int cursorPosition =
                            passwordInput.getSelectionStart();

                    if (isChecked) {
                        passwordInput.setTransformationMethod(
                                HideReturnsTransformationMethod.getInstance()
                        );
                    } else {
                        passwordInput.setTransformationMethod(
                                PasswordTransformationMethod.getInstance()
                        );
                    }

                    passwordInput.setSelection(
                            Math.max(0, Math.min(
                                    cursorPosition,
                                    passwordInput.getText().length()
                            ))
                    );
                }
        );

        // No automatic Firebase checking when the screen opens.
        statusText.setText("");
    }



    private void loginDriver() {

        String email = emailInput.getText().toString().trim();

        // Password is deliberately not trimmed.
        String password = passwordInput.getText().toString();

        if (email.isEmpty() || password.isEmpty()) {
            showError("Please enter your email and password.");
            return;
        }

        setLoading(true);
        statusText.setTextColor(0xFF52657A);
        statusText.setText("Signing in...");

        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {

                    if (task.isSuccessful()) {

                        FirebaseUser user =
                                firebaseAuth.getCurrentUser();

                        if (user == null || user.isAnonymous()) {

                            firebaseAuth.signOut();
                            setLoading(false);

                            showError(
                                    "Unable to verify your account."
                            );
                            return;
                        }

                        verifyDriverAssignment(user);

                    } else {

                        setLoading(false);

                        String message =
                                task.getException() != null
                                        ? task.getException().getMessage()
                                        : "Please check your login details.";

                        showError("Login failed: " + message);
                    }
                });
    }

    private void verifyDriverAssignment(FirebaseUser user) {

        assignmentsReference.child(user.getUid())
                .addListenerForSingleValueEvent(
                        new ValueEventListener() {

                            @Override
                            public void onDataChange(DataSnapshot snapshot) {

                                boolean hasValidAssignment = false;

                                for (DataSnapshot assignment :
                                        snapshot.getChildren()) {

                                    Boolean isAssigned = assignment
                                            .child("isAssigned")
                                            .getValue(Boolean.class);

                                    if (Boolean.TRUE.equals(isAssigned)) {
                                        hasValidAssignment = true;
                                        break;
                                    }
                                }

                                if (hasValidAssignment) {

                                    openDriverDashboard();

                                } else {

                                    firebaseAuth.signOut();
                                    setLoading(false);

                                    showError(
                                            "No active bus assignment found. "
                                                    + "Please contact the administrator."
                                    );
                                }
                            }

                            @Override
                            public void onCancelled(DatabaseError error) {

                                firebaseAuth.signOut();
                                setLoading(false);

                                showError(
                                        "Assignment verification failed: "
                                                + error.getMessage()
                                );
                            }
                        });
    }

    private void openDriverDashboard() {

        Toast.makeText(
                this,
                "Driver login successful",
                Toast.LENGTH_SHORT
        ).show();

        Intent intent = new Intent(
                DriverLoginActivity.this,
                DriverActivity.class
        );

        startActivity(intent);
        finish();
    }

    private void showError(String message) {
        statusText.setTextColor(0xFFB3261E);
        statusText.setText(message);
    }

    private void setLoading(boolean loading) {

        loginButton.setEnabled(!loading);
        loginButton.setAlpha(loading ? 0.7f : 1.0f);

        // Keep the button label unchanged.
        loginButton.setText("Login");
    }
}
