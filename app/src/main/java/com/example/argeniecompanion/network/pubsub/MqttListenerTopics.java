package com.example.argeniecompanion.network.pubsub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MqttListenerTopics {
    public static List<String> mqttSubscriptionsList = Collections.synchronizedList(new ArrayList<>());

    //Ar topics
    public static String remoteTapTopic = "supportgenie/%s/session/video/cordinates/%s";
    public static String remoteDragTopic = "supportgenie/%s/session/video/drag_coordinates/%s";
    public static String remoteRemoveMarkersTopic = "supportgenie/%s/session/video/remove_markers/%s";
    public static String remoteAnnotationColorTopic = "supportgenie/%s/session/video/annotationColor/%s";

    //Session topics
    public static String sessionParticipantAddedTopic = "supportgenie/%s/session/participant/added/%s";
    public static String sessionParticipantUpdatedTopic = "supportgenie/%s/session/participant/updated/%s";
    public static String sessionChatMessageTopic = "supportgenie/%s/session/message/%s";

    //Common topics
    public static String hangupTopic = "supportgenie/%s/video/hangup/%s";
    public static String videoBusyTopic = "supportgenie/%s/video/busy/%s";
    public static String recordingTopic = "supportgenie/%s/recording/started/%s";
    public static String userLogoutTopic = "supportgenie/%s/logout/%s";
    public static String webRTCTopic = "supportgenie/%s/webrtc";
    public static String screenShareTopic = "supportgenie/%s/video/screen-share/started/%s";


}
