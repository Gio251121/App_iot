package com.example.app_iot;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class SettingsActivity extends AppCompatActivity {

    public static final String PREFS_NAME    = "mqtt_prefs";
    public static final String KEY_URL       = "broker_url";
    public static final String KEY_USERNAME  = "username";
    public static final String KEY_PASSWORD  = "password";
    public static final String KEY_CLIENT_ID = "client_id";

    public static final String DEFAULT_URL       = "ssl://db49354fc78743c7a438062aae747e93.s1.eu.hivemq.cloud:8883";
    public static final String DEFAULT_USERNAME  = "hivemq.webclient.1775809769089";
    public static final String DEFAULT_PASSWORD  = "Qc>;dADFx%:49ap7TU5i";
    public static final String DEFAULT_CLIENT_ID = "android-client-001";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings); // <-- carica il layout

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        EditText etUrl      = findViewById(R.id.etBrokerUrl);
        EditText etUsername = findViewById(R.id.etUsername);
        EditText etPassword = findViewById(R.id.etPassword);
        EditText etClientId = findViewById(R.id.etClientId);
        Button btnSave      = findViewById(R.id.btnSave);

        // Carica i valori salvati
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        etUrl.setText(prefs.getString(KEY_URL, DEFAULT_URL));
        etUsername.setText(prefs.getString(KEY_USERNAME, DEFAULT_USERNAME));
        etPassword.setText(prefs.getString(KEY_PASSWORD, DEFAULT_PASSWORD));
        etClientId.setText(prefs.getString(KEY_CLIENT_ID, DEFAULT_CLIENT_ID));

        btnSave.setOnClickListener(v -> {
            prefs.edit()
                    .putString(KEY_URL,       etUrl.getText().toString())
                    .putString(KEY_USERNAME,  etUsername.getText().toString())
                    .putString(KEY_PASSWORD,  etPassword.getText().toString())
                    .putString(KEY_CLIENT_ID, etClientId.getText().toString())
                    .apply();

            Toast.makeText(this, "Impostazioni salvate", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}