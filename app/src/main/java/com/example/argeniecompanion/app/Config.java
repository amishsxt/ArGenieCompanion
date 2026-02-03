package com.example.argeniecompanion.app;

public class Config {
    private enum Environment{
        PRODUCTION,
        PRE_PRODUCTION,
        DEV
    }
    private String apiUrl;
    private String graphql;
    private String mqttServerHost;
    private int mqttServerPort;
    private String mqttServerPassword;
    private String mqttUsername;
    private String deeplinkUrl = null;
    private String portalUrl = null;
    private String flagsmithApiKey;
    private String whiteBoardKey;
    private String whiteBoardUrl = "https://whiteboard-client.staging.argenie.ai/#room=";
    private boolean isLoggingEnabled;
    private String livekitUrl;
    private String signUpUrl;
    private String keycloakClientId;
    private String keycloakClientSecret;
    private String keycloakIssuer;
    private String keycloakLogoutIssuer;
    private static final String flagsmithApiUrl = "https://flagsmith.argenie.ai/api/v1/";

    //change the app environment according to the need
    private static final Environment appEnvironment = Environment.DEV;

    public Config() {
        switch(appEnvironment){
            case PRODUCTION:{
                apiUrl = "https://webservice.argenie.ai/v3";
                graphql = "https://graphql.argenie.ai/graphql/";
                mqttServerHost = "broker.argenie.ai";
                mqttServerPassword = "Kingfisher94108";
                mqttUsername = "argenie";
                mqttServerPort = 443;
                deeplinkUrl = "https://argenie.app.link";
                portalUrl = "https://portal.argenie.ai/";
                flagsmithApiKey = "gkpTJVUhwKMPSVpWnM34iz";
                whiteBoardKey = "FTGPQEWPROzKV1wF1fZFGQ";
                isLoggingEnabled = false;
                livekitUrl = "wss://argenie-remote-assist-8srg0z3h.livekit.cloud";
                whiteBoardUrl = "https://whiteboard.client.argenie.ai/#room=";
                signUpUrl = "https://www.argenie.ai/products/remote-assist#pricing";
                keycloakClientId = "ar-genie";
                keycloakClientSecret = "NqRwi7j6In9DdC4w9drKqFousCVWSW6c";
                keycloakIssuer = "https://auth.argenie.ai/realms/ar-genie";
                keycloakLogoutIssuer = "https://auth.argenie.ai/realms/ar-genie/protocol/openid-connect/logout";
                break;
            }
            case PRE_PRODUCTION:{
                apiUrl = "https://webservice.pre-prod.argenie.ai/v3";
                graphql = "https://graphql.pre-prod.argenie.ai/graphql/";
                mqttServerHost = "broker.argenie.ai";
                mqttServerPassword = "Kingfisher94108";
                mqttUsername = "argenie";
                mqttServerPort = 443;
                deeplinkUrl = "https://argenie.app.link/pre-prod";
                portalUrl = "https://portal.pre-prod.argenie.ai/";
                flagsmithApiKey = "Z9Jdq4v9gTpdrLPbJp7oHY";
                whiteBoardKey = "FTGPQEWPROzKV1wF1fZFGQ";
                isLoggingEnabled = true;
                livekitUrl = "wss://argenie-remote-assist-8srg0z3h.livekit.cloud";
                whiteBoardUrl = "https://whiteboard.client.pre-prod.argenie.ai/#room=";
                signUpUrl = "https://argenienew.webflow.io/products/remote-assist#pricing";
                keycloakClientId = "ar-genie";
                keycloakClientSecret = "3492pgdhbxVjrQNuSNyFMnjhcGfTvU34";
                keycloakIssuer = "https://keycloak.dev.argenie.ai/realms/ar-genie-preprod";
                keycloakLogoutIssuer = "https://keycloak.dev.argenie.ai/realms/ar-genie-preprod/protocol/openid-connect/logout";
                break;
            }
            case DEV:{
                apiUrl = "https://webservice.dev.argenie.ai/v3";
                graphql = "https://graphql.dev.argenie.ai/graphql/";
                mqttServerHost = "broker.argenie.ai";
                mqttServerPassword = "Kingfisher94108";
                mqttUsername = "argenie";
                mqttServerPort = 443;
                deeplinkUrl = "https://argenie.app.link/dev";
                portalUrl = "https://portal.dev.argenie.ai/";
                flagsmithApiKey = "JDEAeTANfK8fRnisQLUBe8";
                whiteBoardKey = "FTGPQEWPROzKV1wF1fZFGQ";
                isLoggingEnabled = true;
                livekitUrl = "wss://argenie-remote-assist-8srg0z3h.livekit.cloud";
                whiteBoardUrl = "https://whiteboard.client.staging.argenie.ai/#room=";
                signUpUrl = "https://argenienew.webflow.io/products/remote-assist#pricing";
                keycloakClientId = "ar-genie";
                keycloakClientSecret = "7wMGC6MueobyKoGFUNYsNMKR3q39iE3u";
                keycloakIssuer = "https://keycloak.dev.argenie.ai/realms/ar-genie";
                keycloakLogoutIssuer = "https://keycloak.dev.argenie.ai/realms/ar-genie/protocol/openid-connect/logout";
                break;
            }
        }
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public String getKeycloakClientId() {
        return keycloakClientId;
    }

    public String getKeycloakClientSecret() {
        return keycloakClientSecret;
    }

    public String getKeycloakIssuer() {
        return keycloakIssuer;
    }

    public String getGraphqlUrl() {
        return graphql;
    }

    public String getMqttServerHost() {
        return mqttServerHost;
    }

    public String getMqttServerPassword() {
        return mqttServerPassword;
    }

    public String getMqttUsername() {
        return mqttUsername;
    }

    public String getDeeplinkUrl() {
        return deeplinkUrl;
    }

    public String getPortalUrl(){
        return portalUrl;
    }

    public String getWhiteBoardKey() {
        return whiteBoardKey;
    }

    public String getWhiteBoardUrl() {
        return whiteBoardUrl;
    }

    public String getFlagsmithApiKey(){
        return flagsmithApiKey;
    }

    public String getFlagsmithApiUrl(){
        return flagsmithApiUrl;
    }

    public String getAppEnvironment() {
        return appEnvironment.name();
    }
    public boolean isLoggingEnabled() {
        return isLoggingEnabled;
    }
    public String getLivekitUrl() {
        return livekitUrl;
    }

    public String getSignUpUrl(){
        return signUpUrl;
    }

    public void setLivekitUrl(String livekitUrl){
        this.livekitUrl = livekitUrl;
    }

    public String getKeycloakLogoutIssuer() {
        return keycloakLogoutIssuer;
    }

    public int getMqttServerPort() {
        return mqttServerPort;
    }
}
