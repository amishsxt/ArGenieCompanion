package com.example.argeniecompanion.app;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.argeniecompanion.R;
import com.example.argeniecompanion.logger.AppLogger;
import com.example.argeniecompanion.network.ApiRequestManager;

public class DataStore {
    private final static String TAG = DataStore.class.getSimpleName();
    private final static SharedPreferences userSharedPreferences = ArGenieApp.getInstance().getSharedPreferences(
            ArGenieApp.getAppContext().getString(R.string.user_data_file_key), Context.MODE_PRIVATE);
    public static void saveUserData(String companyId, String companyName, String userId, String userName, String userEmailId, String planId, String accessToken, String refreshToken, String idToken) {
        SharedPreferences.Editor editor = userSharedPreferences.edit();
        editor.putString("userId", userId);
        editor.putString("companyId", companyId);
        editor.putString("companyName", companyName);
        editor.putString("userName", userName);
        editor.putString("userEmailId", userEmailId);
        editor.putBoolean("isGuestUser", false);
        editor.putString("planId", planId);
        editor.putString("idToken", idToken);
        editor.putInt("versionCode", ArGenieApp.VERSION_CODE);
        editor.putString("apiUrl", ArGenieApp.getInstance().getConfig().getApiUrl());
        editor.putString("deviceId", ArGenieApp.getUserDeviceId());
        editor.apply();
        saveAuthData(planId, accessToken, refreshToken, idToken);
        getUserSavedData();
        AppLogger.d(TAG, "saveUserData(): User details saved");
    }

    public static void getUserSavedData() {
        ArGenieApp.userId = userSharedPreferences.getString("userId", null);
        ArGenieApp.companyId = userSharedPreferences.getString("companyId", null);
        ArGenieApp.companyName = userSharedPreferences.getString("companyName", null);
        ArGenieApp.userEmailId = userSharedPreferences.getString("userEmailId", null);
        ArGenieApp.planId = userSharedPreferences.getString("planId", null);
        ArGenieApp.isGuestUser = userSharedPreferences.getBoolean("isGuestUser", true);
        ArGenieApp.apiUrl = userSharedPreferences.getString("apiUrl", null);
        ArGenieApp.deviceId = userSharedPreferences.getString("deviceId", null);
        ArGenieApp.accessToken = userSharedPreferences.getString("accessToken", null);
        ArGenieApp.refreshToken = userSharedPreferences.getString("refreshToken", null);
        ArGenieApp.idToken = userSharedPreferences.getString("idToken", null);
        ArGenieApp.userName = userSharedPreferences.getString("userName", "Guest");

        AppLogger.d(TAG, "getUserSavedData(): Got: userId: " + ArGenieApp.userId + " isGuestUser: " + ArGenieApp.isGuestUser + " companyId: " + ArGenieApp.companyId+ " AccessToken: "+ArGenieApp.accessToken+" refreshToken?:"+ArGenieApp.refreshToken);

        if (ArGenieApp.accessToken == null){
            //re-initialize api request client
            ApiRequestManager.init();
        }
    }

    public static void saveAuthData(String planId, String accessToken, String refreshToken, String idToken){
        ArGenieApp.planId = planId;
        ArGenieApp.accessToken = accessToken;
        ArGenieApp.refreshToken = refreshToken;
        ArGenieApp.idToken = idToken;

        // Update the client BearerAuth with updated accessToken
        if (ApiRequestManager.client != null && accessToken != null) {
            ApiRequestManager.client.setBearerAuth(accessToken);
        }

        SharedPreferences.Editor editor = userSharedPreferences.edit();
        editor.putString("accessToken", accessToken);
        editor.putString("refreshToken", refreshToken);
        editor.putString("idToken", idToken);
        editor.apply();
        AppLogger.d(TAG, "saveAuthData(): User auth details saved");
    }

    public static void deleteUserAllData(){
        // Step 1: Clear all data from SharedPreferences
        SharedPreferences.Editor editor = userSharedPreferences.edit();
        editor.clear();
        editor.apply();

        // Step 2: Manually reset all the live data in the ArGenieApp singleton
        ArGenieApp.userId = null;
        ArGenieApp.companyId = null;
        ArGenieApp.companyName = null;
        ArGenieApp.userEmailId = null;
        ArGenieApp.planId = null;
        ArGenieApp.isGuestUser = true; // Default to guest
        ArGenieApp.userName = "Guest";
        ArGenieApp.accessToken = null;
        ArGenieApp.refreshToken = null;
        ArGenieApp.idToken = null;
        ArGenieApp.apiUrl = null;
        ArGenieApp.hostCompanyId = null;
        ArGenieApp.agentId = null;
        ArGenieApp.ticketId = null;
        ArGenieApp.currentMeetingId = null;
        ArGenieApp.videoSessionId = null;
        ArGenieApp.chatSessionId = null;

        // Step 3: Re-initialize services that depend on the user state
        ApiRequestManager.init(); // Re-initializes the API client without an auth token

        AppLogger.d(TAG, "deleteUserAllData(): All user data cleared from storage and app state.");
    }

    public static void saveGuestUserName(String localUserName){
        SharedPreferences.Editor editor = userSharedPreferences.edit();
        editor.putString("userName", localUserName);
        editor.apply();
    }
}
