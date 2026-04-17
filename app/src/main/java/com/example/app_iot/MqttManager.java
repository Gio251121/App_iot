package com.example.app_iot;

import android.os.Handler;
import android.os.Looper;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class MqttManager {

    private MqttClient mqttClient;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Callback per la connessione
    public interface ConnectionCallback {
        void onSuccess();
        void onFailure(String error);
        void onDisconnected();
    }

    // Callback per la pubblicazione
    public interface PublishCallback {
        void onSuccess();
        void onFailure(String error);
    }

    // NUOVA: Callback per i messaggi in arrivo dall'ESP
    public interface MessageCallback {
        void onMessageArrived(String topic, String message);
    }

    private ConnectionCallback connectionCallback;
    private MessageCallback messageCallback; // Nuova variabile

    public void setConnectionCallback(ConnectionCallback callback) {
        this.connectionCallback = callback;
    }

    // NUOVO: Metodo per impostare la callback dei messaggi
    public void setMessageCallback(MessageCallback callback) {
        this.messageCallback = callback;
    }

    public void connect(String brokerUrl, String clientId, String username, String password) {
        new Thread(() -> {
            try {
                mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());

                mqttClient.setCallback(new MqttCallbackExtended() {
                    @Override
                    public void connectComplete(boolean reconnect, String serverURI) {
                        mainHandler.post(() -> {
                            if (connectionCallback != null) connectionCallback.onSuccess();
                        });
                    }

                    @Override
                    public void connectionLost(Throwable cause) {
                        mainHandler.post(() -> {
                            if (connectionCallback != null) connectionCallback.onDisconnected();
                        });
                    }

                    // MODIFICATO: Ora intercetta i messaggi in arrivo!
                    @Override
                    public void messageArrived(String topic, MqttMessage message) {
                        String payload = new String(message.getPayload());
                        mainHandler.post(() -> {
                            if (messageCallback != null) {
                                messageCallback.onMessageArrived(topic, payload);
                            }
                        });
                    }

                    @Override
                    public void deliveryComplete(IMqttDeliveryToken token) {}
                });

                MqttConnectOptions options = new MqttConnectOptions();
                options.setCleanSession(true);
                options.setAutomaticReconnect(true);

                if (username != null && !username.isEmpty()) {
                    options.setUserName(username);
                    options.setPassword(password.toCharArray());
                }

                mqttClient.connect(options);

                // Nota: Nel tuo codice originale c'era un doppio trigger di onSuccess() qui.
                // È meglio lasciarlo solo nel connectComplete della callback per evitare duplicati.

            } catch (MqttException e) {
                mainHandler.post(() -> {
                    if (connectionCallback != null) connectionCallback.onFailure(e.getMessage());
                });
            }
        }).start();
    }

    // NUOVO: Metodo per iscriversi a un topic
    public void subscribe(String topic, int qos) {
        new Thread(() -> {
            try {
                if (mqttClient != null && mqttClient.isConnected()) {
                    mqttClient.subscribe(topic, qos);
                }
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void publish(String topic, String message, int qos, PublishCallback callback) {
        new Thread(() -> {
            try {
                if (mqttClient != null && mqttClient.isConnected()) {
                    MqttMessage mqttMessage = new MqttMessage(message.getBytes());
                    mqttMessage.setQos(qos);
                    mqttClient.publish(topic, mqttMessage);
                    mainHandler.post(() -> {
                        if (callback != null) callback.onSuccess();
                    });
                } else {
                    mainHandler.post(() -> {
                        if (callback != null) callback.onFailure("Non connesso al broker");
                    });
                }
            } catch (MqttException e) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onFailure(e.getMessage());
                });
            }
        }).start();
    }

    public void disconnect() {
        new Thread(() -> {
            try {
                if (mqttClient != null && mqttClient.isConnected()) {
                    mqttClient.disconnect();
                    mainHandler.post(() -> {
                        if (connectionCallback != null) connectionCallback.onDisconnected();
                    });
                }
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }
}