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
    public static final String KEY_VERSION   = "version";
    public static final int    PREFS_VERSION = 1; // aumenta quando cambi le credenziali nel codice

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        EditText etUrl      = findViewById(R.id.etBrokerUrl);
        EditText etUsername = findViewById(R.id.etUsername);
        EditText etPassword = findViewById(R.id.etPassword);
        EditText etClientId = findViewById(R.id.etClientId);
        Button btnSave      = findViewById(R.id.btnSave);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Se la versione è cambiata, resetta con i nuovi default
        int savedVersion = prefs.getInt(KEY_VERSION, 0);
        if (savedVersion < PREFS_VERSION) {
            prefs.edit()
                    .putString(KEY_URL,       ProvaActivity.BROKER_URL)
                    .putString(KEY_USERNAME,  ProvaActivity.USERNAME)
                    .putString(KEY_PASSWORD,  ProvaActivity.PASSWORD)
                    .putString(KEY_CLIENT_ID, ProvaActivity.CLIENT_ID)
                    .putInt(KEY_VERSION,      PREFS_VERSION)
                    .apply();
        }

        // Carica i valori salvati
        etUrl.setText(prefs.getString(KEY_URL,       ProvaActivity.BROKER_URL));
        etUsername.setText(prefs.getString(KEY_USERNAME,  ProvaActivity.USERNAME));
        etPassword.setText(prefs.getString(KEY_PASSWORD,  ProvaActivity.PASSWORD));
        etClientId.setText(prefs.getString(KEY_CLIENT_ID, ProvaActivity.CLIENT_ID));

        btnSave.setOnClickListener(v -> {
            prefs.edit()
                    .putString(KEY_URL,       etUrl.getText().toString())
                    .putString(KEY_USERNAME,  etUsername.getText().toString())
                    .putString(KEY_PASSWORD,  etPassword.getText().toString())
                    .putString(KEY_CLIENT_ID, etClientId.getText().toString())
                    .putInt(KEY_VERSION,      PREFS_VERSION)
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