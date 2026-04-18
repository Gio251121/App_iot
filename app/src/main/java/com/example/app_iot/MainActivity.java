package com.example.app_iot;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.example.app_iot.BuildConfig;

// NUOVI IMPORT PER IL JSON
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {


    private MqttManager mqttManager;
    private TextView tvConnStatus;
    private Button btnRiprova;

    // NUOVO: Riferimenti alle TextView dei sensori
    private TextView tvTemperatura;
    private TextView tvUmidita;

    // Definiamo il topic come costante per comodità
    private static final String TOPIC_DATI = "esp32/dati";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("");

        tvConnStatus = findViewById(R.id.tvConnStatus);
        btnRiprova   = findViewById(R.id.btnRiprova);
        Button btnProva = findViewById(R.id.btnProva);

        // NUOVO: Inizializza le TextView
        tvTemperatura = findViewById(R.id.tvTemperatura);
        tvUmidita = findViewById(R.id.tvUmidita);

        btnRiprova.setVisibility(View.GONE);

        mqttManager = new MqttManager();

        // NUOVO: Intercettiamo i messaggi MQTT in arrivo
        mqttManager.setMessageCallback(new MqttManager.MessageCallback() {
            @Override
            public void onMessageArrived(String topic, String message) {
                // Controlliamo che il topic sia quello che ci interessa
                if (topic.equals(TOPIC_DATI)) {
                    try {
                        // Creiamo un oggetto JSON a partire dalla stringa ricevuta
                        JSONObject jsonObject = new JSONObject(message);

                        // Estraiamo la temperatura (se presente nel JSON)
                        if (jsonObject.has("temperatura")) {
                            String temp = jsonObject.getString("temperatura");
                            tvTemperatura.setText(temp + "°C");
                        }

                        // Estraiamo l'umidità (se presente nel JSON)
                        if (jsonObject.has("umidita")) {
                            String umidita = jsonObject.getString("umidita");
                            tvUmidita.setText(umidita + "%");
                        }

                        // In futuro: qui aggiungerai il parse per la pillola
                        // if (jsonObject.has("pillola_presa")) { ... }

                    } catch (JSONException e) {
                        Log.e("MQTT", "Errore nel parsing del JSON: " + message);
                        e.printStackTrace();
                    }
                }
            }
        });

        // Gestione stato connessione
        mqttManager.setConnectionCallback(new MqttManager.ConnectionCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    tvConnStatus.setText("🟢 Dispositivo online");
                    tvConnStatus.setTextColor(0xFF4CAF50);
                    btnRiprova.setVisibility(View.GONE);
                });

                // NUOVO: Appena connessi, ci iscriviamo al topic dell'ESP32
                mqttManager.subscribe(TOPIC_DATI, 1);
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    tvConnStatus.setText("🔴 Connessione fallita");
                    tvConnStatus.setTextColor(0xFFE53935);
                    btnRiprova.setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void onDisconnected() {
                runOnUiThread(() -> {
                    tvConnStatus.setText("⚪ Dispositivo offline");
                    tvConnStatus.setTextColor(0xFFAAAAAA);
                    btnRiprova.setVisibility(View.VISIBLE);
                });
            }
        });

        btnRiprova.setOnClickListener(v -> connetti());

        btnProva.setOnClickListener(v -> {
            mqttManager.disconnect();
            startActivity(new Intent(this, ProvaActivity.class));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mqttManager != null) {
            mqttManager.disconnect(); // Chiude quella vecchia
            connetti();              // Legge i nuovi dati e connette
        }
    }

    private void connetti() {
        tvConnStatus.setText("⏳ Connessione in corso...");
        tvConnStatus.setTextColor(0xFFAAAAAA);
        btnRiprova.setVisibility(View.GONE);

        // 1. Apri le preferenze
        SharedPreferences prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE);

        // 2. Leggi i dati: prende quelli salvati nei settings, oppure usa BuildConfig di default
        String url      = prefs.getString(SettingsActivity.KEY_URL, BuildConfig.BROKER_URL);
        String clientId = prefs.getString(SettingsActivity.KEY_CLIENT_ID, BuildConfig.CLIENT_ID);
        String user     = prefs.getString(SettingsActivity.KEY_USERNAME, BuildConfig.USERNAME);
        String pass     = prefs.getString(SettingsActivity.KEY_PASSWORD, BuildConfig.PASSWORD);

        // 3. Connettiti usando le variabili lette!
        mqttManager.connect(url, clientId, user, pass);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Buona pratica: disconnettere quando l'activity viene distrutta per liberare risorse
        mqttManager.disconnect();
    }
}