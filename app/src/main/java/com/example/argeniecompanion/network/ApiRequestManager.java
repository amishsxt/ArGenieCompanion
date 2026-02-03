package com.example.argeniecompanion.network;

import android.content.Context;
import android.net.Uri;
import android.os.Looper;

import com.example.argeniecompanion.app.ArGenieApp;
import com.example.argeniecompanion.app.DataStore;
import com.example.argeniecompanion.logger.AppLogger;
import com.example.argeniecompanion.network.callbacks.ApiAsyncResponseCallback;
import com.example.argeniecompanion.network.callbacks.ApiBaseResponseCallback;
import com.example.argeniecompanion.utils.JWTDecoder;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.GrantTypeValues;
import net.openid.appauth.TokenRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.entity.StringEntity;

public class ApiRequestManager {
    private final static String TAG = ApiRequestManager.class.getSimpleName();
    public static AsyncHttpClient client;
    private static volatile boolean isRefreshingToken = false;
    private static final List<ApiBaseResponseCallback> waitingRequests = new ArrayList<>();

    public static void init(){
        client = new AsyncHttpClient();
        client.setTimeout(30000);
        // Set the initial access token when the app starts
        String initialToken = ArGenieApp.accessToken;
        if (initialToken != null) {
            client.setBearerAuth(initialToken);
        }
    }

