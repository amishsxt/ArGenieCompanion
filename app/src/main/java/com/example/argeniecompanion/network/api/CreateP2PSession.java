package com.example.argeniecompanion.network.api;


import com.example.argeniecompanion.app.ArGenieApp;
import com.example.argeniecompanion.logger.AppLogger;
import com.example.argeniecompanion.network.ApiRequestManager;
import com.example.argeniecompanion.network.callbacks.ApiAsyncResponseCallback;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

import cz.msebera.android.httpclient.entity.StringEntity;

public class CreateP2PSession {
    public interface CreateP2PSessionCallbacks {
        void onSuccess(JSONObject responseBody);
        void onFailure(String error);
    }

    private static final String TAG = "CreateP2PSession";

    public static void createAnonymousUser(String companyId, CreateP2PSessionCallbacks createP2PSessionCallbacks) {
        try {
            JSONObject jsonParams = new JSONObject();
            jsonParams.put("companyId", companyId);
            jsonParams.put("name", ArGenieApp.userName);
            StringEntity entity = new StringEntity(jsonParams.toString());
            ApiRequestManager.makeAsyncPostRequest(ArGenieApp.getInstance().getConfig().getApiUrl() + "/user/anonymous/new", entity, new ApiAsyncResponseCallback() {
                @Override
                public void OnStart() {

                }

                @Override
                public void OnSuccess(JSONObject response) {
                    createP2PSessionCallbacks.onSuccess(response);
                }

                @Override
                public void OnFailure(JSONObject responseError, Throwable error) {
                    String detail = "The service is temporarily unavailable. Please try again later.";
                    try {
                        if (responseError != null) {
                            detail = responseError.getString("detail");
                        }
                    } catch (JSONException e) {
                        AppLogger.e(TAG, "createAnonymousUser OnFailure: " + e.getMessage(), e);
                    }
                    createP2PSessionCallbacks.onFailure(detail);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void createP2PSession(String userId, String userType, CreateP2PSessionCallbacks createP2PSessionCallbacks) {
        try {
            JSONObject jsonParams = new JSONObject();
            jsonParams.put("userId", userId);
            if (Objects.equals(userType, "agent"))
                jsonParams.put("holderType", "ticket");
            else
                jsonParams.put("holderType", "customer");
            StringEntity entity = new StringEntity(jsonParams.toString());
            ApiRequestManager.makeAsyncPostRequest(ArGenieApp.getInstance().getConfig().getApiUrl() + "/link_code/fetch", entity, new ApiAsyncResponseCallback() {
                @Override
                public void OnStart() {
                }

                @Override
                public void OnSuccess(JSONObject response) {
                    createP2PSessionCallbacks.onSuccess(response);
                }

                @Override
                public void OnFailure(JSONObject responseError, Throwable error) {
                    String detail = "The service is temporarily unavailable. Please try again later.";
                    try {
                        if (responseError != null) {
                            detail = responseError.getString("detail");
                        }
                    } catch (JSONException e) {
                        AppLogger.e(TAG, "createAnonymousUser OnFailure: " + e.getMessage(), e);
                    }
                    createP2PSessionCallbacks.onFailure(detail);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void generateQr(String deeplinkUrl, CreateP2PSessionCallbacks callbacks) {
        try {
            JSONObject jsonParams = new JSONObject();
            jsonParams.put("link", deeplinkUrl);
            StringEntity entity = new StringEntity(jsonParams.toString());

            ApiRequestManager.makeAsyncPostRequest(ArGenieApp.getInstance().getConfig().getApiUrl() + "/sms/get-qrimage-url", entity, new ApiAsyncResponseCallback() {
                @Override
                public void OnStart() {

                }

                @Override
                public void OnSuccess(JSONObject response) {
                    callbacks.onSuccess(response);
                }

                @Override
                public void OnFailure(JSONObject responseError, Throwable error) {
                    String detail = "Unknown error during QR generation.";
                    try {
                        if (responseError != null && responseError.has("detail")) {
                            detail = responseError.getString("detail");
                        }
                    } catch (JSONException ignored) {
                        AppLogger.e(TAG,"generateQr OnFailure: "+ignored.getMessage().toString());
                    }finally {
                        String errorMessage = (error != null ? error.getMessage() : "No error message") + " - " + detail;
                        callbacks.onFailure(detail.isEmpty() ? errorMessage : detail);
                    }
                }
            });
        } catch (Exception e) {
            String errorMsg = "Exception in generateQr: " + e.getMessage();
            e.printStackTrace();
            callbacks.onFailure(errorMsg);
        }
    }
}