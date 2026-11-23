
package com.anant.upsrtcsmartbustracker;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class AdminLoginActivity extends AppCompatActivity {

    private EditText emailInput, passwordInput;
    private Button loginButton;

    private FirebaseAuth firebaseAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_login);

        firebaseAuth = FirebaseAuth.getInstance();

        emailInput = findViewById(R.id.adminEmailInput);
        passwordInput = findViewById(R.id.adminPasswordInput);
        loginButton = findViewById(R.id.adminLoginButton);

        loginButton.setOnClickListener(v -> loginAdmin());
    }

    private void loginAdmin() {

        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this,
                    "Please enter email and password",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        loginButton.setEnabled(false);

        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {

                    if (task.isSuccessful()) {

                        FirebaseUser user = firebaseAuth.getCurrentUser();

                        if (user == null) {
                            loginButton.setEnabled(true);
                            return;
                        }

                        user.getIdToken(true)
                                .addOnSuccessListener(result -> {

                                    Boolean isAdmin =
                                            (Boolean) result.getClaims().get("admin");

                                    if (Boolean.TRUE.equals(isAdmin)) {

                                        Toast.makeText(this,
                                                "Administrator login successful",
                                                Toast.LENGTH_SHORT).show();

                                        Intent intent = new Intent(
                                                AdminLoginActivity.this,
                                                AdminDashboardActivity.class
                                        );

                                        startActivity(intent);
                                        finish();

                                    } else {

                                        firebaseAuth.signOut();

                                        loginButton.setEnabled(true);

                                        Toast.makeText(this,
                                                "Access denied. Administrator account required.",
                                                Toast.LENGTH_LONG).show();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    loginButton.setEnabled(true);

                                    Toast.makeText(this,
                                            "Token verification failed: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show();
                                });

                    } else {

                        loginButton.setEnabled(true);

                        Toast.makeText(this,
                                "Login failed: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
}
