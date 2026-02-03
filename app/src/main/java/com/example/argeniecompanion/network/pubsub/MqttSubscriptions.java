package com.example.argeniecompanion.network.pubsub;

import static java.nio.charset.StandardCharsets.UTF_8;

import androidx.appcompat.app.AlertDialog;

import com.example.argeniecompanion.app.ArGenieApp;
import com.example.argeniecompanion.logger.AppLogger;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAckReasonCode;

public class MqttSubscriptions {

    public static final String TAG = MqttSubscriptions.class.getSimpleName();
    public static AlertDialog alertDialog;

    //AR topics
    public static void listenSessionChatMessage(){
        Mqtt5BlockingClient client = ArGenieApp.getInstance().getMqttWebRTC().client;
        String topic = String.format(MqttListenerTopics.sessionChatMessageTopic, ArGenieApp.hostCompanyId, ArGenieApp.chatSessionId);
        AppLogger.i(TAG, "listenSessionChatMessage topic: "+ topic);
        client.toAsync().subscribeWith()
                .topicFilter(topic)
                .callback(publish -> {
                    if (publish.getPayload().isPresent()) {
                        AppLogger.d(TAG, "Mqtt Received message tag: " + publish.getTopic() + " -> " + UTF_8.decode(publish.getPayload().get()));
                        try {
                            MqttWebRTC.messageCallbacks.addMessageCallback(ArGenieApp.chatSessionId, ArGenieApp.hostCompanyId, publish.getPayload().get());
                        } catch (Exception e) {
                            AppLogger.e(TAG, "addMessageListener interface file " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }).send().whenComplete(((mqtt5SubAck, throwable) -> {
                    AppLogger.d(TAG,"MQTT subscribed : "+ topic +mqtt5SubAck);
                    if (mqtt5SubAck.getReasonCodes().get(0).getCode() == Mqtt5SubAckReasonCode.GRANTED_QOS_2.getCode())
                        MqttListenerTopics.mqttSubscriptionsList.add(topic);
                    if (throwable != null) {
                        AppLogger.e(TAG, throwable.getMessage());
                    } else {
                        // Send ack if required.
                    }
                }));
    }
}
