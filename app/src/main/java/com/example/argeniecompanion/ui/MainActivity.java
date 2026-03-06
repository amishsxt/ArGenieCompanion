package com.example.argeniecompanion.ui;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static com.example.argeniecompanion.app.ArGenieApp.chatSessionId;
import static com.example.argeniecompanion.app.ArGenieApp.hostCompanyId;
import static com.example.argeniecompanion.app.ArGenieApp.userId;
import static com.example.argeniecompanion.app.ArGenieApp.videoSessionId;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.argeniecompanion.R;
import com.example.argeniecompanion.app.ArGenieApp;
import com.example.argeniecompanion.bluetooth.protocol.BleCommandListener;
import com.example.argeniecompanion.bluetooth.protocol.BleGattServer;
import com.example.argeniecompanion.bluetooth.protocol.BleGattServerService;
import com.example.argeniecompanion.bluetooth.protocol.BleProtocol;
import com.example.argeniecompanion.livekit.LiveKitWrapper;
import com.example.argeniecompanion.logger.AppLogger;
import com.example.argeniecompanion.model.ChatMessage;
import com.example.argeniecompanion.network.api.RemoteCallApi;
import com.example.argeniecompanion.network.pubsub.MqttWebRTC;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements MqttWebRTC.MessageCallbacks {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_PERMISSIONS = 101;

    // UI State enum
    private enum UIState {
        NOT_RUNNING,
        SERVER_RUNNING,
        JOINING,
        IN_CALL
    }

    private UIState currentState = UIState.NOT_RUNNING;

    private static final long MESSAGE_PREVIEW_DURATION_MS = 5000;

    // UI Elements
    private ImageView backBtn, chatIv, documentsIv, whiteBoardIv;
    private Button startServerBtn;
    private Button stopServerBtn;
    private TextView statusTv;
    private TextView linkCodeTv;
    private TextView messageLogTv;
    private TextView messagePreviewTv;
    private TextView messagePreviewTimeTv;
    private LinearLayout messagePreviewContainer;
    private ScrollView messageScrollView;
    private LinearLayout callControlsLayout;
    private Button leaveBtn;
    private Button micBtn;
    private Button cameraBtn;

    private final StringBuilder messageLog = new StringBuilder();
    private static final long FADE_DURATION_MS = 300;

    private final Handler previewHandler = new Handler(Looper.getMainLooper());
    private final Runnable hidePreviewRunnable = () -> {
        if (messagePreviewContainer == null) return;
        messagePreviewContainer.animate()
                .alpha(0f)
                .setDuration(FADE_DURATION_MS)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        messagePreviewContainer.setVisibility(View.GONE);
                        messagePreviewContainer.setAlpha(1f);
                    }
                })
                .start();
    };

    // Chat & Documents
    private final List<ChatMessage> chatMessages = new ArrayList<>();
    private ChatFragment      chatFragment;
    private DocumentsFragment documentsFragment;

    // BLE Service binding
    private BleGattServerService bleService;
    private boolean bleServiceBound = false;

    // LiveKit
    private LiveKitWrapper liveKitWrapper;
    private String linkCode, userName;
    private boolean micMuted = false;
    private boolean videoMuted = false;

    private static final int REQUEST_CALL_PERMISSIONS = 102;

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startPhase1();
                } else {
                    Toast.makeText(this, "Camera permission is required for video calls", Toast.LENGTH_LONG).show();
                    addLog("Camera permission denied");
                    updateUIState(UIState.SERVER_RUNNING);
                }
            });

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BleGattServerService.LocalBinder binder = (BleGattServerService.LocalBinder) service;
            bleService = binder.getService();
            bleServiceBound = true;

            // Set command listener for JOIN/LEAVE
            bleService.setCommandListener(commandListener);

            // Set connection listener
            bleService.setConnectionListener(connectionListener);

            // Reset in-room state when binding (in case service was running from previous session)
            bleService.setInRoom(false);
            bleService.setMicMuted(false);
            bleService.setVideoMuted(false);

            addLog("BLE service bound");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bleServiceBound = false;
            bleService = null;
            addLog("BLE service unbound");
        }
    };

    private final BleGattServer.ConnectionListener connectionListener = new BleGattServer.ConnectionListener() {
        @Override
        public void onDeviceConnected(String deviceName, String deviceAddress) {
            runOnUiThread(() -> {
                if (currentState == UIState.SERVER_RUNNING) {
                    statusTv.setText("Connected to " + (deviceName != null ? deviceName : "device"));
                }
                addLog("Connected to: " + deviceName + " [" + deviceAddress + "]");
            });
        }

        @Override
        public void onDeviceDisconnected() {
            runOnUiThread(() -> {
                if (currentState == UIState.SERVER_RUNNING) {
                    statusTv.setText(R.string.status_waiting_join);
                }
                addLog("Device disconnected");
            });
        }
    };

    private final BleCommandListener commandListener = new BleCommandListener() {
        @Override
        public void onJoinRoom(String linkCode, String userName) {
            runOnUiThread(() -> {
                if (currentState == UIState.IN_CALL) {
                    addLog("JOIN_ROOM received while already in call, ignoring");
                    return;
                }

                addLog("JOIN_ROOM: linkCode=" + linkCode + ", userName=" + userName);
                MainActivity.this.linkCode = linkCode;
                MainActivity.this.userName = userName;

                // Request camera + audio permissions then start connection flow
                boolean hasCameraPermission = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED;
                boolean hasAudioPermission = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED;

                if (hasCameraPermission && hasAudioPermission) {
                    startPhase1();
                } else {
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                            REQUEST_CALL_PERMISSIONS);
                }
            });
        }

        @Override
        public void onLeaveRoom() {
            runOnUiThread(() -> {
                addLog("LEAVE_ROOM received");
                if (currentState == UIState.IN_CALL) {
                    leaveSession(true);
                }
            });
        }

        @Override
        public void onMicMute() {
            runOnUiThread(() -> {
                if (currentState == UIState.IN_CALL && liveKitWrapper != null) {
                    liveKitWrapper.enableMicrophone(false);
                    micMuted = true;
                    updateMicButton();
                    updateBleServiceState();
                    addLog("Microphone muted (BLE)");
                } else {
                    addLog("MIC_MUTE (not in call, ignored)");
                }
            });
        }

        @Override
        public void onMicUnmute() {
            runOnUiThread(() -> {
                if (currentState == UIState.IN_CALL && liveKitWrapper != null) {
                    liveKitWrapper.enableMicrophone(true);
                    micMuted = false;
                    updateMicButton();
                    updateBleServiceState();
                    addLog("Microphone unmuted (BLE)");
                } else {
                    addLog("MIC_UNMUTE (not in call, ignored)");
                }
            });
        }

        @Override
        public void onVideoMute() {
            runOnUiThread(() -> {
                if (currentState == UIState.IN_CALL && liveKitWrapper != null) {
                    liveKitWrapper.enableCamera(false);
                    videoMuted = true;
                    updateCameraButton();
                    updateBleServiceState();
                    addLog("Camera disabled (BLE)");
                } else {
                    addLog("VIDEO_MUTE (not in call, ignored)");
                }
            });
        }

        @Override
        public void onVideoUnmute() {
            runOnUiThread(() -> {
                if (currentState == UIState.IN_CALL && liveKitWrapper != null) {
                    liveKitWrapper.enableCamera(true);
                    videoMuted = false;
                    updateCameraButton();
                    updateBleServiceState();
                    addLog("Camera enabled (BLE)");
                } else {
                    addLog("VIDEO_UNMUTE (not in call, ignored)");
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI elements
        backBtn = findViewById(R.id.back_btn);
        startServerBtn = findViewById(R.id.start_server_btn);
        stopServerBtn = findViewById(R.id.stop_server_btn);
        statusTv = findViewById(R.id.status_tv);
        linkCodeTv = findViewById(R.id.link_code_tv);
        messageLogTv = findViewById(R.id.message_log_tv);
        messageScrollView = findViewById(R.id.message_scroll_view);
        callControlsLayout = findViewById(R.id.call_controls_layout);
        leaveBtn = findViewById(R.id.leave_btn);
        micBtn = findViewById(R.id.mic_btn);
        cameraBtn = findViewById(R.id.camera_btn);
        chatIv          = findViewById(R.id.chat_iv);
        documentsIv     = findViewById(R.id.documents_iv);
        whiteBoardIv    = findViewById(R.id.white_board_iv);
        messagePreviewTv        = findViewById(R.id.message_preview_tv);
        messagePreviewTimeTv    = findViewById(R.id.message_preview_time_tv);
        messagePreviewContainer = findViewById(R.id.message_preview_container);

        backBtn.setOnClickListener(view -> onBackPressed());

        chatIv.setOnClickListener(view -> openChat());

        documentsIv.setOnClickListener(view -> openDocuments());

        whiteBoardIv.setOnClickListener( view -> openWhiteBoard());

        // Set up click listeners
        startServerBtn.setOnClickListener(v -> {
            if (checkBluetoothPermissions()) {
                if (isBluetoothEnabled()) {
                    startBluetoothService();
                } else {
                    promptEnableBluetooth();
                }
            } else {
                requestBluetoothPermissions();
            }
        });

        stopServerBtn.setOnClickListener(v -> stopBluetoothService());

        leaveBtn.setOnClickListener(v -> leaveSession(true));

        micBtn.setOnClickListener(v -> {
            if (liveKitWrapper != null) {
                micMuted = !micMuted;
                liveKitWrapper.enableMicrophone(!micMuted);
                updateMicButton();
                updateBleServiceState();
                addLog(micMuted ? "Microphone muted" : "Microphone unmuted");
            }
        });

        cameraBtn.setOnClickListener(v -> {
            if (liveKitWrapper != null) {
                videoMuted = !videoMuted;
                liveKitWrapper.enableCamera(!videoMuted);
                updateCameraButton();
                updateBleServiceState();
                addLog(videoMuted ? "Camera disabled" : "Camera enabled");
            }
        });

        updateUIState(UIState.NOT_RUNNING);

        // Set default focus for Vuzix D-pad navigation
        backBtn.requestFocus();

        // Disable activity controls while any fragment is covering the screen.
        // This prevents focus leaking through the fragment overlay to buttons behind it.
        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            boolean fragmentActive =
                    getSupportFragmentManager().getBackStackEntryCount() > 0;
            setActivityControlsFocusable(!fragmentActive);
            if (!fragmentActive) {
                // Restore focus to the visible action button
                if (stopServerBtn.getVisibility() == View.VISIBLE) {
                    stopServerBtn.requestFocus();
                } else {
                    startServerBtn.requestFocus();
                }
            }
        });
    }

    // -------------------- UI STATE MANAGEMENT --------------------

    private void updateUIState(UIState state) {
        currentState = state;

        switch (state) {
            case NOT_RUNNING:
                statusTv.setText("Status: Not Running");
                statusTv.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark));
                linkCodeTv.setVisibility(View.GONE);
                startServerBtn.setVisibility(View.VISIBLE);
                stopServerBtn.setVisibility(View.GONE);
                callControlsLayout.setVisibility(View.GONE);
                chatIv.setVisibility(View.GONE);
                documentsIv.setVisibility(View.GONE);
                whiteBoardIv.setVisibility(View.GONE);
                // Focus chain: Back <-> Start Server (Stop Server is GONE)
                backBtn.setNextFocusDownId(R.id.start_server_btn);
                backBtn.setNextFocusUpId(R.id.start_server_btn);
                startServerBtn.setNextFocusDownId(R.id.back_btn);
                startServerBtn.setNextFocusUpId(R.id.back_btn);
                startServerBtn.requestFocus();
                break;

            case SERVER_RUNNING:
                statusTv.setText(R.string.status_waiting_join);
                statusTv.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
                linkCodeTv.setVisibility(View.GONE);
                startServerBtn.setVisibility(View.GONE);
                stopServerBtn.setVisibility(View.VISIBLE);
                callControlsLayout.setVisibility(View.GONE);
                chatIv.setVisibility(View.GONE);
                documentsIv.setVisibility(View.GONE);
                whiteBoardIv.setVisibility(View.GONE);
                // Focus chain: Back <-> Stop Server (Start Server is GONE)
                backBtn.setNextFocusDownId(R.id.stop_server_btn);
                backBtn.setNextFocusUpId(R.id.stop_server_btn);
                stopServerBtn.setNextFocusDownId(R.id.back_btn);
                stopServerBtn.setNextFocusUpId(R.id.back_btn);
                stopServerBtn.requestFocus();
                if (bleServiceBound && bleService != null) {
                    bleService.updateNotification("Waiting for connection...");
                }
                break;

            case JOINING:
                // Status text will be updated by individual phases
                statusTv.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark));
                linkCodeTv.setVisibility(View.VISIBLE);
                linkCodeTv.setText(getString(R.string.link_code_display, linkCode));
                startServerBtn.setVisibility(View.GONE);
                stopServerBtn.setVisibility(View.VISIBLE);
                callControlsLayout.setVisibility(View.GONE);
                chatIv.setVisibility(View.GONE);
                documentsIv.setVisibility(View.GONE);
                whiteBoardIv.setVisibility(View.GONE);
                // Focus chain: Back <-> Stop Server
                backBtn.setNextFocusDownId(R.id.stop_server_btn);
                backBtn.setNextFocusUpId(R.id.stop_server_btn);
                stopServerBtn.setNextFocusDownId(R.id.back_btn);
                stopServerBtn.setNextFocusUpId(R.id.back_btn);
                stopServerBtn.requestFocus();
                if (bleServiceBound && bleService != null) {
                    bleService.updateNotification("Joining call...");
                }
                break;

            case IN_CALL:
                statusTv.setText(R.string.status_in_call);
                statusTv.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
                linkCodeTv.setVisibility(View.VISIBLE);
                linkCodeTv.setText(getString(R.string.link_code_display, linkCode));
                startServerBtn.setVisibility(View.GONE);
                stopServerBtn.setVisibility(View.VISIBLE);
