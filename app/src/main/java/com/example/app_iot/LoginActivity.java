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
            // Se ha già fatto l'accesso in passato, salta direttamente alla Main!
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish(); // Chiude la pagina di Login
            return;   // Ferma la lettura del resto di questo file
        }
        setContentView(R.layout.activity_login);

        mqttManager = new MqttManager();

        EditText etUser = findViewById(R.id.etUser);
        EditText etPassword = findViewById(R.id.etPassword);
        Button btnAccedi = findViewById(R.id.btnAccedi);

        btnAccedi.setOnClickListener(v -> {
            String user = etUser.getText().toString();
            String pass = etPassword.getText().toString();

            // 🚨 1. MASTER KEY: Login offline di emergenza
            if (user.equals("admin") && pass.equals("1234")) {
                Toast.makeText(LoginActivity.this, "Accesso di emergenza (Admin)!", Toast.LENGTH_SHORT).show();
                eseguiLoginEffettivo();
                return; // Ferma tutto e non usa MQTT
            }

            // 🌐 2. PREPARAZIONE DATI MQTT
            String url      = BuildConfig.BROKER_URL;
            String clientId = BuildConfig.CLIENT_ID;
            String user2    = BuildConfig.USERNAME;
            String pass2    = BuildConfig.PASSWORD;

            String clientId2 = clientId + "_" + System.currentTimeMillis();
            String mioTopicRisposta = "esp32/risposta/" + clientId2;

            String messaggioCompleto = "{" +
                    "\"username\":\"" + user + "\"," +
                    "\"password\":\"" + pass + "\"," +
                    "\"reply_to\":\"" + mioTopicRisposta + "\"" +
                    "}";

            // 3. IMPOSTIAMO IL RICEVITORE: Cosa fare quando Laravel ci risponde
            // Usiamo il tuo nuovo metodo setMessageCallback
            mqttManager.setMessageCallback((topic_in_arrivo, messaggio_ricevuto) -> {
                if (topic_in_arrivo.equals(mioTopicRisposta)) {
                    try {
                        org.json.JSONObject json = new org.json.JSONObject(messaggio_ricevuto);
                        int stato = json.getInt("stato");

                        if (stato == 1) {
                            // LOGIN CORRETTO!
                            String jsonPrescrizioni = json.getJSONObject("p").toString();

                            // 2. Salviamo il JSON nelle SharedPreferences insieme al login
                            SharedPreferences prefs2 = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                            prefs2.edit()
                                    .putBoolean("isLogged", true)
                                    .putLong("loginTime", System.currentTimeMillis())
                                    .putString("prescrizioni_salvate", jsonPrescrizioni) // <--- ECCOLO!
                                    .apply();

                            Toast.makeText(LoginActivity.this, "Accesso eseguito!", Toast.LENGTH_SHORT).show();

                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                            finish();
                        } else {
                            // LOGIN FALLITO
                            Toast.makeText(LoginActivity.this, "Utente o password errati!", Toast.LENGTH_LONG).show();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(LoginActivity.this, "Errore nella lettura dei dati dal server", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            // 4. IMPOSTIAMO LE AZIONI DI CONNESSIONE (Sostituisce il vecchio timer di 1.5 secondi)
            mqttManager.setConnectionCallback(new MqttManager.ConnectionCallback() {
                @Override
                public void onSuccess() {
                    // Appena la connessione ha successo, ci iscriviamo e pubblichiamo!
                    mqttManager.subscribe(mioTopicRisposta, 1);

                    mqttManager.publish("esp32/login_request", messaggioCompleto, 1, new MqttManager.PublishCallback() {
                        @Override
                        public void onSuccess() {
                            System.out.println("✅ Richiesta inviata. In attesa di risposta su: " + mioTopicRisposta);
                        }

                        @Override
                        public void onFailure(String error) { // Modificato in String come vuole il tuo MqttManager
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

            // 5. AVVIA LA CONNESSIONE (Questo fa partire tutto il meccanismo)
            Toast.makeText(LoginActivity.this, "Verifica credenziali in corso...", Toast.LENGTH_SHORT).show();
            mqttManager.connect(url, clientId2, user2, pass2);
        });
    }

    // Un piccolo "metodo aiutante" per non ripetere il codice di salvataggio due volte
    private void eseguiLoginEffettivo() {
        if (mqttManager != null) {
            mqttManager.disconnect();
        }

        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        prefs.edit()
                .putBoolean("isLogged", true)
                .putLong("loginTime", System.currentTimeMillis())
                .apply();

        startActivity(new Intent(LoginActivity.this, MainActivity.class));
        finish();
    }
}