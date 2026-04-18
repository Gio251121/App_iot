package com.example.app_iot;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class SettingsActivity extends AppCompatActivity {

    public static final String PREFS_NAME    = "mqtt_prefs";
    public static final String KEY_URL       = "broker_url";
    public static final String KEY_USERNAME  = "username";
    public static final String KEY_PASSWORD  = "password";
    public static final String KEY_CLIENT_ID = "client_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        LinearLayout headerMqtt = findViewById(R.id.headerMqtt);
        LinearLayout contentMqtt = findViewById(R.id.contentMqtt);
        TextView iconMqtt = findViewById(R.id.iconMqtt);

        LinearLayout headerPillole = findViewById(R.id.headerPillole);
        LinearLayout contentPillole = findViewById(R.id.contentPillole);
        TextView iconPillole = findViewById(R.id.iconPillole);

        // Serve per avere l'animazione di scorrimento fluido
        LinearLayout rootView = findViewById(R.id.rootView);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        EditText etUrl      = findViewById(R.id.etBrokerUrl);
        EditText etUsername = findViewById(R.id.etUsername);
        EditText etPassword = findViewById(R.id.etPassword);
        EditText etClientId = findViewById(R.id.etClientId);
        Button btnSave      = findViewById(R.id.btnSave);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Carica i valori salvati.
        // Se non c'è nulla, usa i default presi dal file locale (BuildConfig)
        etUrl.setText(prefs.getString(KEY_URL,       BuildConfig.BROKER_URL));
        etUsername.setText(prefs.getString(KEY_USERNAME,  BuildConfig.USERNAME));
        etPassword.setText(prefs.getString(KEY_PASSWORD,  BuildConfig.PASSWORD));
        etClientId.setText(prefs.getString(KEY_CLIENT_ID, BuildConfig.CLIENT_ID));

        headerMqtt.setOnClickListener(v -> {
            boolean isExpanded = contentMqtt.getVisibility() == View.VISIBLE;

            // Animazione nativa di espansione
            TransitionManager.beginDelayedTransition(rootView, new AutoTransition());

            if (isExpanded) {
                contentMqtt.setVisibility(View.GONE);
                iconMqtt.setText("▼"); // Freccia giù
            } else {
                contentMqtt.setVisibility(View.VISIBLE);
                iconMqtt.setText("▲"); // Freccia su
            }
        });

        // 3. Imposta il click listener per la seconda sezione
        headerPillole.setOnClickListener(v -> {
            boolean isExpanded = contentPillole.getVisibility() == View.VISIBLE;

            TransitionManager.beginDelayedTransition(rootView, new AutoTransition());

            if (isExpanded) {
                contentPillole.setVisibility(View.GONE);
                iconPillole.setText("▼");
            } else {
                contentPillole.setVisibility(View.VISIBLE);
                iconPillole.setText("▲");
            }
        });

        // 1. Trova il bottone
        Button btnLogout = findViewById(R.id.btnLogout);

        // 2. Gestisci il click
        btnLogout.setOnClickListener(v -> {
            // Apriamo le SharedPreferences del login (AppPrefs)
            SharedPreferences prefss = getSharedPreferences("AppPrefs", MODE_PRIVATE);

            // Svuotiamo tutto (isLogged diventa false, loginTime sparisce)
            prefss.edit().clear().apply();

            // Messaggio di conferma
            Toast.makeText(this, "Disconnessione effettuata", Toast.LENGTH_SHORT).show();

            // Torniamo al Login e puliamo lo stack delle attività
            Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);

            // Queste "flags" servono a dire ad Android:
            // "Cancella tutte le pagine aperte e considera il Login come l'unica pagina rimasta"
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            startActivity(intent);
            finish();
        });

        btnSave.setOnClickListener(v -> {
            // Salva i valori modificati dall'utente
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