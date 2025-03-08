package fr.kewan.trapsmonitor

import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var mqttClientManager: MqttClientManager
    private lateinit var serverHost: EditText
    private lateinit var serverPort: EditText
    private lateinit var deviceName: EditText
    private lateinit var toogleNotifSound: SwitchCompat

    private lateinit var modifyButton: Button    // Bouton "Modifier"
    private lateinit var saveButton: Button      // Bouton "Enregistrer" (utilise l'ID saveButton3)
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var testNotifButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Récupération des vues depuis le layout
        serverHost = findViewById(R.id.serverHost)
        serverPort = findViewById(R.id.serverPort)
        deviceName = findViewById(R.id.deviceName)
        toogleNotifSound = findViewById(R.id.toogleNotifSound)

        modifyButton = findViewById(R.id.modifyButton)
        saveButton = findViewById(R.id.saveButton3) // On utilise le bouton saveButton3 pour enregistrer
        connectButton = findViewById(R.id.connectButton)
        disconnectButton = findViewById(R.id.disconnectButton)
        testNotifButton = findViewById(R.id.testNotifButton)

        // Par défaut, les champs sont en lecture seule et le bouton "Enregistrer" est masqué
        setFieldsEnabled(false)
        saveButton.visibility = View.GONE
        modifyButton.visibility = View.VISIBLE

        // Chargement des préférences depuis SharedPreferences
        val sharedPref = getSharedPreferences("MQTTConfig", MODE_PRIVATE)

        // Charger également depuis le fichier traps_config.txt (si présent)
        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "traps_config.txt"
        )
        if (file.exists()) {
            val lines = file.readLines()
            for (line in lines) {
                val (key, value) = line.split("=")
                when (key) {
                    "serverHost" -> serverHost.setText(value)
                    "serverPort" -> serverPort.setText(value)
                    "deviceName" -> deviceName.setText(value)
                    "toogleNotifSound" -> toogleNotifSound.isChecked = value.toBoolean()
                }
            }
        }

        // Charger également les préférences enregistrées
        if (sharedPref.contains("serverHost")) {
            serverHost.setText(sharedPref.getString("serverHost", "192.168.1.104"))
        }
        if (sharedPref.contains("serverPort")) {
            serverPort.setText(sharedPref.getInt("serverPort", 1883).toString())
        }
        if (sharedPref.contains("deviceName")) {
            deviceName.setText(sharedPref.getString("deviceName", Build.MODEL))
        }
        if (sharedPref.contains("toogleNotifSound")) {
            toogleNotifSound.isChecked = sharedPref.getBoolean("toogleNotifSound", true)
        }

        // Instanciation initiale du MqttClientManager avec des valeurs vides (sera réinitialisé lors de la connexion)
        mqttClientManager = MqttClientManager(this, "", "")

        // Bouton "Modifier": active l'édition et affiche le bouton "Enregistrer"
        modifyButton.setOnClickListener {
            setFieldsEnabled(true)
            modifyButton.visibility = View.GONE
            saveButton.visibility = View.VISIBLE
        }

        // Bouton "Enregistrer": sauvegarde les préférences et désactive l'édition
        saveButton.setOnClickListener {
            savePreferences()
            Toast.makeText(this, "Préférences sauvegardées", Toast.LENGTH_SHORT).show()
            setFieldsEnabled(false)
            saveButton.visibility = View.GONE
            modifyButton.visibility = View.VISIBLE
        }

        connectButton.setOnClickListener {
            // Sauvegarde avant connexion pour s'assurer que la config est à jour
            savePreferences()
            if (!mqttClientManager.isConnected()) {
                connectToServer()
            } else {
                Toast.makeText(this, "Déjà connecté", Toast.LENGTH_SHORT).show()
            }
        }

        disconnectButton.setOnClickListener {
            mqttClientManager.disconnect()
        }

        testNotifButton.setOnClickListener {
            mqttClientManager.publishMessage("notifs/${mqttClientManager.clientId}", "Test de notification")
        }

        // Connexion automatique au démarrage (si les préférences sont valides)
        connectToServer()
    }

    /**
     * Active ou désactive l'édition des champs de configuration.
     */
    private fun setFieldsEnabled(enabled: Boolean) {
        serverHost.isEnabled = enabled
        serverPort.isEnabled = enabled
        deviceName.isEnabled = enabled
        toogleNotifSound.isEnabled = enabled
    }

    /**
     * Récupère les valeurs enregistrées et tente la connexion au broker MQTT.
     */
    private fun connectToServer() {
        val sharedPref = getSharedPreferences("MQTTConfig", MODE_PRIVATE)
        val host = sharedPref.getString("serverHost", "192.168.1.104")
        val port = sharedPref.getInt("serverPort", 1883)
        val name = sharedPref.getString("deviceName", Build.MODEL)

        if (host.isNullOrEmpty()) {
            Toast.makeText(this, "Veuillez renseigner l'adresse du serveur", Toast.LENGTH_SHORT).show()
            return
        }

        val brokerUrl = "tcp://$host:$port"
        mqttClientManager = MqttClientManager(this, brokerUrl, name ?: Build.MODEL)

        try {
            mqttClientManager.connect()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Impossible de se connecter au serveur", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Sauvegarde la configuration dans les SharedPreferences et dans le fichier traps_config.txt.
     */
    private fun savePreferences() {
        val host = serverHost.text.toString()
        val port = serverPort.text.toString().toIntOrNull() ?: 80
        val name = deviceName.text.toString()
        val notifSound = toogleNotifSound.isChecked

        val sharedPref = getSharedPreferences("MQTTConfig", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("serverHost", host)
            putInt("serverPort", port)
            putString("deviceName", name)
            putBoolean("toogleNotifSound", notifSound)
            apply()
        }

        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "traps_config.txt"
        )
        file.writeText("serverHost=$host\nserverPort=$port\ndeviceName=$name\ntoogleNotifSound=$notifSound\n")
    }

    override fun onDestroy() {
        mqttClientManager.publishMessage("devices/${mqttClientManager.clientId}", "Disconnected")
        mqttClientManager.disconnect()
        super.onDestroy()
    }
}
