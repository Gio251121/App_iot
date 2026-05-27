package com.example.app_iot;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    private MqttManager mqttManager;
    private EditText etUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean isLogged = prefs.getBoolean("isLogged", false);



        if (isLogged) {
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_login);

        Button btnTrovaSemafori = findViewById(R.id.btnTrovaSemafori);
        btnTrovaSemafori.setOnClickListener(v -> {
            if (mqttManager != null) mqttManager.disconnect();
            startActivity(new Intent(LoginActivity.this, MappaActivity.class));
        });

        mqttManager = new MqttManager();

        RadioGroup rgModalita = findViewById(R.id.rgModalita);
        LinearLayout layoutPassword = findViewById(R.id.layoutPassword);
         etUser = findViewById(R.id.etUser);
        EditText etPassword = findViewById(R.id.etPassword);
        Button btnAccedi = findViewById(R.id.btnAccedi);

        gestisciSerialeRicevuto(getIntent());



// Ascoltatore per mostrare o nascondere il campo password in base al ruolo selezionato
        rgModalita.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbUtente) {
                layoutPassword.setVisibility(android.view.View.GONE);
                etPassword.setText(""); // Svuota il campo se si torna a utente
            } else {
                layoutPassword.setVisibility(android.view.View.VISIBLE);
            }
        });

        btnAccedi.setOnClickListener(v -> {
                    String codiceSemaforo = etUser.getText().toString().trim();
                    // Se l'utente è in modalità base, invia una stringa vuota come password
                    String passSemaforo = (rgModalita.getCheckedRadioButtonId() == R.id.rbUtente) ? "" : etPassword.getText().toString().trim();

                    if (codiceSemaforo.isEmpty()) {
                        Toast.makeText(this, "Inserisci il Codice Seriale!", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (rgModalita.getCheckedRadioButtonId() == R.id.rbManutentore && passSemaforo.isEmpty()) {
                        Toast.makeText(this, "Inserisci la password di manutenzione!", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Login offline di emergenza
                    if (codiceSemaforo.equals("admin") && passSemaforo.equals("1234")) {
                        Toast.makeText(LoginActivity.this, "Accesso di emergenza!", Toast.LENGTH_SHORT).show();
                        eseguiLoginEffettivo(codiceSemaforo, "admin");
                        return;
                    }

            String url = BuildConfig.BROKER_URL;
            // Generazione ClientID e Topic univoci per evitare collisioni MQTT
            String clientId = BuildConfig.CLIENT_ID + "_" + Math.random();
            String userMqtt = BuildConfig.USERNAME;
            String passMqtt = BuildConfig.PASSWORD;

            String mioTopicRisposta = "esp32/risposta/" + Math.random();

            String messaggioCompleto = "{" +
                    "\"codice_seriale\":\"" + codiceSemaforo + "\"," +
                    "\"password\":\"" + passSemaforo + "\"," +
                    "\"reply_to\":\"" + mioTopicRisposta + "\"" +
                    "}";

            mqttManager.setMessageCallback((topic_in_arrivo, messaggio_ricevuto) -> {
                if (topic_in_arrivo.equals(mioTopicRisposta)) {
                    try {
                        org.json.JSONObject json = new org.json.JSONObject(messaggio_ricevuto);
                        int stato = json.getInt("stato");

                        if (stato == 1) {
                            String nomeIncrocio = json.optString("nome_incrocio", "Semaforo Sconosciuto");
                            String ruolo = json.optString("ruolo", "utente");

                            // Estrazione dei dati geografici dal JSON di risposta
                            double lat = json.optDouble("latitudine", 0.0);
                            double lon = json.optDouble("longitudine", 0.0);

                            // Scrittura dei parametri all'interno del file delle preferenze
                            SharedPreferences prefs2 = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                            prefs2.edit()
                                    .putBoolean("isLogged", true)
                                    .putString("codice_seriale_salvato", codiceSemaforo)
                                    .putString("nome_incrocio_salvato", nomeIncrocio)
                                    .putString("ruolo_utente", ruolo)
                                    .putFloat("lat_semaforo", (float) lat) // Memorizzazione latitudine
                                    .putFloat("lon_semaforo", (float) lon) // Memorizzazione longitudine
                                    .apply();

                            Toast.makeText(LoginActivity.this, "Connesso come: " + ruolo, Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                            finish();
                        } else {
                            Toast.makeText(LoginActivity.this, "Seriale o Password errati!", Toast.LENGTH_LONG).show();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(LoginActivity.this, "Errore di comunicazione col server", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            mqttManager.setConnectionCallback(new MqttManager.ConnectionCallback() {
                @Override
                public void onSuccess() {
                    // Sottoscrizione al topic di risposta univoco prima di pubblicare
                    mqttManager.subscribe(mioTopicRisposta, 1);

                    mqttManager.publish("esp32/login_request", messaggioCompleto, 1, new MqttManager.PublishCallback() {
                        @Override
                        public void onSuccess() {
                            System.out.println("✅ Invio credenziali in corso sul topic di ascolto: " + mioTopicRisposta);
                        }
                        @Override
                        public void onFailure(String error) {
                            System.out.println("❌ Errore di invio: " + error);
                        }
                    });
                }
                @Override
                public void onFailure(String error) {
                    Toast.makeText(LoginActivity.this, "Errore di connessione al broker", Toast.LENGTH_SHORT).show();
                }
                @Override
                public void onDisconnected() {}
            });

            Toast.makeText(LoginActivity.this, "Verifica in corso...", Toast.LENGTH_SHORT).show();
            mqttManager.connect(url, clientId, userMqtt, passMqtt);
        });
    }

    // Metodo aggiornato per includere il ruolo nel login di emergenza
    private void eseguiLoginEffettivo(String codice, String ruolo) {
        if (mqttManager != null) mqttManager.disconnect();
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        prefs.edit()
                .putBoolean("isLogged", true)
                .putString("codice_seriale_salvato", codice)
                .putString("ruolo_utente", ruolo)
                .apply();
        startActivity(new Intent(LoginActivity.this, MainActivity.class));
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Aggiorna l'intent originario archiviato nell'activity con quello corrente
        setIntent(intent);
        // Analizza i dati del nuovo intent per popolare la UI
        gestisciSerialeRicevuto(intent);
    }

    /**
     * Sottoprogramma centralizzato per l'estrazione del codice seriale e l'aggiornamento grafico.
     */
    private void gestisciSerialeRicevuto(Intent intent) {
        if (intent != null) {
            String serialeRicevuto = intent.getStringExtra("seriale_cliccato");
            if (serialeRicevuto != null && !serialeRicevuto.isEmpty() && etUser != null) {
                // Assegnazione del testo recuperato dall'extra
                etUser.setText(serialeRicevuto);
                // Allineamento dell'indice del cursore alla fine della stringa
                etUser.setSelection(serialeRicevuto.length());

                Toast.makeText(this, "Seriale caricato dalla mappa", Toast.LENGTH_SHORT).show();
            }
        }
    }
}