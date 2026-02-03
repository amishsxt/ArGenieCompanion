package com.example.argeniecompanion.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.argeniecompanion.logger.AppLogger;
import com.example.argeniecompanion.network.AppNetworkManager;
import com.example.argeniecompanion.network.pubsub.MqttPublishers;
import com.example.argeniecompanion.network.pubsub.MqttWebRTC;
import com.hivemq.client.mqtt.MqttClientState;

/**
 * Manages MQTT connection lifecycle
 * Handles connection, disconnection, and state management
 */
public class MqttManager {

    private static final String TAG = "MqttManager";

    private final Context context;
    private final Object mqttLock = new Object();

    private MqttWebRTC mqttWebRTC;
    private boolean hasMqttEverConnected = false;

    public MqttManager(Context context) {
        this.context = context.getApplicationContext();
    }

    // =============================================================================================
    // Connection Management
    // =============================================================================================

    /**
     * Start MQTT if needed (network available, user logged in, not already connected)
     */
    public void startIfNeeded() {
        if (!hasMqttEverConnected &&
                AppNetworkManager.getInstance(context).isConnectedToInternet() &&
                ArGenieApp.userId != null &&
                ArGenieApp.companyId != null) {

            AppLogger.d(TAG, "Starting MQTT (conditions met)");
            start(null);
        } else {
            AppLogger.d(TAG, "MQTT start not needed - " +
                    "connected: " + hasMqttEverConnected +
                    ", internet: " + AppNetworkManager.getInstance(context).isConnectedToInternet() +
                    ", userId: " + (ArGenieApp.userId != null) +
                    ", companyId: " + (ArGenieApp.companyId != null));
        }
    }

    /**
     * Start MQTT connection with optional callback
     */
    public void start(ArGenieApp.MqttConnectionCallback callback) {
        synchronized (mqttLock) {
            // Check if already connected
            if (isConnected()) {
                AppLogger.d(TAG, "MQTT already connected");
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(callback::onMqttConnected);
                }
                return;
            }

            // Check if connecting
            if (isConnecting()) {
                AppLogger.d(TAG, "MQTT already connecting, skipping duplicate request");
                return;
            }

            try {
                // Create or reuse MQTT instance
                if (mqttWebRTC == null) {
                    mqttWebRTC = new MqttWebRTC();
                    AppLogger.d(TAG, "Created new MqttWebRTC instance");
                } else {
                    AppLogger.d(TAG, "Reusing existing MqttWebRTC instance");
                }

                // Initialize MQTT
                mqttWebRTC.initMqtt(
                        context,
                        ArGenieApp.companyId,
                        ArGenieApp.getInstance(),
                        callback
                );

                // Update global reference for publishers
                MqttPublishers.client = mqttWebRTC.client;

                AppLogger.d(TAG, "MQTT initialization started");

            } catch (Exception e) {
                AppLogger.e(TAG, "Error starting MQTT", e);

                // Clean up on failure
                if (mqttWebRTC != null) {
                    mqttWebRTC.client = null;
                    mqttWebRTC = null;
                }

                if (callback != null) {
                    callback.onMqttConnectionFailure("Failed to start MQTT: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Disconnect MQTT
     */
    public boolean disconnect() {
        synchronized (mqttLock) {
            try {
                if (mqttWebRTC != null &&
                        mqttWebRTC.client != null &&
                        mqttWebRTC.client.getState() == MqttClientState.CONNECTED) {

                    AppLogger.d(TAG, "Disconnecting MQTT...");
                    mqttWebRTC.client.disconnect();
                    mqttWebRTC.client = null;
                    mqttWebRTC = null;
                    hasMqttEverConnected = false;

                    AppLogger.d(TAG, "MQTT disconnected successfully");
                    return true;
                } else {
                    AppLogger.d(TAG, "MQTT already disconnected or not initialized");
                    return false;
                }
            } catch (Exception e) {
                AppLogger.e(TAG, "MQTT disconnect error", e);

                // Force cleanup
                mqttWebRTC = null;
                hasMqttEverConnected = false;
                return true;
            }
        }
    }

    /**
     * Restart MQTT connection
     */
    public void restart() {
        AppLogger.d(TAG, "MQTT restart requested");

        // Delay helps prevent rapid reconnection loops
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            disconnect();
            startIfNeeded();
        }, 500);
    }

    // =============================================================================================
    // State Queries
    // =============================================================================================

    /**
     * Check if MQTT is connected
     */
    public boolean isConnected() {
        return mqttWebRTC != null &&
                mqttWebRTC.client != null &&
                mqttWebRTC.client.getState() == MqttClientState.CONNECTED;
    }

    /**
     * Check if MQTT is currently connecting
     */
    public boolean isConnecting() {
        return mqttWebRTC != null &&
                mqttWebRTC.client != null &&
                mqttWebRTC.client.getState() == MqttClientState.CONNECTING;
    }

    /**
     * Get current MQTT state
     */
    public MqttClientState getState() {
        if (mqttWebRTC != null && mqttWebRTC.client != null) {
            return mqttWebRTC.client.getState();
        }
        return null;
    }

    // =============================================================================================
    // Getters/Setters
    // =============================================================================================

    public MqttWebRTC getMqttWebRTC() {
        return mqttWebRTC;
    }

    public boolean hasMqttEverConnected() {
        return hasMqttEverConnected;
    }

    public void setHasMqttEverConnected(boolean connected) {
        this.hasMqttEverConnected = connected;
        if (connected) {
            AppLogger.d(TAG, "MQTT marked as having connected");
        }
    }
}
