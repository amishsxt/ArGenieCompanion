package com.example.argeniecompanion.network.pubsub;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;

import com.example.argeniecompanion.app.ArGenieApp;
import com.example.argeniecompanion.logger.AppLogger;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttClientState;
import com.hivemq.client.mqtt.MqttWebSocketConfig;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5ConnAckException;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5WillPublish;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.ListIterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.reactivex.exceptions.UndeliverableException;


public class MqttWebRTC {
    private static final String TAG = MqttWebRTC.class.getSimpleName();
    public Mqtt5BlockingClient client = null;
    private static MqttWebRTC instance;
    private final ExecutorService connectExecutor = Executors.newSingleThreadExecutor();
    public static OnWebRTCEvent onWebRTCEvent;
    public static RemoteFunctionsCallback remoteFunctionsCallback = null;
    public static MessageCallbacks messageCallbacks = null;

    public interface RemoteFunctionsCallback {
        void onRemoteTapCallback(float tapX, float tapY, int model, int modelSize, String comment, String modelColor, Float rotation);

        void onRemoteDragCallback(ArrayList<JSONObject> taps, String modelColor) throws JSONException;

        void onRemoteClearReceived();
    }

    public interface MessageCallbacks {
        void addMessageCallback(String sessionId, String companyId, ByteBuffer byteBuffer);

    }

    public static MqttWebRTC getInstance() {
        return instance;
    }


    public interface OnWebRTCEvent {
        // this can be any type of method
        void onEvent(JSONObject jsonData);

        void onHangUpReceived();

    }

    public void initMqtt(Context context, String companyId, OnWebRTCEvent onWebRTCEvent, ArGenieApp.MqttConnectionCallback connectionCallback) {
        instance = this;
        if (!isNetworkAvailable()) {
            return;
        }
        AppLogger.i(TAG, "initMqtt(): mqtt init data :");
        AppLogger.d(TAG, "userId: " + ArGenieApp.userId);
        AppLogger.d(TAG, "companyId: " + companyId);
        AppLogger.d(TAG, "client Details:: " + Arrays.toString(getClientDetails(ArGenieApp.userId, "offline").toString().getBytes(UTF_8)));

        if(ArGenieApp.userId == null || companyId == null) return;
        Mqtt5WillPublish willPublishMessage = Mqtt5WillPublish.builder()
                .topic(String.format("supportgenie/%s/status/%s", companyId, ArGenieApp.userId))
                .payload(getClientDetails(ArGenieApp.userId, "offline").toString().getBytes(StandardCharsets.UTF_8))
                .retain(false).qos(MqttQos.AT_LEAST_ONCE)
                .build();

        client = MqttClient.builder()
                .useMqttVersion5()
                .identifier(ArGenieApp.getUserDeviceId())
                .serverHost(ArGenieApp.getInstance().getConfig().getMqttServerHost())
                .serverPort(ArGenieApp.getInstance().getConfig().getMqttServerPort()) // This doesn't work with port 8884.0
                .webSocketConfig(MqttWebSocketConfig.builder().serverPath("/mqtt").build()) // Use the server path configured on your broker
                .sslWithDefaultConfig()
                .automaticReconnectWithDefaultConfig()
                .simpleAuth()
                .username(ArGenieApp.getInstance().getConfig().getMqttUsername())
                .password(ArGenieApp.getInstance().getConfig().getMqttServerPassword().getBytes(UTF_8))
                .applySimpleAuth()
                .addConnectedListener(v -> {
                    AppLogger.d(TAG, "initMqtt(): mqtt connected");
                    ArGenieApp.getInstance().setHasMqttEverConnected(true);
                    if (ArGenieApp.userId != null) {
                        onClientConnected();
                    }
                    // --- TRIGGER SUCCESS CALLBACK ---
                    if (connectionCallback != null) {
                        connectionCallback.onMqttConnected();
                    }
                })
                .addDisconnectedListener(disconnectContext -> {
                    final Throwable cause = disconnectContext.getCause();
                    if (cause instanceof Mqtt5ConnAckException) {
                        AppLogger.d(TAG, "initMqtt(): disconnectListener(): Connect failed because of Negative CONNACK with code: " + cause.getMessage());
                    }
                    else {
                        AppLogger.d(TAG, "initMqtt(): disconnectListener(): MQTT connect failed because of: " + cause.getMessage());

                    }
                    if (connectionCallback != null) {
                        connectionCallback.onMqttConnectionFailure(cause.getMessage());
                    }
                })
                .willPublish(willPublishMessage)
                .buildBlocking();
        AppLogger.d(TAG, "initMqtt(): Mqtt Built");
        MqttWebRTC.onWebRTCEvent = onWebRTCEvent;
        // Run the blocking connect on a background thread to avoid ANR
        connectExecutor.execute(() -> {
            try {
                client.connectWith().send();
            } catch (Exception e) {
                AppLogger.e(TAG, "initMqtt(): connect failed", e);
                if (connectionCallback != null) {
                    connectionCallback.onMqttConnectionFailure(e.getMessage());
                }
            }
        });
    }

