package fr.kewan.trapsmonitor

import android.Manifest
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.json.JSONObject

class MqttClientManager(private val context: Context, serverUri: String, val clientId: String) {

    var batteryLevelMonitor: BatteryLevelMonitor = BatteryLevelMonitor(this, context)
    private var mqttClient: MqttAndroidClient = MqttAndroidClient(context, serverUri, clientId)

    // Indicateurs pour éviter les notifications répétées
    private var hasShownConnectedNotification = false
    private var hasShownDisconnectedNotification = false

    // Actions pour la notification
    companion object {
        const val ACTION_NOTIFICATION_OK = "fr.kewan.trapsmonitor.ACTION_NOTIFICATION_OK"
        const val ACTION_NOTIFICATION_CLICK = "fr.kewan.trapsmonitor.ACTION_NOTIFICATION_CLICK"
    }

    // BroadcastReceiver pour gérer les actions de notification
    private val notificationActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_NOTIFICATION_OK, ACTION_NOTIFICATION_CLICK -> {
                    publishMessage("notifs/$clientId", "message reçu")
                    NotificationManagerCompat.from(context!!).cancel(1)
                }
            }
        }
    }

    // Enregistrer le receiver pour les deux actions
    init {
        val filter = IntentFilter().apply {
            addAction(ACTION_NOTIFICATION_OK)
            addAction(ACTION_NOTIFICATION_CLICK)
        }
        context.registerReceiver(notificationActionReceiver, filter)

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo: NetworkInfo? = connectivityManager.activeNetworkInfo
        if (networkInfo != null && networkInfo.isConnected) {
            connect() // Connexion au démarrage si le réseau est disponible
        }

        mqttClient.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                batteryLevelMonitor.stopMonitoring()
                wifiUpdateHandler.removeCallbacks(runnableCodeWifiUpdate)
                if (!hasShownDisconnectedNotification) {
                    val toast = Toast.makeText(context, "Connexion au serveur perdue", Toast.LENGTH_LONG)
                    toast.show()
                    Handler(Looper.getMainLooper()).postDelayed({ toast.cancel() }, 3000)
                    hasShownDisconnectedNotification = true
                }
                // Tentative de reconnexion automatique après 5 secondes
                Handler(Looper.getMainLooper()).postDelayed({ connect() }, 5000)
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                if (topic == null) return
                when {
                    topic.startsWith("notifs/") -> {
                        val deviceName = topic.substring(7)
                        if (deviceName.equals(clientId, ignoreCase = true)) {
                            Toast.makeText(context, "$message", Toast.LENGTH_LONG).show()
                            showNotification("$message", "$message")
                        }
                    }
                    topic.startsWith("toasts/") -> {
                        val deviceName = topic.substring(7)
                        if (deviceName.equals(clientId, ignoreCase = true)) {
                            Toast.makeText(context, "$message", Toast.LENGTH_LONG).show()
                        }
                    }
                    topic.startsWith("cmd/") -> {
                        val deviceName = topic.substring(4)
                        if (deviceName.equals(clientId, ignoreCase = true)) {
                            val jsonContent = message.toString()
                            val json = JSONObject(jsonContent)
                            val cmd = json.getString("cmd")
                            when (cmd) {
                                "batteryLevel" -> batteryLevelMonitor.publishBatteryLevel()
                                "batteryChargingStatus" -> batteryLevelMonitor.publishBatteryChargingStatus()
                                "batteryStatus" -> {
                                    batteryLevelMonitor.publishBatteryLevel()
                                    batteryLevelMonitor.publishBatteryChargingStatus()
                                }
                                "wifiInfo" -> wifiInfo()
                                "ping" -> {
                                    val startTime = System.currentTimeMillis()
                                    publishMessage("ping/$clientId", "Ping received")
                                    val endTime = System.currentTimeMillis()
                                    val elapsedTime = endTime - startTime
                                    publishMessage("ping/$clientId", "$elapsedTime")
                                }
                                "update" -> {
                                    val apkUrl = json.getString("url")
                                    updateApp(apkUrl)
                                }
                                "version" -> {
                                    try {
                                        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                                        val version = pInfo.versionName
                                        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                            pInfo.longVersionCode
                                        } else {
                                            pInfo.versionCode.toLong()
                                        }
                                        val jsonObj = JSONObject()
                                        jsonObj.put("versionCode", versionCode)
                                        jsonObj.put("versionName", version)
                                        publishMessage("version/$clientId", jsonObj.toString())
                                    } catch (e: PackageManager.NameNotFoundException) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }
                    }
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {}
        })
    }

    private val wifiUpdateHandler = Handler(Looper.getMainLooper())
    private val runnableCodeWifiUpdate = object : Runnable {
        override fun run() {
            wifiInfo()
            wifiUpdateHandler.postDelayed(this, 60 * 1000 * 10) // 10 minutes
        }
    }

    fun isConnected(): Boolean = mqttClient.isConnected

    fun disconnect() {
        try {
            publishMessage("devices/$clientId", "Disconnected")
            mqttClient.disconnect()
            // Désenregistrer le receiver si besoin
            context.unregisterReceiver(notificationActionReceiver)
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    fun connect() {
        try {
            val options = MqttConnectOptions()
            mqttClient.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    subscribeToTopic("notifs/#")
                    subscribeToTopic("toasts/#")
                    subscribeToTopic("cmd/#")
                    publishMessage("devices/$clientId", "Connected")
                    if (!hasShownConnectedNotification) {
                        val toast = Toast.makeText(context, "Connecté", Toast.LENGTH_LONG)
                        toast.show()
                        Handler(Looper.getMainLooper()).postDelayed({ toast.cancel() }, 3000)
                        hasShownConnectedNotification = true
                    }
                    hasShownDisconnectedNotification = false
                    batteryLevelMonitor.startMonitoring()
                    wifiInfo()
                    wifiUpdateHandler.post(runnableCodeWifiUpdate)
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    batteryLevelMonitor.stopMonitoring()
                    wifiUpdateHandler.removeCallbacks(runnableCodeWifiUpdate)
                    Handler(Looper.getMainLooper()).postDelayed({ connect() }, 5000)
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun wifiInfo() {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(context as AppCompatActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }
        val wifiInfo = wifiManager.connectionInfo
        val jsonObj = JSONObject().apply {
            put("rssi", wifiInfo.rssi)
            put("ssid", wifiInfo.ssid)
            put("ip", wifiInfo.ipAddress)
            put("bssid", wifiInfo.bssid)
            put("linkSpeed", wifiInfo.linkSpeed)
        }
        publishMessage("wifi/$clientId", jsonObj.toString())
    }

    fun subscribeToTopic(topic: String) {
        try {
            mqttClient.subscribe(topic, 1)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun publishMessage(topic: String, message: String, qos: Int = 1) {
        try {
            mqttClient.publish(topic, MqttMessage().apply {
                payload = message.toByteArray()
                this.qos = qos
            })
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    fun updateApp(apkUrl: String) {
        val request = DownloadManager.Request(Uri.parse(apkUrl))
        request.setTitle("Application Update")
        request.setDescription("Downloading update...")
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "update.apk")
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)
    }

    private fun showNotification(title: String, content: String) {
        val notificationId = 1
        val channelId = "mqtt_messages"
        val sharedPref = context.getSharedPreferences("MQTTConfig", AppCompatActivity.MODE_PRIVATE)
        val toggleNotifSound = sharedPref.getBoolean("toggleNotifSound", true)

        // Créer un RemoteViews basé sur votre layout personnalisé
        val customView = RemoteViews(context.packageName, R.layout.notification_custom)
        customView.setTextViewText(R.id.notification_title, title)
        customView.setTextViewText(R.id.notification_text, content)

        // Créer un PendingIntent pour le bouton OK
        val okIntent = Intent(ACTION_NOTIFICATION_OK)
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
        val pendingOkIntent = PendingIntent.getBroadcast(context, 0, okIntent, pendingFlags)
        customView.setOnClickPendingIntent(R.id.notification_ok_button, pendingOkIntent)

        // Créer un PendingIntent pour le clic sur la notification entière
        val clickIntent = Intent(ACTION_NOTIFICATION_CLICK)
        val pendingClickIntent = PendingIntent.getBroadcast(context, 1, clickIntent, pendingFlags)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Messages TRAPS"
            val descriptionText = "Notifications de messages TRAPS"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setCustomContentView(customView)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingClickIntent)
            .setOngoing(true) // La notification reste affichée tant qu'elle n'est pas cliquée

        if (toggleNotifSound) {
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        }

        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
            notify(notificationId, builder.build())
        }
    }

}
