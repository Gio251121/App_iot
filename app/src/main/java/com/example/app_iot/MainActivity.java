package com.example.app_iot;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.cardview.widget.CardView;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.config.Configuration;
import android.preference.PreferenceManager;
import android.content.Context;


import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private MqttManager mqttManager;
    private TextView tvConnStatus;

    private MapView mapViewIncrocio;
    private Button btnRiprova;
    //private Button btnProva;
    private CardView cardRossa, cardGialla, cardVerde;

    private TextView tvTemperatura;
    private TextView tvUmidita;

    // Nuovi campi per il semaforo
    private TextView tvNomeIncrocio;
    private TextView tvCodiceSeriale;
    private TextView tvStatoSemaforo;

    private String codiceSemaforoSalvato;
    private String topicDati;

    private View luceRossa, luceGialla, luceVerde;
    private LinearLayout pannelloControllo;
    private String topicLuce;

    private TextView tvLuminosita, tvTraffico, tvAllerte;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("");
        }

        tvLuminosita = findViewById(R.id.tvLuminosita);
        tvTraffico = findViewById(R.id.tvTraffico);
        tvAllerte = findViewById(R.id.tvAllerte);

        cardRossa = findViewById(R.id.cardRossa);
        cardGialla = findViewById(R.id.cardGialla);
        cardVerde = findViewById(R.id.cardVerde);

        // 1. Collegamento elementi grafici
        tvConnStatus = findViewById(R.id.tvConnStatus);
        btnRiprova = findViewById(R.id.btnRiprova);
        //btnProva = findViewById(R.id.btnProva);
        tvTemperatura = findViewById(R.id.tvTemperatura);
        tvUmidita = findViewById(R.id.tvUmidita);

        tvNomeIncrocio = findViewById(R.id.tvNomeIncrocio);
        tvCodiceSeriale = findViewById(R.id.tvCodiceSeriale);
        //tvStatoSemaforo = findViewById(R.id.tvStatoSemaforo);

        Button btnSpegniSemaforo = findViewById(R.id.btnSpegniSemaforo);
        Button btnAccendiSemaforo = findViewById(R.id.btnAccendiSemaforo);
        Button btnInattivoSemaforo = findViewById(R.id.btnInattivoSemaforo);

        btnRiprova.setVisibility(View.GONE);

        // 2. Recupero i dati del semaforo dal Login
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        codiceSemaforoSalvato = prefs.getString("codice_seriale_salvato", "Sconosciuto");
        String nomeIncrocioSalvato = prefs.getString("nome_incrocio_salvato", "Incrocio non definito");
        String ruolo = prefs.getString("ruolo_utente", "utente"); // Estrazione ruolo

        pannelloControllo = findViewById(R.id.pannelloControllo);

        mapViewIncrocio = findViewById(R.id.mapViewIncrocio);
        mapViewIncrocio.setBuiltInZoomControls(false); // Disattiva i tasti +/- grafici per mantenere pulito il layout
        mapViewIncrocio.setMultiTouchControls(true);   // Permette lo zoom tramite pinch-to-zoom delle dita


        // Recupero le coordinate salvate nelle SharedPreferences
        float latSemaforo = prefs.getFloat("lat_semaforo", 0.0f);
        float lonSemaforo = prefs.getFloat("lon_semaforo", 0.0f);

        // Se le coordinate estratte sono valide, configura la vista geografica
        if (latSemaforo != 0.0f && lonSemaforo != 0.0f) {
            GeoPoint posizioneSemaforo = new GeoPoint(latSemaforo, lonSemaforo);

            // Configurazione dell'inquadratura iniziale (Zoom 17.0 mappa l'incrocio in dettaglio)
            mapViewIncrocio.getController().setZoom(17.0);
            mapViewIncrocio.getController().setCenter(posizioneSemaforo);

            // Generazione del Marker descrittivo dell'impianto controllato
            Marker marker = new Marker(mapViewIncrocio);
            marker.setPosition(posizioneSemaforo);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setTitle(nomeIncrocioSalvato);
            marker.setSnippet("Seriale: " + codiceSemaforoSalvato);

            // Inserimento del marker nell'overlay della mappa
            mapViewIncrocio.getOverlays().add(marker);
            mapViewIncrocio.invalidate(); // Forza il refresh asincrono della UI grafica
        }



        // Controllo permessi di visualizzazione (RBAC)
        if (ruolo.equals("admin") || ruolo.equals("manutentore")) {
            pannelloControllo.setVisibility(View.VISIBLE); // Abilita comandi per staff autorizzato
        } else {
            pannelloControllo.setVisibility(View.GONE); // Nasconde comandi per utente standard
        }

        topicDati = "esp/dati";
        topicLuce = "esp/luce"; // Topic dedicato per evitare overlap tra incroci

        tvCodiceSeriale.setText("Seriale: " + codiceSemaforoSalvato);
        tvNomeIncrocio.setText(nomeIncrocioSalvato);

        // 4. Azioni dei bottoni
        btnAccendiSemaforo.setOnClickListener(v -> inviaComando("acceso"));
        btnInattivoSemaforo.setOnClickListener(v -> inviaComando("inattivo"));
        btnSpegniSemaforo.setOnClickListener(v -> inviaComando("spento"));

        //btnProva.setOnClickListener(v -> {
           // if (mqttManager != null) mqttManager.disconnect();
          //  startActivity(new Intent(this, ProvaActivity.class));
       // });
        btnRiprova.setOnClickListener(v -> connetti());

        // 5. Inizializzo MQTT
        mqttManager = new MqttManager();

        mqttManager.setMessageCallback((topic, message) -> {
            if (topic.equals(topicDati)) {
                try {
                    JSONObject jsonObject = new JSONObject(message);

                    // Parsing dati ambientali (usiamo optString per gestire in sicurezza i valori "null")
                    String tempStr = jsonObject.optString("temperatura", "null");
                    String umidStr = jsonObject.optString("umidita", "null");
                    String traffico = jsonObject.optString("traffico", "N/D");
                    int luminosita = jsonObject.optInt("luminosita", 0);
                    boolean piove = jsonObject.optBoolean("acqua", false);

                    // Analisi dati inerziali per rilevamento terremoto/urti
                    boolean terremotoRilevato = false;
                    JSONObject acc = jsonObject.optJSONObject("accelerometro");
                    if (acc != null) {
                        double ax = acc.optDouble("x", 0);
                        double ay = acc.optDouble("y", 0);
                        double az = acc.optDouble("z", 0);
                        // Calcolo vettoriale semplificato. Una soglia > 1.5g indica un movimento anomalo del palo
                        if (Math.abs(ax) > 1.5 || Math.abs(ay) > 1.5 || Math.abs(az) > 1.5) {
                            terremotoRilevato = true;
                        }
                    }

                    // Inizializzazione buffer per le stringhe di allerta
                    StringBuilder allerte = new StringBuilder();

                    // Check condizioni critiche
                    if (terremotoRilevato) allerte.append("⚠️ TERREMOTO/URTO RILEVATO!\n");
                    if (piove) allerte.append("⚠️ Strada Bagnata\n");
                    if (luminosita < 20) allerte.append("⚠️ SCARSA VISIBILITÀ\n");

                    // Verifica soglie temperatura (se il sensore non restituisce null)
                    if (!tempStr.equals("null") && !tempStr.isEmpty()) {
                        try {
                            double temp = Double.parseDouble(tempStr);
                            if (temp > 40) allerte.append("⚠️ CALDO ESTREMO\n");
                            if (temp < 3) allerte.append("⚠️ RISCHIO GHIACCIO\n");
                        } catch (NumberFormatException e) {
                            // Ignora parsing errato
                        }
                    }

                    // Esecuzione sul main thread per aggiornare la UI
                    boolean finalTerremotoRilevato = terremotoRilevato;
                    runOnUiThread(() -> {
                        // Aggiornamento campi testo UI
                        tvTemperatura.setText(tempStr.equals("null") ? "--°C" : tempStr + "°C");
                        tvUmidita.setText(umidStr.equals("null") ? "--%" : umidStr + "%");
                        tvLuminosita.setText(luminosita + "%");
                        tvTraffico.setText(traffico.toUpperCase());

                        // Gestione visibilità box allerte
                        if (allerte.length() > 0) {
                            tvAllerte.setVisibility(View.VISIBLE);
                            tvAllerte.setText(allerte.toString().trim());
                        } else {
                            tvAllerte.setVisibility(View.GONE); // Nascondi se non ci sono problemi
                        }
                    });

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } else if (topic.equals(topicLuce)) {
                // Gestione indipendente del colore semaforo (già implementata)
                runOnUiThread(() -> aggiornaSemaforoUI(message.trim().toLowerCase()));
            }
        });




        mqttManager.setConnectionCallback(new MqttManager.ConnectionCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    tvConnStatus.setText("🟢 Dispositivo online");
                    tvConnStatus.setTextColor(0xFF4CAF50);
                    // Forza SEMPRE la scomparsa del bottone in caso di successo
                    btnRiprova.setVisibility(View.GONE);
                });
                mqttManager.subscribe(topicDati, 1);
                mqttManager.subscribe(topicLuce, 1);
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
                    // Controllo di sicurezza: Mostra offline e il bottone riprova
                    // SOLO SE il manager risulta effettivamente scollegato in questo istante
                    if (!mqttManager.isConnected()) {
                        tvConnStatus.setText("⚪ Dispositivo offline");
                        tvConnStatus.setTextColor(0xFFAAAAAA);
                        btnRiprova.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }

    private void aggiornaSemaforoUI(String statoLuce) {
        // Reset a colori "spenti" (opacità 33% - HEX 55)
        cardRossa.setCardBackgroundColor(0x55FF0000);
        cardGialla.setCardBackgroundColor(0x55FFFF00);
        cardVerde.setCardBackgroundColor(0x5500FF00);

        // Accende il colore ricevuto (opacità 100% - HEX FF)
        switch (statoLuce) {
            case "rosso":
                cardRossa.setCardBackgroundColor(0xFFFF0000);
                break;
            case "giallo":
                cardGialla.setCardBackgroundColor(0xFFFFFF00);
                break;
            case "verde":
                cardVerde.setCardBackgroundColor(0xFF00FF00);
                break;
        }
    }


    private void inviaComando(String payload) {
        String topicComando = "esp/comandi";

        mqttManager.publish(topicComando, payload, 1, new MqttManager.PublishCallback() {
            @Override
            public void onSuccess() {
                // Nessun crash su elementi grafici di testo rimossi, la UI risponderà alla ricezione su topic 'esp/luce'
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
        // Risveglia la mappa per scaricare i nuovi settori
        if (mapViewIncrocio != null) {
            mapViewIncrocio.onResume();
        }

        if (mqttManager != null) {
            mqttManager.disconnect();
            connetti();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Congela la mappa per risparmiare risorse quando esci dall'app
        if (mapViewIncrocio != null) {
            mapViewIncrocio.onPause();
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