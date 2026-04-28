package com.example.app_iot;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private MqttManager mqttManager;
    private TextView tvConnStatus;
    private Button btnRiprova;
    private Button btnProva;

    private TextView tvTemperatura;
    private TextView tvUmidita;

    private TextView tvProssimaDose;
    private TextView tvProssimaPillolaNome;
    private TextView tvProssimaDoseLabel;
    private TextView tvUltimaDose;
    private Button btnConfermaDose;

    private static final String TOPIC_DATI = "esp32/dati";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("");
        }

        tvConnStatus = findViewById(R.id.tvConnStatus);
        btnRiprova = findViewById(R.id.btnRiprova);
        btnProva = findViewById(R.id.btnProva);
        tvTemperatura = findViewById(R.id.tvTemperatura);
        tvUmidita = findViewById(R.id.tvUmidita);

        tvProssimaDose = findViewById(R.id.tvProssimaDose);
        tvProssimaPillolaNome = findViewById(R.id.tvProssimaPillolaNome);
        tvProssimaDoseLabel = findViewById(R.id.tvProssimaDoseLabel);
        tvUltimaDose = findViewById(R.id.tvUltimaDose);
        btnConfermaDose = findViewById(R.id.btnConfermaDose);

        btnRiprova.setVisibility(View.GONE);
        btnConfermaDose.setVisibility(View.GONE);

        calcolaEmostraDosi();

        mqttManager = new MqttManager();

        mqttManager.setMessageCallback((topic, message) -> {
            if (topic.equals(TOPIC_DATI)) {
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
            if (mqttManager != null) mqttManager.disconnect();
            startActivity(new Intent(this, ProvaActivity.class));
        });
    }

    private void calcolaEmostraDosi() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String jsonString = prefs.getString("prescrizioni_salvate", "{}");
        String ultimaConfermata = prefs.getString("ultima_confermata", "");

        try {
            JSONObject prescrizioniSettimana = new JSONObject(jsonString);
            Calendar cal = Calendar.getInstance();

            int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
            int oggi = (dayOfWeek == Calendar.SUNDAY) ? 7 : dayOfWeek - 1;

            int hAdesso = cal.get(Calendar.HOUR_OF_DAY);
            int mAdesso = cal.get(Calendar.MINUTE);
            String oraAttuale = String.format(Locale.getDefault(), "%02d:%02d", hAdesso, mAdesso);

            String ultimaOra = null;

            String prossimaOra = null;
            String prossimaPillolaNome = "";
            int prossimaPillolaQt = 0;
            String etichettaGiorno = "Oggi";
            int giorniAllaProssima = 0; // Serve per calcolare quando far suonare la notifica

            // 1. CERCHIAMO L'ULTIMA DOSE (Solo oggi)
            if (prescrizioniSettimana.has(String.valueOf(oggi))) {
                JSONObject pilloleOggi = prescrizioniSettimana.getJSONObject(String.valueOf(oggi));
                List<String> orari = sortKeys(pilloleOggi);
                for (String ora : orari) {
                    if (ora.compareTo(oraAttuale) <= 0) ultimaOra = ora;
                }
            }

            // 2. CERCHIAMO LA PROSSIMA DOSE
            boolean trovata = false;
            for (int i = 0; i < 7; i++) {
                int giornoDaControllare = (oggi + i - 1) % 7 + 1;
                String giornoStr = String.valueOf(giornoDaControllare);

                if (prescrizioniSettimana.has(giornoStr)) {
                    JSONObject pilloleGiorno = prescrizioniSettimana.getJSONObject(giornoStr);
                    List<String> orari = sortKeys(pilloleGiorno);

                    for (String ora : orari) {
                        if (i == 0) {
                            if (ora.compareTo(oraAttuale) > 0) {
                                prossimaOra = ora;
                                prossimaPillolaNome = pilloleGiorno.getJSONObject(ora).getString("m");
                                prossimaPillolaQt = pilloleGiorno.getJSONObject(ora).getInt("q");
                                giorniAllaProssima = i;
                                trovata = true;
                                break;
                            }
                        } else {
                            prossimaOra = ora;
                            prossimaPillolaNome = pilloleGiorno.getJSONObject(ora).getString("m");
                            prossimaPillolaQt = pilloleGiorno.getJSONObject(ora).getInt("q");
                            etichettaGiorno = getNomeGiorno(giornoDaControllare);
                            giorniAllaProssima = i;
                            trovata = true;
                            break;
                        }
                    }
                }
                if (trovata) break;
            }

            // 3. AGGIORNAMENTO GRAFICA E NOTIFICHE
            if (ultimaOra != null && !ultimaOra.equals(ultimaConfermata)) {
                JSONObject d = prescrizioniSettimana.getJSONObject(String.valueOf(oggi)).getJSONObject(ultimaOra);
                tvProssimaDose.setText(ultimaOra);
                tvProssimaPillolaNome.setText(d.getString("m") + " (" + d.getInt("q") + " pz)");

                tvProssimaDoseLabel.setText("È l'ora di prenderla!");
                tvProssimaDoseLabel.setTextColor(0xFFFF3333);
                btnConfermaDose.setVisibility(View.VISIBLE);

                final String oraInSospeso = ultimaOra;
                btnConfermaDose.setOnClickListener(v -> {
                    prefs.edit().putString("ultima_confermata", oraInSospeso).apply();
                    btnConfermaDose.setVisibility(View.GONE);
                    tvProssimaDoseLabel.setTextColor(0xFF888888);
                    calcolaEmostraDosi();
                });
            } else {
                btnConfermaDose.setVisibility(View.GONE);
                if (prossimaOra != null) {
                    tvProssimaDose.setText(prossimaOra);
                    tvProssimaPillolaNome.setText(prossimaPillolaNome + " (" + prossimaPillolaQt + " pz)");
                    tvProssimaDoseLabel.setText(etichettaGiorno);

                    // --- IMPOSTA LA NOTIFICA PER LA PROSSIMA DOSE ---
                    impostaNotificaAndroid(giorniAllaProssima, prossimaOra, prossimaPillolaNome);

                } else {
                    tvProssimaDose.setText("--:--");
                    tvProssimaPillolaNome.setText("Nessuna terapia programmata");
                    tvProssimaDoseLabel.setText("");
                }
            }

            if (ultimaOra != null) {
                JSONObject d = prescrizioniSettimana.getJSONObject(String.valueOf(oggi)).getJSONObject(ultimaOra);
                tvUltimaDose.setText(d.getString("m") + " alle " + ultimaOra);
            } else {
                tvUltimaDose.setText("— nessuna");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =========================================================
    // FUNZIONE DI NOTIFICA
    // =========================================================
    private void impostaNotificaAndroid(int giorniAggiuntivi, String ora, String nomePillola) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        // CONTROLLO SICUREZZA
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.e("Allarme", "Permesso per allarmi esatti non concesso.");
                return; // Evita il crash
            }
        }

        Intent intent = new Intent(this, AlarmReceiver.class);
        intent.putExtra("nome_pillola", nomePillola);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String[] parti = ora.split(":");
        Calendar calAllarme = Calendar.getInstance();
        calAllarme.add(Calendar.DAY_OF_YEAR, giorniAggiuntivi); // Aggiunge i giorni di distanza (0 = oggi)
        calAllarme.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parti[0]));
        calAllarme.set(Calendar.MINUTE, Integer.parseInt(parti[1]));
        calAllarme.set(Calendar.SECOND, 0);

        if (calAllarme.getTimeInMillis() <= System.currentTimeMillis()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calAllarme.getTimeInMillis(), pendingIntent);
        }
    }

    private String getNomeGiorno(int g) {
        String[] giorni = {"", "Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato", "Domenica"};
        Calendar cal = Calendar.getInstance();
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int oggi = (dayOfWeek == Calendar.SUNDAY) ? 7 : dayOfWeek - 1;

        if (g == oggi + 1 || (oggi == 7 && g == 1)) return "Domani";
        return giorni[g];
    }

    private List<String> sortKeys(JSONObject obj) {
        List<String> keys = new ArrayList<>();
        Iterator<String> it = obj.keys();
        while(it.hasNext()) keys.add(it.next());
        Collections.sort(keys);
        return keys;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mqttManager != null) {
            mqttManager.disconnect();
            connetti();
        }
        calcolaEmostraDosi();
    }

    private void connetti() {
        tvConnStatus.setText("⏳ Connessione in corso...");
        tvConnStatus.setTextColor(0xFFAAAAAA);
        btnRiprova.setVisibility(View.GONE);

        SharedPreferences prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE);
        String url      = prefs.getString(SettingsActivity.KEY_URL, BuildConfig.BROKER_URL);
        String clientId = prefs.getString(SettingsActivity.KEY_CLIENT_ID, BuildConfig.CLIENT_ID);
        String user     = prefs.getString(SettingsActivity.KEY_USERNAME, BuildConfig.USERNAME);
        String pass     = prefs.getString(SettingsActivity.KEY_PASSWORD, BuildConfig.PASSWORD);

        String clientId2 = clientId + "_" + System.currentTimeMillis();
        mqttManager.connect(url, clientId2, user, pass);
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