package com.example.argeniecompanion.app;

import android.app.Application;
import android.content.Context;

import com.example.argeniecompanion.logger.AppLogger;
import com.example.argeniecompanion.network.ApiRequestManager;
import com.example.argeniecompanion.network.callbacks.ApiAsyncResponseCallback;
import com.example.argeniecompanion.network.pubsub.MqttWebRTC;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

import cz.msebera.android.httpclient.entity.StringEntity;

/**
 * Application class for ArGenie Companion
 * Manages app-wide state and MQTT connectivity
 */
public class ArGenieApp extends Application implements MqttWebRTC.OnWebRTCEvent {

    private static final String TAG = ArGenieApp.class.getSimpleName();
    public static final Integer MIN_VERSION_CODE_REQUIRED = 1;
    public static final Integer VERSION_CODE = 1;

    // Singleton instance
    private static ArGenieApp instance;

    // Configuration
    private Config config;

    // MQTT manager
    private MqttManager mqttManager;

    // Callbacks
    private MqttCallback mqttCallback;

    // =============================================================================================
    // Static fields — single source of truth for all session state
    // =============================================================================================

    // User identity
    public static String userId;
    public static String userName = "Guest";
    public static String userEmailId;
    public static String deviceId;

    // Company info
    public static String companyId;
    public static String companyName;
    public static String hostCompanyId;

    // Session info
    public static String currentMeetingId;
    public static String videoSessionId;
    public static String chatSessionId;

    // Auth tokens
    public static String accessToken;
    public static String refreshToken;
    public static String idToken;

    // User type
    public static boolean isGuestUser = true;

    // Plan and agent info
    public static String planId;
    public static String agentId;
    public static String ticketId;

    // Configuration
    public static String apiUrl;

    // =============================================================================================
    // Lifecycle
    // =============================================================================================

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        config = new Config();
        mqttManager = new MqttManager(this);

