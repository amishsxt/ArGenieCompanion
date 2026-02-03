package com.example.argeniecompanion.network;

import org.json.JSONArray;
import org.json.JSONObject;

public class ApiResponse {
    public JSONObject jsonObject;
    public JSONArray jsonArray;
    public String raw;
    public boolean isJson;

    public static ApiResponse from(byte[] body) {
        ApiResponse r = new ApiResponse();

        if (body == null || body.length == 0) {
            r.raw = null;
            r.isJson = false;
            return r;
        }

        String text = new String(body).trim();
        r.raw = text;

        try {
            if (text.startsWith("{")) {
                r.jsonObject = new JSONObject(text);
                r.isJson = true;
            } else if (text.startsWith("[")) {
                r.jsonArray = new JSONArray(text);
                r.isJson = true;
            }
        } catch (Exception ignored) {
            r.isJson = false;
        }

        return r;
    }
}

