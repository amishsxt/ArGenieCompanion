package com.example.argeniecompanion.utils;

import android.util.Base64;

import com.example.argeniecompanion.logger.AppLogger;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public class JWTDecoder {
    private static final String TAG = JWTDecoder.class.getSimpleName();
    public static JSONObject decodeJWT(String jwtToken) {
        String[] jwtParts = jwtToken.split("\\.");
        if (jwtParts.length < 2) {
            return null; // Invalid JWT format
        }
        String base64Url = jwtParts[1];
        String base64 = base64Url.replace("-", "+").replace("_", "/");
        byte[] decodedBytes = Base64.decode(base64, Base64.URL_SAFE);
        try {
            String jsonPayload = new String(decodedBytes, StandardCharsets.UTF_8);
            return new JSONObject(jsonPayload);
        } catch (JSONException e) {
            AppLogger.d(TAG,"jwt token jsonObject conversion error: "+e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