        AppLogger.d(TAG, "ArGenieApp initialized");
    }

    public static ArGenieApp getInstance() {
        return instance;
    }

    public static Context getAppContext() {
        return instance != null ? instance.getApplicationContext() : null;
    }

    // =============================================================================================
    // Configuration
    // =============================================================================================

    public Config getConfig() {
        return config;
    }

    // =============================================================================================
    // Utility Methods
    // =============================================================================================

    /**
     * Get device ID (generates UUID if not set)
     */
    public static String getUserDeviceId() {
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString();
            AppLogger.d(TAG, "Device ID generated: " + deviceId);
        }
        return deviceId;
    }

    /**
     * Get user type (customer/agent)
     */
    public static String getUserType() {
        return isGuestUser ? "customer" : "agent";
    }

    /**
     * Clear session data (on logout or session end)
     */
    public static void clearSession() {
        AppLogger.d(TAG, "Clearing session data");
        currentMeetingId = null;
        videoSessionId = null;
        chatSessionId = null;
        clearAuthTokens();
        // Keep userId, deviceId, companyId, and user name — they persist for the app lifetime
    }

    /**
     * Check if user has an active session
     */
    public static boolean hasActiveSession() {
        return userId != null && accessToken != null;
    }

    /**
     * Check if user is in a meeting
     */
    public static boolean isInMeeting() {
        return currentMeetingId != null;
    }

    /**
     * Set all auth tokens at once
     */
    public static void setAuthTokens(String accessTokenValue, String refreshTokenValue, String idTokenValue) {
        accessToken = accessTokenValue;
        refreshToken = refreshTokenValue;
        idToken = idTokenValue;
        AppLogger.d(TAG, "Auth tokens updated");
    }

    /**
     * Clear all auth tokens
     */
    public static void clearAuthTokens() {
        accessToken = null;
        refreshToken = null;
        idToken = null;
        AppLogger.d(TAG, "Auth tokens cleared");
    }

    /**
     * Set has MQTT ever connected (delegates to MqttManager)
     */
    public void setHasMqttEverConnected(boolean connected) {
        if (mqttManager != null) {
            mqttManager.setHasMqttEverConnected(connected);
        }
    }

    /**
     * Generate anonymous user ID for guest users
     */
    public void generateUserId(ContinueJoinWithLinkId callback) {
        AppLogger.d(TAG, "generateUserId: current userId " + userId);

        if (hostCompanyId != null) {
            companyId = hostCompanyId;
        }

        // Reuse existing userId if already generated
        if (userId != null) {
            AppLogger.d(TAG, "generateUserId: reusing existing userId: " + userId);
            callback.onSuccess();
            return;
        }

        createAnonymousUser(companyId, new CreateP2PSessionCallbacks() {
            @Override
            public void onSuccess(JSONObject responseBody) {
                try {
                    String newUserId = responseBody.getString("userId");
                    AppLogger.d(TAG, "generateUserId: UserID fetched: " + newUserId);
                    userId = newUserId;
                    callback.onSuccess();
                } catch (JSONException e) {
                    AppLogger.e(TAG, "Failed to parse userId from response", e);
                    callback.onFailure("Failed to generate user ID");
                }
            }

            @Override
            public void onFailure(String error) {
                AppLogger.e(TAG, "generateUserId failed: " + error);
                callback.onFailure(error);
            }
        });
    }

    /**
     * Create anonymous user via API
     */
    private void createAnonymousUser(String companyId, CreateP2PSessionCallbacks callback) {
        try {
            JSONObject jsonParams = new JSONObject();
            jsonParams.put("companyId", companyId);
            jsonParams.put("name", userName);

            StringEntity entity = new StringEntity(jsonParams.toString());
            String url = config.getApiUrl() + "/user/anonymous/new";

            ApiRequestManager.makeAsyncPostRequest(url, entity, new ApiAsyncResponseCallback() {
                @Override
                public void OnStart() { }

                @Override
                public void OnSuccess(JSONObject response) {
                    callback.onSuccess(response);
                }

                @Override
                public void OnFailure(JSONObject responseError, Throwable error) {
                    String detail = "The service is temporarily unavailable. Please try again later.";
                    try {
                        if (responseError != null) {
                            detail = responseError.getString("detail");
                        }
                    } catch (JSONException e) {
                        AppLogger.e(TAG, "createAnonymousUser OnFailure", e);
                    }
                    callback.onFailure(detail);
                }
            });
        } catch (Exception e) {
            AppLogger.e(TAG, "createAnonymousUser error", e);
            callback.onFailure("Failed to create anonymous user");
        }
    }

    // =============================================================================================
    // MQTT Management
    // =============================================================================================

    public MqttManager getMqttManager() {
        return mqttManager;
    }

    /**
     * Start MQTT if conditions are met
     */
    public void startMqttIfNeeded() {
        mqttManager.startIfNeeded();
    }

    /**
     * Start MQTT with callback
     */
    public void startMqtt(MqttConnectionCallback callback) {
        mqttManager.start(callback);
    }

    /**
     * Disconnect MQTT
     */
    public boolean disconnectMqtt() {
        return mqttManager.disconnect();
    }

    /**
     * Restart MQTT connection
     */
    public void restartMqtt() {
        mqttManager.restart();
    }

    /**
     * Get MQTT client for publishing
     */
    public static MqttWebRTC getMqttWebRTC() {
        return instance != null ? instance.mqttManager.getMqttWebRTC() : null;
    }

    // =============================================================================================
    // MQTT Event Handlers (Implementation of MqttWebRTC.OnWebRTCEvent)
    // =============================================================================================

    @Override
    public void onEvent(JSONObject jsonObject) {
        AppLogger.d(TAG, "MQTT onEvent userId: " + userId + ", data: " + jsonObject.toString());

        if (mqttCallback == null) {
            AppLogger.e(TAG, "mqttCallback is null");
            return;
        }

        try {
            // Check if current user is a participant
            boolean isParticipant = false;
            JSONArray participants = jsonObject.getJSONArray("participants");

            for (int i = 0; i < participants.length(); i++) {
                String participant = participants.getString(i);
                if (participant.equals(userId)) {
                    isParticipant = true;
                    break;
                }
            }

            // Only process if user is participant and event is not from self
            String remoteUserId = jsonObject.getString("userId");
            if (!remoteUserId.equals(userId) && isParticipant) {
                mqttCallback.onMqttEvent(jsonObject);
            }
        } catch (JSONException e) {
            AppLogger.e(TAG, "Failed to process MQTT event", e);
        }
    }

    @Override
    public void onHangUpReceived() {
        if (mqttCallback != null) {
            mqttCallback.onHangupReceived();
        } else {
            AppLogger.w(TAG, "onHangUpReceived but mqttCallback is null");
        }
    }

    // =============================================================================================
    // Callback Management
    // =============================================================================================

    public void setMqttCallback(MqttCallback callback) {
        this.mqttCallback = callback;
    }

    public MqttCallback getMqttCallback() {
        return mqttCallback;
    }

    // =============================================================================================
    // Interfaces
    // =============================================================================================

    public interface MqttCallback {
        void onMqttEvent(JSONObject jsonObject);
        void onHangupReceived();
    }

    public interface MqttConnectionCallback {
        void onMqttConnected();
        void onMqttConnectionFailure(String error);
    }

    public interface ContinueJoinWithLinkId {
        void onSuccess();
        void onFailure(String error);
    }

    public interface CreateP2PSessionCallbacks {
        void onSuccess(JSONObject responseBody);
        void onFailure(String error);
    }
}
