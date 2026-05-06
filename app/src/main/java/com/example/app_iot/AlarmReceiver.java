    package com.example.app_iot;

    import android.app.AlarmManager;
    import android.app.NotificationChannel;
    import android.app.NotificationManager;
    import android.app.PendingIntent;
    import android.content.BroadcastReceiver;
    import android.content.Context;
    import android.content.Intent;
    import android.content.SharedPreferences;
    import android.os.Build;
    import android.util.Log;

    import androidx.core.app.NotificationCompat;

    import org.json.JSONObject;

    import java.util.ArrayList;
    import java.util.Calendar;
    import java.util.Collections;
    import java.util.Iterator;
    import java.util.List;
    import java.util.Locale;

    public class AlarmReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String nomePillola = intent.getStringExtra("nome_pillola");
            if (nomePillola == null) nomePillola = "La tua terapia";

            // ========================================================
            // 1. MOSTRA LA NOTIFICA SULLO SCHERMO
            // ========================================================
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            String channelId = "pillole_channel";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(channelId, "Promemoria Pillole", NotificationManager.IMPORTANCE_HIGH);
                manager.createNotificationChannel(channel);
            }

            // Se clicca la notifica, apre l'app
            Intent mainIntent = new Intent(context, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context, 0, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("È l'ora della tua pillola")
                    .setContentText("Devi prendere: " + nomePillola)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true);

            // Fa suonare il telefono!
            manager.notify(100, builder.build());

            // ========================================================
            // 2. REAZIONE A CATENA: CALCOLA LA PROSSIMA IN BACKGROUND
            // ========================================================
            boolean Test = intent.getBooleanExtra("test", false);

            if (!Test) {
                programmaProssimaPillola(context);
            }
        }

        // Questa funzione lavora al buio, senza aprire l'app
        private void programmaProssimaPillola(Context context) {
            SharedPreferences prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
            String jsonString = prefs.getString("prescrizioni_salvate", "{}");

            try {
                JSONObject prescrizioni = new JSONObject(jsonString);
                Calendar cal = Calendar.getInstance();

                // Aggiungiamo 1 minuto per evitare che trovi di nuovo la pillola che sta suonando ORA
                cal.add(Calendar.MINUTE, 1);

                int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
                int oggi = (dayOfWeek == Calendar.SUNDAY) ? 7 : dayOfWeek - 1;

                int hAdesso = cal.get(Calendar.HOUR_OF_DAY);
                int mAdesso = cal.get(Calendar.MINUTE);
                String oraAttuale = String.format(Locale.getDefault(), "%02d:%02d", hAdesso, mAdesso);

                // Cerchiamo la PROSSIMA pillola (come faceva MainActivity)
                for (int i = 0; i < 7; i++) {
                    int giornoDaControllare = (oggi + i - 1) % 7 + 1;
                    String giornoStr = String.valueOf(giornoDaControllare);

                    if (prescrizioni.has(giornoStr)) {
                        JSONObject pilloleGiorno = prescrizioni.getJSONObject(giornoStr);

                        // Ordina gli orari
                        List<String> orari = new ArrayList<>();
                        Iterator<String> keys = pilloleGiorno.keys();
                        while(keys.hasNext()) orari.add(keys.next());
                        Collections.sort(orari);

                        // Trova la prossima dose
                        for (String ora : orari) {
                            if (i == 0) {
                                if (ora.compareTo(oraAttuale) > 0) {
                                    impostaAllarme(context, i, ora, pilloleGiorno.getJSONObject(ora).getString("m"));
                                    return; // Finito! Trovata ed impostata.
                                }
                            } else {
                                impostaAllarme(context, i, ora, pilloleGiorno.getJSONObject(ora).getString("m"));
                                return; // Finito! Trovata ed impostata.
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Carica fisicamente l'allarme in Android
        private void impostaAllarme(Context context, int giorniAggiuntivi, String ora, String nomePillola) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) return;
            }

            Intent intent = new Intent(context, AlarmReceiver.class);
            intent.putExtra("nome_pillola", nomePillola);

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            String[] parti = ora.split(":");
            Calendar calAllarme = Calendar.getInstance();
            calAllarme.add(Calendar.DAY_OF_YEAR, giorniAggiuntivi);
            calAllarme.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parti[0]));
            calAllarme.set(Calendar.MINUTE, Integer.parseInt(parti[1]));
            calAllarme.set(Calendar.SECOND, 0);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calAllarme.getTimeInMillis(), pendingIntent);
            }
        }
    }