    public static void makeAsyncGetRequest(String url, ApiAsyncResponseCallback apiAsyncResponseCallback){
        try {
            client.get(url, new AsyncHttpResponseHandler(Looper.getMainLooper()) {
                @Override
                public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                    onApiSuccess(responseBody, apiAsyncResponseCallback);
                }

                @Override
                public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                    if (statusCode == 401) {
                        refreshAccessToken(() -> makeAsyncGetRequest(url, apiAsyncResponseCallback));
                    } else {
                        onApiFailure(responseBody, error, apiAsyncResponseCallback, url);
                    }
                }
            });
        } catch (Exception e) {
            AppLogger.e(TAG, "makeAsyncGetRequest error: ", e);
        }
    }

    public static void makeAsyncPostRequest(String url, StringEntity stringEntity, ApiAsyncResponseCallback apiAsyncResponseCallback){
        try {
            client.post(ArGenieApp.getAppContext(), url, stringEntity, "application/json",
                    new AsyncHttpResponseHandler(Looper.getMainLooper()) {
                        @Override
                        public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                            onApiSuccess(responseBody, apiAsyncResponseCallback);
                        }

                        @Override
                        public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                            if (statusCode == 401) {
                                refreshAccessToken(() -> makeAsyncPostRequest(url, stringEntity, apiAsyncResponseCallback));
                            } else {
                                onApiFailure(responseBody, error, apiAsyncResponseCallback, url);
                            }
                        }
                    });
        } catch (Exception e) {
            AppLogger.e(TAG, "makeAsyncPostRequest error: ", e);
        }
    }

    public static void makeAsyncPostRequest(String url, RequestParams params, ApiAsyncResponseCallback apiAsyncResponseCallback){
        try {
            client.post(url, params, new AsyncHttpResponseHandler(Looper.getMainLooper()) {
                @Override
                public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                    onApiSuccess(responseBody, apiAsyncResponseCallback);
                }

                @Override
                public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                    if (statusCode == 401) {
                        refreshAccessToken(() -> makeAsyncPostRequest(url, params, apiAsyncResponseCallback));
                    } else {
                        onApiFailure(responseBody, error, apiAsyncResponseCallback, url);
                    }
                }
            });
        } catch (Exception e) {
            AppLogger.e(TAG, "makeAsyncPostRequest error: ", e);
        }
    }

    public static void makeAsyncDeleteRequest(String url, StringEntity stringEntity, ApiAsyncResponseCallback apiAsyncResponseCallback){
        try {
            client.delete(ArGenieApp.getAppContext(), url, stringEntity, "application/json",new AsyncHttpResponseHandler(Looper.getMainLooper()) {
                @Override
                public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                    onApiSuccess(responseBody, apiAsyncResponseCallback);
                }

                @Override
                public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                    if (statusCode == 401) {
                        refreshAccessToken(() -> makeAsyncDeleteRequest(url, stringEntity, apiAsyncResponseCallback));
                    } else {
                        onApiFailure(responseBody, error, apiAsyncResponseCallback, url);
                    }
                }
            });
        } catch (Exception e) {
            AppLogger.e(TAG, "makeAsyncDeleteRequest error: ", e);
        }
    }

    public static synchronized void refreshAccessToken(ApiBaseResponseCallback requestToRetry) {
        if (isRefreshingToken) {
            // If a refresh is already in progress, add the new request to the waiting list.
            AppLogger.d(TAG, "Token refresh already in progress. Queueing request.");
            waitingRequests.add(requestToRetry);
            return;
        }

        isRefreshingToken = true;
        AppLogger.i(TAG, "Access token expired. Starting refresh process...");

        Context context = ArGenieApp.getAppContext();
        String currentRefreshToken = ArGenieApp.refreshToken;
        String issuerUri = ArGenieApp.getInstance().getConfig().getKeycloakIssuer();
        String clientId = ArGenieApp.getInstance().getConfig().getKeycloakClientId();
        String clientSecret = ArGenieApp.getInstance().getConfig().getKeycloakClientSecret();

        if (currentRefreshToken == null || issuerUri == null) {
            AppLogger.e(TAG, "Cannot refresh token: Refresh token or issuer URI is null.");
            isRefreshingToken = false; // Reset the flag
//            SessionManagement.showSessionExpiredAlert();
            return;
        }

        AuthorizationServiceConfiguration.fetchFromIssuer(Uri.parse(issuerUri), (serviceConfig, ex) -> {
            if (ex != null || serviceConfig == null) {
                AppLogger.e(TAG, "Failed to fetch service configuration for token refresh.", ex);
                isRefreshingToken = false;
//                SessionManagement.showSessionExpiredAlert();
                return;
            }

            // For confidential clients, the secret must be sent as an additional parameter.
            Map<String, String> additionalParams = new HashMap<>();
            additionalParams.put("client_secret", clientSecret);

            TokenRequest tokenRequest = new TokenRequest.Builder(serviceConfig, clientId)
                    .setGrantType(GrantTypeValues.REFRESH_TOKEN)
                    .setRefreshToken(currentRefreshToken)
                    .setAdditionalParameters(additionalParams)
                    .build();

            AuthorizationService authService = new AuthorizationService(context);
            authService.performTokenRequest(tokenRequest, (tokenResponse, tokenEx) -> {
                synchronized (ApiRequestManager.class) {
                    if (tokenResponse != null) {
                        AppLogger.i(TAG, "Token refresh successful!");

                        // 1. Get the raw tokens from the response
                        String accessToken = tokenResponse.accessToken;
                        String idToken = tokenResponse.idToken;
                        String refreshToken = tokenResponse.refreshToken;

                        // 2. Decode the ID Token to access the custom claims
                        JSONObject decodedIdToken = JWTDecoder.decodeJWT(idToken);
                        String planId = null;
                        if (decodedIdToken != null) {
                            planId = decodedIdToken.optString("plan_id");
                        }

                        // 3. Save everything to your DataStore
                        DataStore.saveAuthData(
                                planId,
                                accessToken,
                                refreshToken,
                                idToken
                        );

                        // Update the HTTP client with the new token
                        client.setBearerAuth(tokenResponse.accessToken);

                        requestToRetry.onSuccess();
                        // Retry all other requests that were waiting
                        for (ApiBaseResponseCallback waitingRequest : waitingRequests) {
                            waitingRequest.onSuccess();
                        }
                    } else {
                        AppLogger.e(TAG, "Token refresh failed. Refresh token may be invalid.", tokenEx);
//                        SessionManagement.showSessionExpiredAlert();
                    }

                    // Clean up for the next time
                    waitingRequests.clear();
                    isRefreshingToken = false;
                }
            });
        });
    }

    public static void onApiSuccess(byte[] responseBody, ApiAsyncResponseCallback callback) {
        if (callback == null) return;

        ApiResponse response = ApiResponse.from(responseBody);

        // Backward compatibility:
        if (response.jsonObject != null) {
            callback.OnSuccess(response.jsonObject);
        } else if (response.jsonArray != null) {
            // Wrap array to keep existing callback signature safe
            JSONObject wrapper = new JSONObject();
            try {
                wrapper.put("data", response.jsonArray);
            } catch (JSONException ignored) {}
            callback.OnSuccess(wrapper);
        } else {
            // Non-JSON success (204, text, etc.)
            callback.OnSuccess(null);
        }
    }


    public static void onApiFailure(byte[] responseBody, Throwable error, ApiAsyncResponseCallback callback, String url) {
        if (url != null) {
            AppLogger.e(TAG, "onApiFailure(): Error fetching: " + url);
        }

        if (error != null) {
            AppLogger.e(TAG, "API Error: ", error);
        }

        if (callback == null) return;

        ApiResponse response = ApiResponse.from(responseBody);

        // Only pass JSONObject if it's REALLY JSON
        if (response.jsonObject != null) {
            callback.OnFailure(response.jsonObject, error);
        } else {
            // Create a safe fallback error object
            JSONObject fallback = new JSONObject();
            try {
                fallback.put("raw", response.raw);
                fallback.put("message", error != null ? error.getMessage() : "Unknown error");
            } catch (JSONException ignored) {}

            callback.OnFailure(fallback, error);
        }
    }

}
