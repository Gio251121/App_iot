package com.example.app_iot;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.app.AppCompatActivity;

public class ProvaActivity extends AppCompatActivity {

    public static final String BROKER_URL = BuildConfig.BROKER_URL;
    public static final String CLIENT_ID  = BuildConfig.CLIENT_ID;
    public static final String USERNAME   = BuildConfig.USERNAME;
    public static final String PASSWORD   = BuildConfig.PASSWORD;

    private MqttManager mqttManager;

    private EditText etTopic, etMessage;
    private Button btnConnect, btnPublish, btnTestNotifica;
    private TextView tvStatus;

    @SuppressLint("ScheduleExactAlarm")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prova);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("");
        }

        etTopic    = findViewById(R.id.etTopic);
        etMessage  = findViewById(R.id.etMessage);
        btnConnect = findViewById(R.id.btnConnect);
        btnPublish = findViewById(R.id.btnPublish);
        tvStatus   = findViewById(R.id.tvStatus);

        // NUOVO BOTTONE PER TESTARE LA NOTIFICA
        btnTestNotifica = findViewById(R.id.btnTestNotifica);

        btnPublish.setEnabled(false);
        mqttManager = new MqttManager();

        mqttManager.setConnectionCallback(new MqttManager.ConnectionCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    tvStatus.setText("✅ Connesso");
                    btnConnect.setText("Disconnetti");
                    btnPublish.setEnabled(true);
                });
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    tvStatus.setText("❌ Errore: " + error);
                    btnConnect.setText("Connetti");
                    btnPublish.setEnabled(false);
                });
            }

            @Override
            public void onDisconnected() {
                runOnUiThread(() -> {
                    tvStatus.setText("⚪ Disconnesso");
                    btnConnect.setText("Connetti");
                    btnPublish.setEnabled(false);
                });
            }
        });

        btnConnect.setOnClickListener(v -> {
            if (mqttManager.isConnected()) {
                mqttManager.disconnect();
            } else {
                mqttManager.connect(BROKER_URL, CLIENT_ID, USERNAME, PASSWORD);
                tvStatus.setText("⏳ Connessione in corso...");
            }
        });

        btnPublish.setOnClickListener(v -> {
            String topic   = etTopic.getText().toString();
            String message = etMessage.getText().toString();

            if (topic.isEmpty() || message.isEmpty()) {
                Toast.makeText(this, "Inserisci topic e messaggio", Toast.LENGTH_SHORT).show();
                return;
            }

            mqttManager.publish(topic, message, 1, new MqttManager.PublishCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> Toast.makeText(ProvaActivity.this, "✅ Messaggio inviato!", Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onFailure(String error) {
                    runOnUiThread(() -> Toast.makeText(ProvaActivity.this, "❌ Errore invio: " + error, Toast.LENGTH_SHORT).show());
                }
            });
        });

        btnTestNotifica.setOnClickListener(v -> {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

            // CONTROLLO DI SICUREZZA PER ANDROID 12+ (API 31+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    Toast.makeText(this, "Devi concedere il permesso per le sveglie esatte nelle impostazioni!", Toast.LENGTH_LONG).show();
                    // Opzionale: apri le impostazioni per l'utente
                    Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    startActivity(intent);
                    return; // Ferma il codice qui per evitare il crash
                }
            }

            Toast.makeText(this, "Sveglia impostata! Chiudi l'app e aspetta 5 secondi...", Toast.LENGTH_LONG).show();

            Intent intent = new Intent(this, AlarmReceiver.class);
            intent.putExtra("nome_pillola", "Pillola di Test");

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    this, 999, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            long tempoSveglia = System.currentTimeMillis() + 5000;

            // Imposta l'allarme (ora che sappiamo di avere il permesso)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, tempoSveglia, pendingIntent);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mqttManager.disconnect();
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
}