    public void onClientConnected(){
        if (client.getState() == MqttClientState.CONNECTED) {
            AppLogger.d(TAG, "onClientConnected(): client connected starting");
            client.toAsync().publishWith()
                    .topic(String.format("supportgenie/%s/status/%s", ArGenieApp.companyId, ArGenieApp.userId))
                    .payload(getClientDetails(ArGenieApp.userId, "online")
                            .toString().getBytes(UTF_8)).retain(false).qos(MqttQos.AT_LEAST_ONCE).send();
            AppLogger.d(TAG, "onClientConnected(): client connected completed");
        } else {
            AppLogger.d(TAG, "onClientConnected(): client not connected");
        }
    }

    public JSONObject getClientDetails(String userId, String status){
        String clientDetailsString = String.format("%s %s %s", "Android", Build.VERSION.SDK_INT, Build.MODEL);
        JSONObject clientDetails = new JSONObject();
        try {
            clientDetails.put("userId", userId);
            clientDetails.put("userType", ArGenieApp.getUserType());
            clientDetails.put("clientId", ArGenieApp.getUserDeviceId());
            clientDetails.put("clientType", "androidApp");
            clientDetails.put("clientDetails", clientDetailsString);
            clientDetails.put("clientVersion", ArGenieApp.VERSION_CODE);
            clientDetails.put("status", status);
            AppLogger.d(TAG, "getClientDetails(): " + clientDetails);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return clientDetails;
    }

    public void send(JSONObject jsonData) {
        String strPayload = jsonData.toString();
        MqttPublishers.publishWebRTCMessage(strPayload);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) ArGenieApp.getAppContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    public synchronized void removeAllListeners() {
        if (client==null || client.getState() != MqttClientState.CONNECTED)
            return;
        AppLogger.d(TAG,"removeAllListeners(): UnSubscribing topics :"+MqttListenerTopics.mqttSubscriptionsList);
        ListIterator<String> mqttSubsListIterator = MqttListenerTopics.mqttSubscriptionsList.listIterator();
        while (mqttSubsListIterator.hasNext()){
            try {
                client.unsubscribeWith().topicFilter(mqttSubsListIterator.next()).send();
                mqttSubsListIterator.remove();
            }catch(ConcurrentModificationException | UndeliverableException e ){
                AppLogger.e(TAG,e.toString());
            }
        }
        AppLogger.d(TAG,"removeAllListeners(): Updated Subscribed topics list:"+MqttListenerTopics.mqttSubscriptionsList);
    }

    public void initializeAllCommonListeners(){
        //remove any listener that is already subscribed(from old session) before subscribing new listeners
        removeAllListeners();
        MqttSubscriptions.listenSessionChatMessage();
    }
}