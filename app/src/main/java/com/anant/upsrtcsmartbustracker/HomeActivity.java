
package com.anant.upsrtcsmartbustracker;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);

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

        Button passengerButton = findViewById(R.id.passengerButton);
        Button driverButton = findViewById(R.id.driverButton);
        Button adminButton = findViewById(R.id.adminButton);

        passengerButton.setOnClickListener(v -> {
            Intent intent = new Intent(
                    HomeActivity.this,
                    PassengerActivity.class
            );
            startActivity(intent);
        });

        driverButton.setOnClickListener(v -> {
            startActivity(new Intent(HomeActivity.this, DriverLoginActivity.class));
        });

        adminButton.setOnClickListener(v -> {
            Intent intent = new Intent(
                    HomeActivity.this,
                    AdminLoginActivity.class
            );
            startActivity(intent);
        });
    }
}
