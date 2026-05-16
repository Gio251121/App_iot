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
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private MqttManager mqttManager;
    private TextView tvConnStatus;
    private Button btnRiprova;
    private Button btnProva;

    private TextView tvTemperatura;
    private TextView tvUmidita;

    // Nuovi campi per il semaforo
    private TextView tvNomeIncrocio;
    private TextView tvCodiceSeriale;
    private TextView tvStatoSemaforo;

    private String codiceSemaforoSalvato;
    private String topicDati;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("");
        }

        // 1. Collegamento elementi grafici
        tvConnStatus = findViewById(R.id.tvConnStatus);
        btnRiprova = findViewById(R.id.btnRiprova);
        btnProva = findViewById(R.id.btnProva);
        tvTemperatura = findViewById(R.id.tvTemperatura);
        tvUmidita = findViewById(R.id.tvUmidita);

        tvNomeIncrocio = findViewById(R.id.tvNomeIncrocio);
        tvCodiceSeriale = findViewById(R.id.tvCodiceSeriale);
        tvStatoSemaforo = findViewById(R.id.tvStatoSemaforo);

        Button btnSpegniSemaforo = findViewById(R.id.btnSpegniSemaforo);
        Button btnAccendiSemaforo = findViewById(R.id.btnAccendiSemaforo);
        Button btnInattivoSemaforo = findViewById(R.id.btnInattivoSemaforo);

        btnRiprova.setVisibility(View.GONE);

        // 2. Recupero i dati del semaforo dal Login
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        codiceSemaforoSalvato = prefs.getString("codice_seriale_salvato", "Sconosciuto");
        String nomeIncrocioSalvato = prefs.getString("nome_incrocio_salvato", "Incrocio non definito");

        tvCodiceSeriale.setText("Seriale: " + codiceSemaforoSalvato);
        tvNomeIncrocio.setText(nomeIncrocioSalvato);

        // 3. Imposto il topic da cui leggere i dati (es: esp32/dati/ESP32-SEM01)
        topicDati = "esp32/dati/" + codiceSemaforoSalvato;

        // 4. Azioni dei bottoni
        btnAccendiSemaforo.setOnClickListener(v -> inviaComando("acceso"));
        btnSpegniSemaforo.setOnClickListener(v -> inviaComando("spento"));
        btnInattivoSemaforo.setOnClickListener(v -> inviaComando("inattivo"));

        btnProva.setOnClickListener(v -> {
            if (mqttManager != null) mqttManager.disconnect();
            startActivity(new Intent(this, ProvaActivity.class));
        });
        btnRiprova.setOnClickListener(v -> connetti());

        // 5. Inizializzo MQTT
        mqttManager = new MqttManager();

        mqttManager.setMessageCallback((topic, message) -> {
            if (topic.equals(topicDati)) {
                try {
                    JSONObject jsonObject = new JSONObject(message);
                    runOnUiThread(() -> {
                        try {
                            if (jsonObject.has("temperatura")) {
                                tvTemperatura.setText(jsonObject.getString("temperatura") + "°C");
                            }
                            if (jsonObject.has("umidita")) {
                                tvUmidita.setText(jsonObject.getString("umidita") + "%");
                            }
                            // Se il semaforo ci manda il suo stato attuale (es: "acceso", "spento")
                            if (jsonObject.has("stato")) {
                                tvStatoSemaforo.setText(jsonObject.getString("stato").toUpperCase());
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    });
                } catch (JSONException e) {
                    Log.e("MQTT", "Errore JSON: " + message);
                }
            }
        });

        mqttManager.setConnectionCallback(new MqttManager.ConnectionCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    tvConnStatus.setText("🟢 Dispositivo online");
                    tvConnStatus.setTextColor(0xFF4CAF50);
                    btnRiprova.setVisibility(View.GONE);
                });
                // Mi iscrivo ai dati specifici di questo semaforo
                mqttManager.subscribe(topicDati, 1);
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
    }

    private void inviaComando(String payload) {
        // Uso un topic specifico per questo semaforo, così non modifico gli altri incroci!
        String topicComando = "esp/comandi/" + codiceSemaforoSalvato;

        mqttManager.publish(topicComando, payload, 1, new MqttManager.PublishCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(MainActivity.this, "Comando inviato: " + payload.toUpperCase(), Toast.LENGTH_SHORT).show();
                // Aggiorniamo la grafica in attesa che il semaforo confermi
                tvStatoSemaforo.setText(payload.toUpperCase());
            }

            @Override
            public void onFailure(String error) {
                Toast.makeText(MainActivity.this, "Errore di rete: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mqttManager != null) {
            mqttManager.disconnect();
            connetti();
        }
    }

    private void connetti() {
        tvConnStatus.setText("⏳ Connessione in corso...");
        tvConnStatus.setTextColor(0xFFAAAAAA);
        btnRiprova.setVisibility(View.GONE);

        SharedPreferences prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE);
        String url      = prefs.getString(SettingsActivity.KEY_URL, BuildConfig.BROKER_URL);

        // Uso il seriale anche nel Client ID per non sovrappormi
        String clientId = BuildConfig.CLIENT_ID + "_" + codiceSemaforoSalvato + "_" + System.currentTimeMillis();
        String user     = prefs.getString(SettingsActivity.KEY_USERNAME, BuildConfig.USERNAME);
        String pass     = prefs.getString(SettingsActivity.KEY_PASSWORD, BuildConfig.PASSWORD);

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
        if (mqttManager != null) {
            mqttManager.disconnect();
        }
    }
}