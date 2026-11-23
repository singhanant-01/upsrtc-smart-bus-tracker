
package com.anant.upsrtcsmartbustracker;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

public class MainActivity extends AppCompatActivity {

    private RadioGroup languageRadioGroup;
    private Button continueButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences preferences =
                getSharedPreferences("AppSettings", MODE_PRIVATE);

        String savedLanguage =
                preferences.getString("selected_language", null);


        // If language is already selected, open HomeActivity.
        if (savedLanguage != null) {
            AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(savedLanguage)
            );

            openHomeActivity();
            return;
        }

        setContentView(R.layout.activity_main);

        languageRadioGroup = findViewById(R.id.languageRadioGroup);
        continueButton = findViewById(R.id.continueButton);

        continueButton.setOnClickListener(v -> {

            int selectedId = languageRadioGroup.getCheckedRadioButtonId();

            String language = (selectedId == R.id.hindiRadio)
                    ? "hi" : "en";

            preferences.edit()
                    .putString("selected_language", language)
                    .apply();

            AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(language)
            );

            openHomeActivity();
        });
    }

    private void openHomeActivity() {
        Intent intent = new Intent(MainActivity.this, HomeActivity.class);
        startActivity(intent);
        finish();
    }
}
