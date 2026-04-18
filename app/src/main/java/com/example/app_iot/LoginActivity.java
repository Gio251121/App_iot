package com.example.app_iot;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        EditText etUser = findViewById(R.id.etUser);
        EditText etPassword = findViewById(R.id.etPassword);
        Button btnAccedi = findViewById(R.id.btnAccedi);

        btnAccedi.setOnClickListener(v -> {
            String user = etUser.getText().toString();
            String pass = etPassword.getText().toString();

            // FINTO DATABASE: Controlla credenziali provvisorie
            if (user.equals("admin") && pass.equals("1234")) {

                // 1. Salva in memoria che l'accesso è avvenuto con successo
                SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                prefs.edit().putBoolean("isLogged", true).apply();

                // 2. Messaggio di benvenuto
                Toast.makeText(this, "Accesso eseguito!", Toast.LENGTH_SHORT).show();

                // 3. Porta l'utente alla Home Page (MainActivity)
                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                startActivity(intent);

                // 4. Chiudi la pagina di login per non farci tornare l'utente col tasto "Indietro"
                finish();

            } else {
                Toast.makeText(this, "Utente o password errati!", Toast.LENGTH_SHORT).show();
            }
        });
    }
}