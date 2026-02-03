package com.example.argeniecompanion.network.callbacks;

import org.json.JSONObject;

public interface ApiAsyncResponseCallback {
    void OnStart();
    void OnSuccess(JSONObject response);

    void OnFailure(JSONObject responseError, Throwable error);
}
