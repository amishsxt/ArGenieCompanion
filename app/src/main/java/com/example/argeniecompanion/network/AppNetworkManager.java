package com.example.argeniecompanion.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

import com.example.argeniecompanion.app.ArGenieApp;
import com.example.argeniecompanion.logger.AppLogger;
import com.hivemq.client.mqtt.MqttClientState;

import io.reactivex.annotations.NonNull;

/**
 * Manages network connectivity status for the application.
 *
 * This class is a thread-safe singleton that monitors network changes using the modern
 * ConnectivityManager.NetworkCallback API. It provides reliable status updates and ensures
 * services dependent on internet connectivity, like MQTT, are managed correctly when the
 * network becomes available or is lost.
 */
public class AppNetworkManager {

    private static final String TAG = "AppNetworkManager";

    // Use volatile to ensure that assignment to the instance variable is published correctly to other threads.
    private static volatile AppNetworkManager instance;

    private final ConnectivityManager connectivityManager;
    private final ArGenieApp application;
    private ConnectivityManager.NetworkCallback networkCallback;

    /**
     * Private constructor to prevent instantiation from outside the class.
     *
     * @param context The application context.
     */
    private AppNetworkManager(Context context) {
        // Use application context to avoid memory leaks associated with Activity contexts.
        this.application = (ArGenieApp) context.getApplicationContext();
        this.connectivityManager = (ConnectivityManager) this.application.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    /**
     * Gets the singleton instance of the AppNetworkManager.
     *
     * @param context The context, preferably the application context.
     * @return The single instance of AppNetworkManager.
     */
    public static AppNetworkManager getInstance(Context context) {
        // Double-checked locking for thread-safe singleton initialization.
        if (instance == null) {
            synchronized (AppNetworkManager.class) {
                if (instance == null) {
                    instance = new AppNetworkManager(context);
                }
            }
        }
        return instance;
    }

    /**
     * Initializes the network manager by registering the network callback.
     * This should be called once when the application starts.
     */
    public void init() {
        startNetworkCallbacks();
    }

    /**
     * Provides an instantaneous, synchronous check for a validated internet connection.
     * This is the modern and reliable replacement for pinging an external server.
     *
     * @return true if the device has a network connection with validated internet access, false otherwise.
     */
    public boolean isConnectedToInternet() {
        if (connectivityManager == null) {
            return false;
        }
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            return false;
        }
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    /**
     * Checks if the MQTT client is currently in a connected state.
     *
     * @return true if the MQTT client is connected, false otherwise.
     */
    public boolean isMqttConnected() {
        // This check is now simplified, as it only needs to report the client's own state.
        if (application.getMqttWebRTC() != null && application.getMqttWebRTC().client != null) {
            return application.getMqttWebRTC().client.getState() == MqttClientState.CONNECTED;
        }
        return false;
    }

    /**
     * Registers a NetworkCallback to listen for network state changes.
     * This is the core of the modern network monitoring approach.
     */
    private void startNetworkCallbacks() {
        // Build a request to look for networks with internet capability.
        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            /**
             * Called when a network with internet capability is available, but not necessarily validated.
             * The most reliable trigger is onCapabilitiesChanged when NET_CAPABILITY_VALIDATED is present.
             */
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                AppLogger.i(TAG, "Network available: " + network + ". Checking for validated internet.");
                // Check if the network is already validated.
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
                if (capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                    handleNetworkConnected();
                }
            }

            /**
             * Called when a network connection is lost.
             */
            @Override
            public void onLost(@NonNull Network network) {
                super.onLost(network);
                AppLogger.e(TAG, "Network connection lost: " + network);
            }

            /**
             * Called when the capabilities of a network change. This is the most reliable way
             * to detect when a network gains actual, validated internet access.
             */
            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities);
                final boolean hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                AppLogger.i(TAG, "Network capabilities changed for " + network + ". Has validated internet: " + hasInternet);
                if (hasInternet) {
                    handleNetworkConnected();
                }
            }
        };

        // Register the callback with the system.
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
    }

    /**
     * Centralized handler for when a validated network connection is confirmed.
     * Notifies the app and ensures the MQTT client is started if not already connected.
     */
    private void handleNetworkConnected() {
        AppLogger.i(TAG, "Validated internet connection is available.");

        // ✅ Let HiveMQ handle reconnect, don't restart MQTT here
        AppLogger.i(TAG, "Relying on HiveMQ auto-reconnect. No manual start.");
    }

    /**
     * Unregisters the network callback. Should be called when the app is shutting down
     * to prevent leaks.
     */
    public void cleanup() {
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
                AppLogger.i(TAG, "Network callback unregistered.");
            } catch (Exception e) {
                AppLogger.e(TAG, "Error unregistering network callback", e);
            }
        }
    }
}
