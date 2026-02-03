package com.example.argeniecompanion.network.api;

import android.content.Context;

import com.example.argeniecompanion.app.ArGenieApp;
import com.example.argeniecompanion.logger.AppLogger;
import com.example.argeniecompanion.network.ApiRequestManager;
import com.example.argeniecompanion.network.callbacks.ApiAsyncResponseCallback;
import com.example.argeniecompanion.network.callbacks.OnInfoTextCallbacks;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

import cz.msebera.android.httpclient.entity.StringEntity;

public class RemoteCallApi {

    public interface RemoteApiCallbacks {
        void onSuccess(JSONObject responseBody);
        void onFailure(String message);
    }

    public static final String TAG = RemoteCallApi.class.getSimpleName();

    public static void validateLinkCode(String linkId, String userId, OnInfoTextCallbacks onInfoTextCallbacks, RemoteApiCallbacks remoteApiCallbacks) {
        try {
            if (ApiRequestManager.client == null) ApiRequestManager.init();

            if (onInfoTextCallbacks != null) {
                onInfoTextCallbacks.onTextChange("Validating link code...");
            }

            ApiRequestManager.makeAsyncPostRequest(
                    ArGenieApp.getInstance().getConfig().getApiUrl() + "/link_code/validate/" + linkId,
                    (StringEntity) null,
                    new ApiAsyncResponseCallback() {
                        @Override
                        public void OnStart() {
                            AppLogger.d(TAG, "valida    teLinkCode: Starting validation for linkId: " + linkId);
                        }

                        @Override
                        public void OnSuccess(JSONObject response) {
                            AppLogger.d(TAG, "validateLinkCode: Success - " + (response != null ? response.toString() : "null"));
                            ArGenieApp.currentMeetingId = linkId;
                            // If the response is JSONObject instead of expected JSONArray
                            try {
                                ArGenieApp.videoSessionId = response.getString("videoSessionId");
                                ArGenieApp.chatSessionId = response.getString("chatSessionId");
                                ArGenieApp.hostCompanyId = response.getString("hostCompanyId");
                                String agentId = response.getString("creator");
                                if (!Objects.equals(userId, agentId)) {
                                    ArGenieApp.agentId = agentId;
                                }
                                String holderType = response.getString("holderType");
                                if (holderType.equals("ticket"))
                                    ArGenieApp.ticketId = response.getString("ticketId");

                                remoteApiCallbacks.onSuccess(null);

                            } catch (JSONException e) {
                                AppLogger.e(TAG, Objects.requireNonNull(e.getMessage()));
                                remoteApiCallbacks.onFailure("1");
                            }
                        }

                        @Override
                        public void OnFailure(JSONObject responseError, Throwable error) {
                            AppLogger.e(TAG, "validateLinkCode: Failed - " + (responseError != null ? responseError.toString() : "null"));
                            if (onInfoTextCallbacks != null) {
                                onInfoTextCallbacks.onTextChange(null);
                            }
                            if (remoteApiCallbacks != null) {
                                String message = "Validation failed";
                                try {
                                    if (responseError != null && responseError.has("detail")) {
                                        message = responseError.getString("detail");
                                    } else if (responseError != null && responseError.has("message")) {
                                        message = responseError.getString("message");
                                    } else if (error != null) {
                                        message = error.getMessage();
                                    }
                                } catch (Exception e) {
                                    AppLogger.e(TAG, "Error parsing failure response", e);
                                }
                                remoteApiCallbacks.onFailure(message);
                            }
                        }
                    });
        } catch (Exception e) {
            AppLogger.e(TAG, "validateLinkCode: Exception", e);
            if (onInfoTextCallbacks != null) {
                onInfoTextCallbacks.onTextChange(null);
            }
            if (remoteApiCallbacks != null) {
                remoteApiCallbacks.onFailure("Failed to validate link code");
            }
        }
    }

