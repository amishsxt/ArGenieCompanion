package com.example.argeniecompanion.network;

import com.example.argeniecompanion.logger.AppLogger;

import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class PostJson {
    private static final String TAG = "ApiService";
    private static final OkHttpClient client = new OkHttpClient();

    public static void postJson(String url, String userId, String linkCode, String userName, String deviceId, Callback callback) {
        try {
            // Create JSON body
            String jsonBody = new JSONObject()
                    .put("userId", userId)
                    .put("linkCode", linkCode)
                    .put("name", userName)
                    .put("deviceId", deviceId)
                    .toString();

            // Create RequestBody
            RequestBody body = RequestBody.create(
                    jsonBody,
                    MediaType.parse("application/json; charset=utf-8")
            );

            // Build request with headers
            Request.Builder requestBuilder = new Request.Builder()
                    .url(url)
                    .post(body);

            // Create and execute the request
            Request request = requestBuilder.build();
            client.newCall(request).enqueue(callback);

        } catch (JSONException e) {
            AppLogger.e(TAG, "postJson(): catch{}: Error creating JSON body", e);
        }
    }
}