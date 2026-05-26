package com.example.app_iot;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

public class MappaActivity extends AppCompatActivity {

    private MapView mapView;
    private MqttManager mqttManager;
    private final String topicRichiesta = "esp/semafori/request";
    private final String topicRisposta = "esp/semafori";

    private Marker ultimoMarkerCliccato = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Inizializzazione della configurazione di Osmdroid per la gestione della cache
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));

        setContentView(R.layout.activity_mappa);

        // Configurazione Toolbar e gestione freccia indietro
        Toolbar toolbar = findViewById(R.id.toolbarMappa);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        // Inizializzazione mappa grafica
        mapView = findViewById(R.id.mapView);
        mapView.setBuiltInZoomControls(true);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(6.0); // Zoom iniziale sull'Italia
        mapView.getController().setCenter(new GeoPoint(41.8719, 12.5674));

        // Avvio client di rete MQTT
        mqttManager = new MqttManager();
        String url = BuildConfig.BROKER_URL;
        String clientId = BuildConfig.CLIENT_ID + "_map_" + Math.random();
        String userMqtt = BuildConfig.USERNAME;
        String passMqtt = BuildConfig.PASSWORD;

        // Configurazione ricevitore messaggi
        mqttManager.setMessageCallback((topic, message) -> {
            if (topic.equals(topicRisposta)) {
                try {
                    // Parsing della risposta JSON contenente l'elenco dei dispositivi
                    JSONArray jsonArray = new JSONArray(message);

                    runOnUiThread(() -> {
                        mapView.getOverlays().clear(); // Pulizia vecchi marker

                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject semaforo = jsonArray.optJSONObject(i);
                            if (semaforo == null) continue;

                            double lat = semaforo.optDouble("latitudine", 0.0);
                            double lon = semaforo.optDouble("longitudine", 0.0);
                            String seriale = semaforo.optString("codice_seriale", "N/D");
                            String incrocio = semaforo.optString("nome_incrocio", "Sconosciuto");

                            if (lat != 0.0 && lon != 0.0) {
                                // Creazione fisica del marker sulla mappa geografica
                                Marker marker = new Marker(mapView);
                                marker.setPosition(new GeoPoint(lat, lon));
                                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                                marker.setTitle("Incrocio: " + incrocio);
                                marker.setSnippet("Seriale: " + seriale);

// Gestione del click sul marker per il passaggio dei dati
                                marker.setOnMarkerClickListener(new Marker.OnMarkerClickListener() {
                                    @Override
                                    public boolean onMarkerClick(Marker m, MapView mv) {
                                        // Se il riquadro informativo è già aperto, il click successivo esegue il reindirizzamento
                                        if (m.equals(ultimoMarkerCliccato) && m.isInfoWindowOpen()) {

                                            Intent intent = new Intent(MappaActivity.this, LoginActivity.class);
                                            intent.putExtra("seriale_cliccato", seriale);
                                            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                            startActivity(intent);
                                            finish();

                                        } else {
                                            // Primo click su un nuovo marker: mostra le info, centra la visuale e aggiorna la memoria
                                            m.showInfoWindow();
                                            mv.getController().animateTo(m.getPosition()); // UX: sposta fluidamente la mappa sul marker

                                            // Salvataggio del riferimento per il prossimo click
                                            ultimoMarkerCliccato = m;
                                        }
                                        return true;
                                    }
                                });

                                mapView.getOverlays().add(marker);
                            }
                        }
                        mapView.invalidate(); // Forza il ridisegno grafico della mappa
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        // Connessione ed invio richiesta trigger
        mqttManager.setConnectionCallback(new MqttManager.ConnectionCallback() {
            @Override
            public void onSuccess() {
                mqttManager.subscribe(topicRisposta, 1);
                // Invio stringa vuota di trigger sul canale di richiesta fisso
                mqttManager.publish(topicRichiesta, "get", 1, null);
            }

            @Override
            public void onFailure(String error) {
                Toast.makeText(MappaActivity.this, "Errore rete mappa", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDisconnected() {}
        });

        mqttManager.connect(url, clientId, userMqtt, passMqtt);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mqttManager != null) mqttManager.disconnect();
    }
}