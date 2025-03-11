# Traps Monitor

**Traps Monitor** est une application Android conçue pour surveiller et transmettre diverses informations d'un appareil via MQTT. L'application est optimisée pour fonctionner sur des appareils allant d'Android 4.4 (KitKat) à Android 11.

## Fonctionnalités

- **Transmission d'informations via MQTT**  
  - Envoi du niveau de batterie, de l'état de charge et d'autres informations système.
  - Transmission des informations Wi-Fi (RSSI, SSID, BSSID, vitesse de connexion, etc.).
  - Notification du broker lors de la connexion et de la déconnexion du périphérique.

- **Réception et traitement de commandes MQTT**  
  - L'application répond à diverses commandes envoyées par le broker, telles que `batteryLevel`, `batteryChargingStatus`, `wifiInfo`, `ping`, `update` et `version`.
  - Les réponses et informations sont publiées sur des topics dédiés.

- **Interface de configuration conviviale**  
  - Affiche la configuration actuelle (adresse du broker, port, nom du device, etc.) en lecture seule.
  - Permet de modifier les paramètres en appuyant sur le bouton **Modifier**, puis de sauvegarder avec **Enregistrer**.
  - La configuration est stockée dans les SharedPreferences et dans un fichier `traps_config.txt` dans le dossier Downloads.

- **Notifications personnalisées**  
  - Lors de la réception d'un message MQTT, une notification avec un layout personnalisé s'affiche, mettant en valeur le texte avec une taille augmentée.
  - La notification inclut un bouton **OK** et réagit également au clic global pour envoyer le message MQTT `"message reçu"` sur un topic spécifique (par exemple, `ack/[deviceId]`) avant de se fermer.

- **Service de fond et démarrage automatique**  
  - Un service de fond maintient la connexion au broker MQTT en continu.
  - Un BootReceiver lance automatiquement ce service au démarrage de l'appareil, assurant ainsi une connexion persistante même en arrière-plan.

## Architecture

- **MqttClientManager**  
  Gère la connexion MQTT, la publication et la réception des messages.  
  - Implémente la logique pour les notifications personnalisées.
  - Gère les indicateurs pour éviter l'affichage répété des messages de connexion/déconnexion.

- **BatteryLevelMonitor**  
  Surveille et publie le niveau de batterie et l'état de charge.

- **Interface Utilisateur**  
  Un seul écran propose trois boutons principaux :
  - **Modifier/Enregistrer** : Bascule entre le mode lecture seule et le mode édition pour la configuration.
  - **Connecter/Déconnecter** : Permet d'établir ou de rompre la connexion MQTT.
  - **Test** : Permet d'envoyer un message de test via MQTT.
  
- **Service de fond et BootReceiver**  
  Le service de fond assure une connexion continue au broker, tandis que le BootReceiver démarre ce service dès le démarrage de l'appareil.

## Installation

1. **Cloner le dépôt :**

   ```bash
   git clone https://github.com/votre-utilisateur/traps-monitor.git
   cd traps-monitor
   
2. **Ouvrir le projet dans Android Studio.**

3. **Configurer les dépendances :**
Le projet utilise Gradle avec un version catalog pour la gestion centralisée des versions des bibliothèques.

4. **Configurer le broker MQTT :**
Utilisez l'interface de configuration de l'application pour définir l'adresse du broker, le port et le nom du device. Ces paramètres sont sauvegardés dans les SharedPreferences et dans le fichier traps_config.txt dans le dossier Downloads.
Exécuter l'application sur un appareil ou un émulateur Android.

## Utilisation
Configuration initiale :
Au démarrage, l'interface affiche la configuration en lecture seule.
Appuyez sur Modifier pour activer le mode édition, modifiez les paramètres (adresse du broker, port, nom du device, etc.), puis appuyez sur Enregistrer pour sauvegarder les modifications.

**Connexion et Notifications :**
L'application se connecte automatiquement au broker MQTT et affiche un Toast "Connecté" (durant 3 secondes) lors de la première connexion.
Lorsqu'un message MQTT est reçu, une notification personnalisée s'affiche.
Un clic global sur la notification ou sur le bouton OK envoie un message "message reçu" sur le topic ack/[deviceId] et ferme la notification.

**Commandes MQTT :**
L'application répond aux commandes envoyées sur des topics prédéfinis pour fournir, par exemple, l'état de la batterie ou les informations Wi-Fi, ou pour initier une mise à jour de l'application.

**Compatibilité**
L'application est conçue pour fonctionner sur Android 4.4 (KitKat) minimum et a été testée jusqu'à Android 11.
