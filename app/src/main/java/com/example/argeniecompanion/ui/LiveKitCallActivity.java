package com.example.argeniecompanion.ui;

import static com.example.argeniecompanion.app.ArGenieApp.chatSessionId;
import static com.example.argeniecompanion.app.ArGenieApp.hostCompanyId;
import static com.example.argeniecompanion.app.ArGenieApp.userId;
import static com.example.argeniecompanion.app.ArGenieApp.videoSessionId;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
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
import com.example.argeniecompanion.bluetooth.protocol.BleCommandListener;
import com.example.argeniecompanion.bluetooth.protocol.BleGattServer;
import com.example.argeniecompanion.bluetooth.protocol.BleGattServerService;
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

    // BLE GATT Server integration
    private BleGattServerService bleService;
    private boolean bleServiceBound = false;
    private boolean micMuted = false;
    private boolean videoMuted = false;

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

    private final ServiceConnection bleServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BleGattServerService.LocalBinder binder = (BleGattServerService.LocalBinder) service;
            bleService = binder.getService();
            bleServiceBound = true;
            AppLogger.i(TAG, "BLE GATT Server service bound");

            // Set command listener for BLE commands
            bleService.setCommandListener(bleCommandListener);

            // Set connection listener (optional, for logging)
            bleService.setConnectionListener(new BleGattServer.ConnectionListener() {
                @Override
                public void onDeviceConnected(String deviceName, String deviceAddress) {
                    AppLogger.i(TAG, "BLE Controller connected: " + deviceName);
                }

                @Override
                public void onDeviceDisconnected() {
                    AppLogger.i(TAG, "BLE Controller disconnected");
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bleServiceBound = false;
            bleService = null;
            AppLogger.i(TAG, "BLE GATT Server service unbound");
        }
    };

    private final BleCommandListener bleCommandListener = new BleCommandListener() {
        @Override
        public boolean onJoinRoom(String linkCode, String userName) {
            // Already in a room, reject new join requests
            AppLogger.w(TAG, "JOIN_ROOM received while already in call, ignoring");
            return false;
        }

        @Override
        public void onLeaveRoom() {
            AppLogger.i(TAG, "LEAVE_ROOM command received via BLE");
            runOnUiThread(() -> leaveSession(true));
        }

        @Override
        public void onMicMute() {
            AppLogger.i(TAG, "MIC_MUTE command received via BLE");
            runOnUiThread(() -> {
                if (liveKitWrapper != null) {
                    liveKitWrapper.enableMicrophone(false);
                    micMuted = true;
                    updateBleServiceState();
                    AppLogger.i(TAG, "Microphone muted");
                }
            });
        }

        @Override
        public void onMicUnmute() {
            AppLogger.i(TAG, "MIC_UNMUTE command received via BLE");
            runOnUiThread(() -> {
                if (liveKitWrapper != null) {
                    liveKitWrapper.enableMicrophone(true);
                    micMuted = false;
                    updateBleServiceState();
                    AppLogger.i(TAG, "Microphone unmuted");
                }
            });
        }

        @Override
        public void onVideoMute() {
            AppLogger.i(TAG, "VIDEO_MUTE command received via BLE");
            runOnUiThread(() -> {
                if (liveKitWrapper != null) {
                    liveKitWrapper.enableCamera(false);
                    videoMuted = true;
                    updateBleServiceState();
                    AppLogger.i(TAG, "Camera disabled");
                }
            });
        }

        @Override
        public void onVideoUnmute() {
            AppLogger.i(TAG, "VIDEO_UNMUTE command received via BLE");
            runOnUiThread(() -> {
                if (liveKitWrapper != null) {
                    liveKitWrapper.enableCamera(true);
                    videoMuted = false;
                    updateBleServiceState();
                    AppLogger.i(TAG, "Camera enabled");
                }
            });
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

                // Bind to BLE service to receive commands now that we're in the call
                bindBleService();

                // Initialize state - camera is on by default, mic starts muted
                micMuted = false;
                videoMuted = false;
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

        // Update BLE service state - no longer in room
        if (bleServiceBound && bleService != null) {
            bleService.setInRoom(false);
        }

        if (liveKitWrapper != null) {
            liveKitWrapper.stop();
            liveKitWrapper = null;
        }

        RemoteCallApi.leaveSessionApi(userId, videoSessionId, chatSessionId, hangup, getApplicationContext());
        ArGenieApp.getInstance().disconnectMqtt();

        // Clear session state so the next join starts fresh
        ArGenieApp.clearSession();

        finish();
    }

    /**
     * Bind to the BLE GATT Server service to receive commands.
     * Called after successfully joining a room.
     */
    private void bindBleService() {
        Intent intent = new Intent(this, BleGattServerService.class);
        bindService(intent, bleServiceConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * Unbind from the BLE GATT Server service.
     */
    private void unbindBleService() {
        if (bleServiceBound) {
            // Clear listeners before unbinding
            if (bleService != null) {
                bleService.setCommandListener(null);
                bleService.setConnectionListener(null);
                bleService.setInRoom(false);
            }
            unbindService(bleServiceConnection);
            bleServiceBound = false;
            bleService = null;
        }
    }

    /**
     * Update the BLE service with current mic/video/room state.
     */
    private void updateBleServiceState() {
        if (bleServiceBound && bleService != null) {
            bleService.setMicMuted(micMuted);
            bleService.setVideoMuted(videoMuted);
            bleService.setInRoom(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(leaveReceiver);
        unbindBleService();
    }
}