//                callControlsLayout.setVisibility(View.VISIBLE);
                updateMicButton();
                updateCameraButton();
                chatIv.setVisibility(View.VISIBLE);
                documentsIv.setVisibility(View.VISIBLE);
                whiteBoardIv.setVisibility(View.VISIBLE);
                // Focus chain: Back <-> Stop Server
                backBtn.setNextFocusDownId(R.id.stop_server_btn);
                backBtn.setNextFocusUpId(R.id.stop_server_btn);
                stopServerBtn.setNextFocusDownId(R.id.back_btn);
                stopServerBtn.setNextFocusUpId(R.id.back_btn);
                stopServerBtn.requestFocus();
                if (bleServiceBound && bleService != null) {
                    bleService.updateNotification("In Call");
                }
                break;
        }
    }

    private void updateMicButton() {
        micBtn.setText(micMuted ? R.string.mic_off : R.string.mic_on);
    }

    private void updateCameraButton() {
        cameraBtn.setText(videoMuted ? R.string.camera_off : R.string.camera_on);
    }

    // -------------------- LIVEKIT CONNECTION PHASES --------------------

    // Phase 1: Validate link code
    private void startPhase1() {
        updateUIState(UIState.JOINING);
        statusTv.setText(R.string.status_validating);
        AppLogger.i(TAG, "Phase 1: Validating link code " + linkCode);

        ArGenieApp.userName = this.userName;

        RemoteCallApi.validateLinkCode(linkCode, ArGenieApp.userId, null,
                new RemoteCallApi.RemoteApiCallbacks() {
                    @Override
                    public void onSuccess(JSONObject responseBody) {
                        runOnUiThread(() -> {
                            AppLogger.i(TAG, "Phase 1 complete: Link code validated");
                            addLog("Link code validated");
                            startPhase2();
                        });
                    }

                    @Override
                    public void onFailure(String message) {
                        runOnUiThread(() -> {
                            AppLogger.e(TAG, "Phase 1 failed: " + message);
                            statusTv.setText(getString(R.string.status_error, message));
                            addLog("Error: " + message);
                            updateUIState(UIState.SERVER_RUNNING);
                            sendBleCommandResult(BleProtocol.CMD_ROOM_JOINED, false);
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
                    addLog("Connecting to MQTT...");
                });
                connectMqtt();
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    AppLogger.e(TAG, "Phase 2 failed (user ID): " + error);
                    statusTv.setText(getString(R.string.status_error, error));
                    addLog("Error: " + error);
                    updateUIState(UIState.SERVER_RUNNING);
                    sendBleCommandResult(BleProtocol.CMD_ROOM_JOINED, false);
                });
            }
        });
    }

    private void connectMqtt() {
        ArGenieApp.getInstance().startMqtt(new ArGenieApp.MqttConnectionCallback() {
            @Override
            public void onMqttConnected() {
                AppLogger.i(TAG, "Phase 2 complete: MQTT connected");
                MqttWebRTC.messageCallbacks = MainActivity.this;
                ArGenieApp.getInstance().getMqttWebRTC().initializeAllCommonListeners();

                runOnUiThread(() -> addLog("MQTT connected"));
                startPhase3();
            }

            @Override
            public void onMqttConnectionFailure(String error) {
                runOnUiThread(() -> {
                    AppLogger.e(TAG, "Phase 2 failed (MQTT): " + error);
                    statusTv.setText(getString(R.string.status_error, error));
                    addLog("MQTT error: " + error);
                    updateUIState(UIState.SERVER_RUNNING);
                    sendBleCommandResult(BleProtocol.CMD_ROOM_JOINED, false);
                });
            }
        });
    }

    // Phase 3: Join room
    private void startPhase3() {
        runOnUiThread(() -> statusTv.setText(R.string.status_joining_room));
        AppLogger.i(TAG, "Phase 3: Joining room");

        RemoteCallApi.joinRoomApi(linkCode, userId, hostCompanyId, new RemoteCallApi.RemoteApiCallbacks() {
            @Override
            public void onSuccess(JSONObject responseBody) {
                runOnUiThread(() -> {
                    try {
                        if (responseBody != null && !responseBody.getBoolean("success")) {
                            String message = responseBody.getString("message");
                            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                            addLog("Join failed: " + message);
                            sendBleCommandResult(BleProtocol.CMD_ROOM_JOINED, false);
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
                    addLog("Joined room");
                    startPhase4();
                });
            }

            @Override
            public void onFailure(String message) {
                runOnUiThread(() -> {
                    AppLogger.e(TAG, "Phase 3 failed: " + message);
                    statusTv.setText(getString(R.string.status_error, message));
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                    addLog("Error: " + message);
                    updateUIState(UIState.SERVER_RUNNING);
                    sendBleCommandResult(BleProtocol.CMD_ROOM_JOINED, false);
                });
            }
        });
    }

    // Phase 4: Generate LiveKit token and connect to LiveKit room
    private void startPhase4() {
        statusTv.setText(R.string.status_connecting_livekit);
        AppLogger.i(TAG, "Phase 4: Generating LiveKit token and connecting");
        addLog("Connecting to LiveKit...");

        liveKitWrapper = new LiveKitWrapper(getApplicationContext());
        liveKitWrapper.setConnectionCallback(new LiveKitWrapper.ConnectionCallback() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> {
                    AppLogger.i(TAG, "Phase 4 complete: LiveKit connected, streaming video");
                    addLog("LiveKit connected - In call");

                    // Initialize state - camera is on by default, mic starts unmuted
                    micMuted = false;
                    videoMuted = false;

                    updateUIState(UIState.IN_CALL);
                    updateBleServiceState();

                    // Notify controller that glasses have joined the room
                    sendBleCommandResult(BleProtocol.CMD_ROOM_JOINED, true);
                });
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    AppLogger.e(TAG, "Phase 4 failed: " + error);
                    statusTv.setText(getString(R.string.status_error, error));
                    addLog("LiveKit error: " + error);
                    updateUIState(UIState.SERVER_RUNNING);

                    // Send deferred JOIN_ROOM failure response to controller
                    sendBleCommandResult(BleProtocol.CMD_ROOM_JOINED, false);
                });
            }
        });
        liveKitWrapper.start(linkCode);
    }

    // -------------------- LEAVE SESSION --------------------

    private void leaveSession(boolean hangup) {
        AppLogger.i(TAG, "Leaving session, hangup=" + hangup);
        addLog("Leaving session...");
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

        // Clear session state so the next join starts fresh
        ArGenieApp.clearSession();

        chatMessages.clear();
        chatFragment = null;
        documentsFragment = null;

        linkCode = null;
        micMuted = false;
        videoMuted = false;

        addLog("Session ended");
        updateUIState(UIState.SERVER_RUNNING);
        updateBleServiceState();

        // Send deferred LEAVE_ROOM success response to controller
        sendBleCommandResult(BleProtocol.CMD_LEAVE_ROOM, true);
    }

    // -------------------- BLE SERVICE STATE --------------------

    private void updateBleServiceState() {
        if (bleServiceBound && bleService != null) {
            bleService.setMicMuted(micMuted);
            bleService.setVideoMuted(videoMuted);
            bleService.setInRoom(currentState == UIState.IN_CALL);
        }
    }

    private void sendBleCommandResult(byte command, boolean success) {
        if (bleServiceBound && bleService != null) {
            bleService.sendDeferredCommandResult(command, success);
        }
    }

    // -------------------- BLUETOOTH SERVICE --------------------

    private void startBluetoothService() {
        Intent serviceIntent = new Intent(this, BleGattServerService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent);
        } else {
            startService(serviceIntent);
        }

        // Bind to service to get callbacks
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        updateUIState(UIState.SERVER_RUNNING);

        addLog("BLE GATT Server started (binary protocol)");
        addLog("Advertising...");
        addLog("Waiting for Controller to connect...");

        Toast.makeText(this, "Bluetooth Server Started", Toast.LENGTH_SHORT).show();

        demoStart();
    }

    private void demoStart(){
        runOnUiThread(() -> {
            if (currentState == UIState.IN_CALL) {
                addLog("JOIN_ROOM received while already in call, ignoring");
                return;
            }

            addLog("JOIN_ROOM: linkCode=" + linkCode + ", userName=" + userName);
            MainActivity.this.linkCode = "929-845-519";
            MainActivity.this.userName = "VUZIX";

            // Request camera + audio permissions then start connection flow
            boolean hasCameraPermission = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED;
            boolean hasAudioPermission = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;

            if (hasCameraPermission && hasAudioPermission) {
                startPhase1();
            } else {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                        REQUEST_CALL_PERMISSIONS);
            }
        });
    }

    private void stopBluetoothService() {
        addLog("Stopping BLE server...");

        // Leave session if in call
        if (currentState == UIState.IN_CALL) {
            leaveSession(true);
        }

        // Unbind from service
        if (bleServiceBound) {
            if (bleService != null) {
                bleService.setCommandListener(null);
                bleService.setConnectionListener(null);
            }
            unbindService(serviceConnection);
            bleServiceBound = false;
            bleService = null;
        }

        // Stop the service
        Intent serviceIntent = new Intent(this, BleGattServerService.class);
        stopService(serviceIntent);

        updateUIState(UIState.NOT_RUNNING);
        addLog("BLE server stopped");
        Toast.makeText(this, "Bluetooth Server Stopped", Toast.LENGTH_SHORT).show();
    }

    private void addLog(String message) {
        messageLog.append(message).append("\n");
        messageLogTv.setText(messageLog.toString());
        messageScrollView.post(() -> messageScrollView.scrollTo(0, messageLogTv.getBottom()));
    }

    private void openChat() {
        chatFragment = new ChatFragment(chatMessages);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, chatFragment)
                .addToBackStack(null)
                .commit();
    }

    private void openDocuments() {
        documentsFragment = new DocumentsFragment(chatMessages);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, documentsFragment)
                .addToBackStack(null)
                .commit();
    }

    private void openWhiteBoard() {
        String domain = ArGenieApp.getInstance().getConfig().getWhiteBoardUrl();
        String key = ArGenieApp.getInstance().getConfig().getWhiteBoardKey();
        String roomName = videoSessionId;

        String url = domain + roomName + "," + key + "," + userName;
        AppLogger.i(TAG, "openWhiteBoard url=" + url
                + " | videoSessionId=" + videoSessionId
                + " | chatSessionId=" + chatSessionId
                + " | meetingId=" + ArGenieApp.currentMeetingId);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new WhiteBoardFragment(url))
                .addToBackStack(null)
                .commit();
    }

    // -------------------- MQTT MESSAGE CALLBACKS --------------------

    @Override
    public void addMessageCallback(String sessionId, String companyId, ByteBuffer byteBuffer) {
        String json = StandardCharsets.UTF_8.decode(byteBuffer).toString();
        ChatMessage chatMessage = ChatMessage.fromJsonString(json);
        if (chatMessage == null) return;
        runOnUiThread(() -> {
            chatMessages.add(chatMessage);
            if (chatFragment != null && chatFragment.isAdded()) {
                chatFragment.addMessage(chatMessage);
            }
            if (!chatMessage.isTextMessage() &&
                    documentsFragment != null && documentsFragment.isAdded()) {
                documentsFragment.addDocument(chatMessage);
            }
            // Show temporary preview on main screen when in a call
            if (currentState == UIState.IN_CALL && chatMessage.isTextMessage()) {
                showMessagePreview(chatMessage.getMessage());
            }
        });
    }

    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("h:mm a", Locale.getDefault());

    private void showMessagePreview(String text) {
        messagePreviewTv.setText(text);
        messagePreviewTimeTv.setText(TIME_FORMAT.format(new Date()));
        // Cancel any running fade-out before showing again
        messagePreviewContainer.animate().cancel();
        messagePreviewContainer.setAlpha(0f);
        messagePreviewContainer.setVisibility(View.VISIBLE);
        messagePreviewContainer.animate()
                .alpha(1f)
                .setDuration(FADE_DURATION_MS)
                .setListener(null)
                .start();
        previewHandler.removeCallbacks(hidePreviewRunnable);
        previewHandler.postDelayed(hidePreviewRunnable, MESSAGE_PREVIEW_DURATION_MS);
    }

    // -------------------- PERMISSIONS --------------------

    @android.annotation.SuppressLint("MissingPermission")
    private boolean isBluetoothEnabled() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) return false;
        BluetoothAdapter adapter = bluetoothManager.getAdapter();
        return adapter != null && adapter.isEnabled();
    }

    private void promptEnableBluetooth() {
        new AlertDialog.Builder(this)
                .setTitle("Bluetooth Required")
                .setMessage("Bluetooth is turned off. Please enable Bluetooth to start the server.")
                .setPositiveButton("Enable", (dialog, which) -> {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivity(enableBtIntent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private boolean checkBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED;
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_ADVERTISE
                    },
                    REQUEST_PERMISSIONS);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = grantResults.length > 0;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                if (isBluetoothEnabled()) {
                    startBluetoothService();
                } else {
                    promptEnableBluetooth();
                }
            } else {
                Toast.makeText(this, "Permissions required for Bluetooth functionality", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_CALL_PERMISSIONS) {
            boolean allGranted = grantResults.length > 0;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                startPhase1();
            } else {
                Toast.makeText(this, "Camera and microphone permissions are required for calls", Toast.LENGTH_LONG).show();
                addLog("Call permissions denied");
                updateUIState(UIState.SERVER_RUNNING);
            }
        }
    }

    /**
     * Enables or disables focusability on all activity-level interactive controls.
     * Called by the back stack listener so that views behind a fragment overlay
     * can never receive D-pad focus while the fragment is visible.
     */
    private void setActivityControlsFocusable(boolean focusable) {
        backBtn.setFocusable(focusable);
        startServerBtn.setFocusable(focusable);
        stopServerBtn.setFocusable(focusable);
        chatIv.setFocusable(focusable);
        documentsIv.setFocusable(focusable);
        whiteBoardIv.setFocusable(focusable);
        leaveBtn.setFocusable(focusable);
        micBtn.setFocusable(focusable);
        cameraBtn.setFocusable(focusable);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            androidx.fragment.app.Fragment current =
                    getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (current instanceof DocumentsFragment) {
                if (((DocumentsFragment) current).handleNavKey(event.getKeyCode())) {
                    return true;
                }
            }
            if (current instanceof DocumentViewerFragment) {
                if (((DocumentViewerFragment) current).handleNavKey(event.getKeyCode())) {
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        previewHandler.removeCallbacks(hidePreviewRunnable);

        // Leave session if still in call
        if (currentState == UIState.IN_CALL) {
            leaveSession(true);
        }

        // Unbind from BLE service
        if (bleServiceBound) {
            if (bleService != null) {
                bleService.setCommandListener(null);
                bleService.setConnectionListener(null);
            }
            unbindService(serviceConnection);
            bleServiceBound = false;
        }

        // Stop the service so it doesn't keep running after the activity is destroyed
        Intent serviceIntent = new Intent(this, BleGattServerService.class);
        stopService(serviceIntent);
    }
}
