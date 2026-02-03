package com.example.argeniecompanion.ui;

import static com.example.argeniecompanion.app.ArGenieApp.chatSessionId;
import static com.example.argeniecompanion.app.ArGenieApp.hostCompanyId;
import static com.example.argeniecompanion.app.ArGenieApp.userId;
import static com.example.argeniecompanion.app.ArGenieApp.videoSessionId;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.argeniecompanion.R;
import com.example.argeniecompanion.app.ArGenieApp;
import com.example.argeniecompanion.livekit.LiveKitWrapper;
import com.example.argeniecompanion.logger.AppLogger;
import com.example.argeniecompanion.network.api.RemoteCallApi;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

public class LiveKitCallActivity extends AppCompatActivity {

    private static final String TAG = LiveKitCallActivity.class.getSimpleName();

    private TextView linkCodeTv;
    private TextView statusTv;
    private Button leaveBtn;
    private String linkCode;
    private LiveKitWrapper liveKitWrapper;

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startPhase1();
                } else {
                    Toast.makeText(this, "Camera permission is required for video calls", Toast.LENGTH_LONG).show();
                    finish();
                }
            });

    private final BroadcastReceiver leaveReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            AppLogger.i(TAG, "Leave broadcast received");
            leaveSession(true);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_live_kit_call);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        linkCodeTv = findViewById(R.id.link_code_tv);
        statusTv = findViewById(R.id.status_tv);
        leaveBtn = findViewById(R.id.leave_btn);

        linkCode = getIntent().getStringExtra(MainActivity.EXTRA_LINK_CODE);
        if (linkCode == null) {
            Toast.makeText(this, "No link code provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        linkCodeTv.setText(getString(R.string.link_code_display, linkCode));
        leaveBtn.setOnClickListener(v -> leaveSession(true));

        // Register for LEAVE broadcasts from MainActivity
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(leaveReceiver, new IntentFilter(MainActivity.ACTION_LEAVE));

        // Request camera permission before starting the call
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startPhase1();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    // Phase 1: Validate link code
    private void startPhase1() {
        statusTv.setText(R.string.status_validating);
        AppLogger.i(TAG, "Phase 1: Validating link code " + linkCode);

        RemoteCallApi.validateLinkCode(linkCode, ArGenieApp.userId, null,
                new RemoteCallApi.RemoteApiCallbacks() {
                    @Override
                    public void onSuccess(JSONObject responseBody) {
                        runOnUiThread(() -> {
                            AppLogger.i(TAG, "Phase 1 complete: Link code validated");
                            startPhase2();
                        });
                    }

                    @Override
                    public void onFailure(String message) {
                        runOnUiThread(() -> {
                            AppLogger.e(TAG, "Phase 1 failed: " + message);
                            statusTv.setText(getString(R.string.status_error, message));
                        });
                    }
                });
    }

    // Phase 2: Generate user ID and connect MQTT
    private void startPhase2() {
        statusTv.setText(R.string.status_generating_user);
        AppLogger.i(TAG, "Phase 2: Generating user ID");

        ArGenieApp.getInstance().generateUserId(new ArGenieApp.ContinueJoinWithLinkId() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    statusTv.setText(R.string.status_connecting_mqtt);
                    AppLogger.i(TAG, "Phase 2: Connecting MQTT");
                });
                connectMqtt();
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    AppLogger.e(TAG, "Phase 2 failed (user ID): " + error);
                    statusTv.setText(getString(R.string.status_error, error));
                });
            }
        });
    }

    private void connectMqtt() {
        ArGenieApp.getInstance().startMqtt(new ArGenieApp.MqttConnectionCallback() {
            @Override
            public void onMqttConnected() {
                AppLogger.i(TAG, "Phase 2 complete: MQTT connected");
                ArGenieApp.getInstance().getMqttWebRTC().initializeAllCommonListeners();

                startPhase3();
            }

            @Override
            public void onMqttConnectionFailure(String error) {
                runOnUiThread(() -> {
                    AppLogger.e(TAG, "Phase 2 failed (MQTT): " + error);
                    statusTv.setText(getString(R.string.status_error, error));
                });
            }
        });
    }

    // Phase 3: Join room
    private void startPhase3() {
        statusTv.setText(R.string.status_joining_room);
        AppLogger.i(TAG, "Phase 3: Joining room");

        RemoteCallApi.joinRoomApi(linkCode, userId, hostCompanyId, new RemoteCallApi.RemoteApiCallbacks() {
            @Override
            public void onSuccess(JSONObject responseBody) {
                runOnUiThread(() -> {
                    try {
                        if (responseBody != null && !responseBody.getBoolean("success")) {
                            Toast.makeText(LiveKitCallActivity.this,
                                    responseBody.getString("message"), Toast.LENGTH_LONG).show();
                            leaveSession(false);
                            return;
                        }
                    } catch (JSONException e) {
                        AppLogger.e(TAG, "Error parsing join response", e);
                    }

                    try {
                        if (responseBody != null) {
                            if (!Objects.equals(ArGenieApp.hostCompanyId,
                                    responseBody.getString("hostCompanyId"))) {
                                ArGenieApp.hostCompanyId = responseBody.getString("hostCompanyId");
                            }
                            if (responseBody.has("livekitServer")) {
                                String livekitUrl = responseBody.getString("livekitServer");
                                if (!livekitUrl.equals("null")) {
                                    ArGenieApp.getInstance().getConfig().setLivekitUrl(livekitUrl);
                                }
                            }
                        }
                    } catch (JSONException e) {
                        AppLogger.e(TAG, "Error parsing livekit config", e);
                    }

                    AppLogger.i(TAG, "Phase 3 complete: Joined room");
                    startPhase4();
                });
            }

            @Override
            public void onFailure(String message) {
                runOnUiThread(() -> {
                    AppLogger.e(TAG, "Phase 3 failed: " + message);
                    statusTv.setText(getString(R.string.status_error, message));
                    Toast.makeText(LiveKitCallActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // Phase 4: Generate LiveKit token and connect to LiveKit room
    private void startPhase4() {
        statusTv.setText(R.string.status_connecting_livekit);
        AppLogger.i(TAG, "Phase 4: Generating LiveKit token and connecting");

        liveKitWrapper = new LiveKitWrapper(getApplicationContext());
        liveKitWrapper.setConnectionCallback(new LiveKitWrapper.ConnectionCallback() {
            @Override
            public void onConnected() {
                AppLogger.i(TAG, "Phase 4 complete: LiveKit connected, streaming video");
                statusTv.setText(R.string.status_in_call);
            }

            @Override
            public void onFailure(String error) {
                AppLogger.e(TAG, "Phase 4 failed: " + error);
                statusTv.setText(getString(R.string.status_error, error));
            }
        });
        liveKitWrapper.start(linkCode);
    }

    private void leaveSession(boolean hangup) {
        AppLogger.i(TAG, "Leaving session, hangup=" + hangup);
        statusTv.setText(R.string.status_leaving);

        if (liveKitWrapper != null) {
            liveKitWrapper.stop();
        }

        RemoteCallApi.leaveSessionApi(userId, videoSessionId, chatSessionId, hangup, getApplicationContext());
        ArGenieApp.getInstance().disconnectMqtt();

        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(leaveReceiver);
    }
}
