package com.example.argeniecompanion.network.pubsub;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.example.argeniecompanion.app.ArGenieApp;
import com.example.argeniecompanion.logger.AppLogger;
import com.hivemq.client.mqtt.MqttClientState;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public class MqttPublishers {
    private static final String TAG = MqttPublishers.class.getSimpleName();
    public static Mqtt5BlockingClient client;

    public static void publishHangUpMqttMessage(String companyId, String sessionId, String userId) {
        AppLogger.d("MqttWebRTC", "publishHangUpMqttMessage(): In on hangup publish ");
        String topic = String.format("supportgenie/" + companyId + "/video/hangup/" + sessionId);
        String strPayload = null;
        try {
            JSONObject payload = new JSONObject().put("type", "bye");
            payload.put("userId", userId);
            strPayload = payload.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (client.getState() == MqttClientState.CONNECTED) {
            client.publishWith()
                    .topic(topic)
                    .payload(strPayload.getBytes(UTF_8))
                    .send();
            AppLogger.d("MqttWebRTC", "publishHangUpMqttMessage(): Sent hangup mqtt message: " + topic);
        }
    }

    public static void publishClearRemoteMarkers(String companyId, String sessionId) {
        AppLogger.d(TAG, "publishClearRemoteMarkers(): publishClearRemoteMarkers for " + sessionId);
        String topic = String.format("supportgenie/%s/session/video/remove_markers/%s", companyId, sessionId);
        try {
            JSONObject payload = new JSONObject();
            payload.put("clearMarkers", true);
            if (client.getState() == MqttClientState.CONNECTED) {
                client.publishWith()
                        .topic(topic)
                        .payload(payload.toString().getBytes(UTF_8))
                        .send();
                AppLogger.d(TAG, "publishClearRemoteMarkers(): Sent client ready message: " + topic);
            }
        } catch (JSONException e) {
            AppLogger.e(TAG, "publishClearRemoteMarkers(): Failed: publishClearRemoteMarkers: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void publishRemoteTap(String companyId, String sessionId, Float tapX, Float tapY, Integer selectedShape, int selectedSize, Integer selectedThickness, String selectedColor, Float rotation) {
        AppLogger.d(TAG, "publishRemoteTap(): publishRemoteTap for " + sessionId);
        String topic = String.format("supportgenie/%s/session/video/cordinates/%s", companyId, sessionId);
        try {
            JSONObject payload = new JSONObject();
            payload.put("xAxis", tapX);
            payload.put("yAxis", tapY);
            payload.put("selectedShape", selectedShape);
            payload.put("selectedSize", selectedSize);
            payload.put("selectedThickness", selectedThickness);
            payload.put("selectedColor", selectedColor);
            payload.put("rotation", rotation);
            if (client.getState() == MqttClientState.CONNECTED) {
                client.publishWith()
                        .topic(topic)
                        .payload(payload.toString().getBytes(UTF_8))
                        .send();
            }
        } catch (JSONException e) {
            AppLogger.e(TAG, "publishRemoteTap(): catch{}: Failed: publishRemoteTap: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void publishRemoteTap(String companyId, String sessionId, Float tapX, Float tapY, Integer selectedShape, int selectedSize, Integer selectedThickness, String text, String color, Float rotation) {
        AppLogger.d(TAG, "publishRemoteTap(): publishRemoteTap for " + sessionId);
        String topic = String.format("supportgenie/%s/session/video/cordinates/%s", companyId, sessionId);
        try {
            JSONObject payload = new JSONObject();
            payload.put("xAxis", tapX);
            payload.put("yAxis", tapY);
            payload.put("selectedShape", selectedShape);
            payload.put("selectedSize", selectedSize);
            payload.put("selectedThickness", selectedThickness);
            payload.put("comment", text);
            payload.put("selectedColor", color);
            payload.put("rotation", rotation);
            if (client.getState() == MqttClientState.CONNECTED) {
                client.publishWith()
                        .topic(topic)
                        .payload(payload.toString().getBytes(UTF_8))
                        .send();
            }
        } catch (JSONException e) {
            AppLogger.e(TAG, "publishRemoteTap(): catch{}: Failed: publishRemoteTap: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void publishRemoteDrag(String companyId, String sessionId, JSONArray taps, Integer selectedThickness, String color) {
        AppLogger.d(TAG, "publishRemoteDrag(): publishRemoteDrag for " + sessionId);
        String topic = String.format("supportgenie/%s/session/video/drag_coordinates/%s", companyId, sessionId);
        try {
            JSONObject payload = new JSONObject();
            payload.put("taps", taps);
            payload.put("selectedThickness", selectedThickness);
            payload.put("selectedColor", color);
            if (client.getState() == MqttClientState.CONNECTED) {
                client.publishWith()
                        .topic(topic)
                        .payload(payload.toString().getBytes(UTF_8))
                        .send();
            }
        } catch (JSONException e) {
            AppLogger.e(TAG, "publishRemoteDrag(): catch{}: Failed: publishRemoteDrag: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void publishVideo(String companyId, String videoSessionId, JSONObject payload){
        String publishVideoTopic = String.format("supportgenie/%s/session/video/publish/%s", companyId, videoSessionId);
        if (client.getState() == MqttClientState.CONNECTED) {
            client.publishWith()
                    .topic(publishVideoTopic)
                    .payload(payload.toString().getBytes(UTF_8))
                    .send();
        }
    }

    // ROOM RELATED P2P METHODS
    public static void publishJoinRoom(String companyId, String videoSessionId, String userId, Boolean isInitiator){
        String joinRoomTopic = String.format("supportgenie/%s/session/video/join/%s", companyId, videoSessionId);
        try {
            JSONObject payload = new JSONObject();
            payload.put("userId", userId);
            payload.put("isInitiator", isInitiator);
            if (client.getState() == MqttClientState.CONNECTED) {
                client.publishWith()
                        .topic(joinRoomTopic)
                        .payload(payload.toString().getBytes(UTF_8))
                        .send();
                AppLogger.d(TAG, "publishJoinRoom(): " + payload);
            }
        } catch (JSONException e) {
            AppLogger.e(TAG, "publishJoinRoom(): catch(): Failed: publishJoinRoom: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void publishColorChange(String companyId, String sessionId, String userId, String name, String color) {
        String topic = String.format("supportgenie/" + companyId + "/session/video/annotationColor/" + sessionId);
        try {
            JSONObject payload = new JSONObject();
            payload.put("userId", userId);
            payload.put("name", name);
            payload.put("color", color);
            if (client.getState() == MqttClientState.CONNECTED) {
                client.publishWith()
                        .topic(topic)
                        .payload(payload.toString().getBytes(UTF_8))
                        .send();
            }
        } catch (JSONException e) {
            AppLogger.e(TAG, "publishRemoteDrag(): catch(): failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void publishWebRTCMessage(String strPayload) {
        try {
            if (client.getState() == MqttClientState.CONNECTED) {
                client.publishWith()
                        .topic(String.format(MqttListenerTopics.webRTCTopic, ArGenieApp.hostCompanyId))
                        .payload(strPayload.getBytes(StandardCharsets.UTF_8))
                        .send();
            }
        } catch (Exception e) {
            AppLogger.e(TAG, "publishWebRTCMessage(): catch{}: Failed:  " + e.getMessage());
            e.printStackTrace();
        }

    }
}