    public static void leaveSessionApi(String userId, String videoSessionId, String chatSessionId, Boolean hangup, Context context) {
        try {
            String currentMeetingId = ArGenieApp.currentMeetingId;
            AppLogger.d(TAG, "leaveSessionApi(): for linkId: " + currentMeetingId);

            if (currentMeetingId == null) {
                AppLogger.w(TAG, "leaveSessionApi(): No current meeting ID, skipping leave request");
                return;
            }

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("userId", userId);
            jsonObject.put("videoSessionId", videoSessionId);
            jsonObject.put("chatSessionId", chatSessionId);
            jsonObject.put("deviceId", ArGenieApp.getUserDeviceId());

            StringEntity stringEntity = new StringEntity(jsonObject.toString());

            ApiRequestManager.makeAsyncPostRequest(
                    ArGenieApp.getInstance().getConfig().getApiUrl() + "/link_code/leave/" + currentMeetingId,
                    stringEntity,
                    new ApiAsyncResponseCallback() {
                        @Override
                        public void OnStart() {
                            AppLogger.d(TAG, "leaveSessionApi: Starting leave request");
                        }

                        @Override
                        public void OnSuccess(JSONObject response) {
                            AppLogger.d(TAG, "leaveSessionApi: Successfully left session");
                            // Clear the meeting ID after successful leave
                            ArGenieApp.currentMeetingId = null;
                        }

                        @Override
                        public void OnFailure(JSONObject responseError, Throwable error) {
                            AppLogger.e(TAG, "leaveSessionApi: Failed to leave session - " +
                                    (responseError != null ? responseError.toString() : (error != null ? error.getMessage() : "Unknown error")));
                        }
                    });
        } catch (Exception e) {
            AppLogger.e(TAG, "leaveSessionApi: Exception", e);
        }
    }

    public static void joinRoomApi(String code, String userId, String companyId, RemoteApiCallbacks remoteApiCallbacks) {
        try {
            AppLogger.d(TAG, "joinRoomApi: Joining room with code: " + code);

            JSONObject jsonParams = new JSONObject();
            jsonParams.put("companyId", companyId);
            if (userId != null) {
                jsonParams.put("userId", userId);
                jsonParams.put("userType", ArGenieApp.getUserType());
                jsonParams.put("deviceId", ArGenieApp.getUserDeviceId());
            }

            AppLogger.i(TAG, jsonParams.toString());

            StringEntity entity = new StringEntity(jsonParams.toString());

            ApiRequestManager.makeAsyncPostRequest(
                    ArGenieApp.getInstance().getConfig().getApiUrl() + "/link_code/join/" + code,
                    entity,
                    new ApiAsyncResponseCallback() {
                        @Override
                        public void OnStart() {
                            AppLogger.d(TAG, "joinRoomApi: Starting join request");
                        }

                        @Override
                        public void OnSuccess(JSONObject response) {
                            AppLogger.d(TAG, "joinRoomApi: Successfully joined room - " + (response != null ? response.toString() : "null"));

                            // Store the meeting ID from response if available
                            if (response != null) {
                                try {
                                    if (response.has("meetingId")) {
                                        String meetingId = response.getString("meetingId");
                                        ArGenieApp.currentMeetingId = meetingId;
                                    } else if (response.has("linkId")) {
                                        String linkId = response.getString("linkId");
                                        ArGenieApp.currentMeetingId = linkId;
                                    }
                                } catch (Exception e) {
                                    AppLogger.e(TAG, "joinRoomApi: Error parsing meeting ID", e);
                                }
                            }

                            if (remoteApiCallbacks != null) {
                                remoteApiCallbacks.onSuccess(response);
                            }
                        }

                        @Override
                        public void OnFailure(JSONObject responseError, Throwable error) {
                            AppLogger.e(TAG, "joinRoomApi: Failed to join room - " +
                                    (responseError != null ? responseError.toString() : (error != null ? error.getMessage() : "Unknown error")));

                            if (remoteApiCallbacks != null) {
                                String message = "Failed to join room";
                                try {
                                    if (responseError != null && responseError.has("detail")) {
                                        message = responseError.getString("detail");
                                    } else if (responseError != null && responseError.has("message")) {
                                        message = responseError.getString("message");
                                    } else if (error != null) {
                                        message = error.getMessage();
                                    }
                                } catch (Exception e) {
                                    AppLogger.e(TAG, "Error parsing failure response", e);
                                }
                                remoteApiCallbacks.onFailure(message);
                            }
                        }
                    });
        } catch (Exception e) {
            AppLogger.e(TAG, "joinRoomApi: Exception - " + e.getMessage(), e);
            if (remoteApiCallbacks != null) {
                remoteApiCallbacks.onFailure("Failed to join room: " + e.getMessage());
            }
        }
    }
}
