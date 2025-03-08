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

    private lateinit var modifyButton: Button    // Bouton Modifier/Enregistrer
    private lateinit var connectButton: Button   // Bouton Connecter/Déconnecter
    private lateinit var testNotifButton: Button   // Bouton Test
    private lateinit var closeButton: Button       // Bouton Fermer

    // Flag pour savoir si on est en mode édition
    private var isEditing = false

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
        connectButton = findViewById(R.id.connectButton)
        testNotifButton = findViewById(R.id.testNotifButton)
        closeButton = findViewById(R.id.closeButton)

        // Par défaut, les champs sont en lecture seule
        setFieldsEnabled(false)
        // On démarre avec le bouton Modifier et celui de connexion affiché en "Connecter"
        modifyButton.text = getString(R.string.btn_modify)
        connectButton.text = getString(R.string.btn_connect)

        // Charger la configuration depuis SharedPreferences et le fichier externe
        loadPreferences()

        // Instanciation initiale du client MQTT avec des valeurs vides (sera réinitialisé lors de la connexion)
        mqttClientManager = MqttClientManager(this, "", "")

        // Bouton Modifier/Enregistrer : bascule entre mode lecture seule et édition
        modifyButton.setOnClickListener {
            if (!isEditing) {
                // Passage en mode édition
                setFieldsEnabled(true)
                modifyButton.text = getString(R.string.btn_save)
                isEditing = true
            } else {
                // Enregistrement des modifications et retour en mode lecture seule
                savePreferences()
                Toast.makeText(this, getString(R.string.preferences_saved), Toast.LENGTH_SHORT).show()
                setFieldsEnabled(false)
                modifyButton.text = getString(R.string.btn_modify)
                isEditing = false
            }
        }

        // Bouton Connecter/Déconnecter : bascule selon l'état de la connexion MQTT
        connectButton.setOnClickListener {
            if (!mqttClientManager.isConnected()) {
                savePreferences() // S'assurer que la configuration est à jour
                connectToServer()
                connectButton.text = getString(R.string.btn_disconnect)
            } else {
                mqttClientManager.disconnect()
                connectButton.text = getString(R.string.btn_connect)
            }
        }

        // Bouton Test : envoie un message de test via MQTT
        testNotifButton.setOnClickListener {
            mqttClientManager.publishMessage("notifs/${mqttClientManager.clientId}", "Test de notification")
        }

        // Bouton Fermer : ferme l'activité sans quitter l'application
        closeButton.setOnClickListener {
            finish()
        }

        // Tentative de connexion automatique au démarrage
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
     * Charge la configuration depuis SharedPreferences et, si présent, depuis le fichier traps_config.txt.
     */
    private fun loadPreferences() {
        val sharedPref = getSharedPreferences("MQTTConfig", MODE_PRIVATE)

        // Chargement depuis le fichier traps_config.txt dans le dossier Downloads (si disponible)
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "traps_config.txt")
        if (file.exists()) {
            val lines = file.readLines()
            for (line in lines) {
                val parts = line.split("=")
                if (parts.size >= 2) {
                    val key = parts[0]
                    val value = parts[1]
                    when (key) {
                        "serverHost" -> serverHost.setText(value)
                        "serverPort" -> serverPort.setText(value)
                        "deviceName" -> deviceName.setText(value)
                        "toogleNotifSound" -> toogleNotifSound.isChecked = value.toBoolean()
                    }
                }
            }
        }

        // Chargement depuis SharedPreferences
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
    }

    /**
     * Tente la connexion au serveur MQTT en utilisant la configuration sauvegardée.
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
     * Sauvegarde la configuration dans SharedPreferences et dans le fichier traps_config.txt.
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

        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "traps_config.txt")
        file.writeText("serverHost=$host\nserverPort=$port\ndeviceName=$name\ntoogleNotifSound=$notifSound\n")
    }

    override fun onDestroy() {
        mqttClientManager.publishMessage("devices/${mqttClientManager.clientId}", "Disconnected")
        mqttClientManager.disconnect()
        super.onDestroy()
    }
}
