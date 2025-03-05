package fr.kewan.trapsmonitor;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

public class BackgroundService extends Service {
    private static final String TAG = "BackgroundService";
    private MqttClientManager mqttClientManager;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Récupération des préférences sauvegardées
        SharedPreferences sharedPref = getSharedPreferences("MQTTConfig", Context.MODE_PRIVATE);
        String serverHost = sharedPref.getString("serverHost", "192.168.1.104");
        int serverPort = sharedPref.getInt("serverPort", 1883);
        String serverUri = "tcp://" + serverHost + ":" + serverPort;
        String clientId = sharedPref.getString("deviceName", android.os.Build.MODEL);

        // Pour le debug
        Log.d("BackgroundService", "Connexion au serveur MQTT : " + serverUri + " avec clientId " + clientId);

        mqttClientManager = new MqttClientManager(this, serverUri, clientId);
        mqttClientManager.connect();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (mqttClientManager != null && mqttClientManager.isConnected()) {
            mqttClientManager.disconnect();
            Log.d(TAG, "Déconnexion du client MQTT");
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}