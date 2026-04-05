package com.example.app_iot;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    // ======= CONFIGURAZIONE BROKER =======
    private static final String BROKER_URL = "ssl://eb42853a0afd4d9484cd4561f4a0ebf2.s1.eu.hivemq.cloud:8883";
    private static final String CLIENT_ID  = "android-client-001";
    private static final String USERNAME   = "hivemq.webclient.1775400253585";
    private static final String PASSWORD   = "b&1.7Kf2,?FLiHACv3qc";
    // =====================================

    private MqttManager mqttManager;

    private EditText etTopic, etMessage;
    private Button btnConnect, btnPublish;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Collega le views
        etTopic    = findViewById(R.id.etTopic);
        etMessage  = findViewById(R.id.etMessage);
        btnConnect = findViewById(R.id.btnConnect);
        btnPublish = findViewById(R.id.btnPublish);
        tvStatus   = findViewById(R.id.tvStatus);

        btnPublish.setEnabled(false);
        mqttManager = new MqttManager();

        // Callbacks connessione
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

        // Bottone connetti / disconnetti
        btnConnect.setOnClickListener(v -> {
            if (mqttManager.isConnected()) {
                mqttManager.disconnect();
            } else {
                mqttManager.connect(BROKER_URL, CLIENT_ID, USERNAME, PASSWORD);
                tvStatus.setText("⏳ Connessione in corso...");
            }
        });

        // Bottone pubblica messaggio
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
                    runOnUiThread(() ->
                            Toast.makeText(MainActivity.this, "✅ Messaggio inviato!", Toast.LENGTH_SHORT).show()
                    );
                }

                @Override
                public void onFailure(String error) {
                    runOnUiThread(() ->
                            Toast.makeText(MainActivity.this, "❌ Errore invio: " + error, Toast.LENGTH_SHORT).show()
                    );
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mqttManager.disconnect();
    }
}