package com.example.app_iot;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    private MqttManager mqttManager;

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

        mqttManager = new MqttManager();

        EditText etUser = findViewById(R.id.etUser);         // Codice Seriale
        EditText etPassword = findViewById(R.id.etPassword); // Password del semaforo
        Button btnAccedi = findViewById(R.id.btnAccedi);

        btnAccedi.setOnClickListener(v -> {
            String codiceSemaforo = etUser.getText().toString().trim();
            String passSemaforo = etPassword.getText().toString().trim();

            if (codiceSemaforo.isEmpty() || passSemaforo.isEmpty()) {
                Toast.makeText(this, "Inserisci Seriale e Password!", Toast.LENGTH_SHORT).show();
                return;
            }

            // 🚨 1. MASTER KEY: Login offline di emergenza
            if (codiceSemaforo.equals("admin") && passSemaforo.equals("1234")) {
                Toast.makeText(LoginActivity.this, "Accesso di emergenza!", Toast.LENGTH_SHORT).show();
                eseguiLoginEffettivo(codiceSemaforo);
                return;
            }

            // 🌐 2. PREPARAZIONE DATI MQTT
            String url      = BuildConfig.BROKER_URL;
            // Usiamo il seriale per identificare il client in modo univoco
            String clientId = BuildConfig.CLIENT_ID + "_" + codiceSemaforo;
            String userMqtt = BuildConfig.USERNAME;
            String passMqtt = BuildConfig.PASSWORD;

            // 🎯 ECCO LA MODIFICA: Il topic di risposta ora è basato sul Seriale!
            String mioTopicRisposta = "esp32/risposta/" + codiceSemaforo;

            // Prepariamo il pacchetto con Seriale, Password e il nuovo Topic
            String messaggioCompleto = "{" +
                    "\"codice_seriale\":\"" + codiceSemaforo + "\"," +
                    "\"password\":\"" + passSemaforo + "\"," +
                    "\"reply_to\":\"" + mioTopicRisposta + "\"" +
                    "}";

            // 3. IMPOSTIAMO IL RICEVITORE
            mqttManager.setMessageCallback((topic_in_arrivo, messaggio_ricevuto) -> {
                if (topic_in_arrivo.equals(mioTopicRisposta)) {
                    try {
                        org.json.JSONObject json = new org.json.JSONObject(messaggio_ricevuto);
                        int stato = json.getInt("stato");

                        if (stato == 1) {
                            String nomeIncrocio = json.optString("nome_incrocio", "Semaforo Sconosciuto");

                            // 💾 Salviamo tutto nel database del telefono
                            SharedPreferences prefs2 = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                            prefs2.edit()
                                    .putBoolean("isLogged", true)
                                    .putString("codice_seriale_salvato", codiceSemaforo) // Salvato per il futuro!
                                    .putString("nome_incrocio_salvato", nomeIncrocio)
                                    .apply();

                            Toast.makeText(LoginActivity.this, "Connesso a: " + nomeIncrocio, Toast.LENGTH_SHORT).show();

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

            // 4. AZIONI DI CONNESSIONE E INVIO
            mqttManager.setConnectionCallback(new MqttManager.ConnectionCallback() {
                @Override
                public void onSuccess() {
                    // Ci iscriviamo al nuovo topic personalizzato
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

    private void eseguiLoginEffettivo(String codice) {
        if (mqttManager != null) mqttManager.disconnect();
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        prefs.edit().putBoolean("isLogged", true).putString("codice_seriale_salvato", codice).apply();
        startActivity(new Intent(LoginActivity.this, MainActivity.class));
        finish();
    }
}