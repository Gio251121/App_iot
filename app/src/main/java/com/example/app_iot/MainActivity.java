package com.example.app_iot;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class MainActivity extends AppCompatActivity {

    private MqttManager mqttManager;
    private TextView tvConnStatus;
    private Button btnRiprova;

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

        btnRiprova.setVisibility(View.GONE);

        mqttManager = new MqttManager();
        mqttManager.setConnectionCallback(new MqttManager.ConnectionCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    tvConnStatus.setText("🟢 Dispositivo online");
                    tvConnStatus.setTextColor(0xFF4CAF50);
                    btnRiprova.setVisibility(View.GONE);
                });
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
        if (!mqttManager.isConnected()) {
            connetti();
        }
    }

    private void connetti() {
        tvConnStatus.setText("⏳ Connessione in corso...");
        tvConnStatus.setTextColor(0xFFAAAAAA);
        btnRiprova.setVisibility(View.GONE);
        mqttManager.connect(ProvaActivity.BROKER_URL, ProvaActivity.CLIENT_ID, ProvaActivity.USERNAME, ProvaActivity.PASSWORD);
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
    }

}