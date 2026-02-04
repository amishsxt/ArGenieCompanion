package com.example.argeniecompanion.ui;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.argeniecompanion.R;
import com.example.argeniecompanion.bluetooth.protocol.BleCommandListener;
import com.example.argeniecompanion.bluetooth.protocol.BleGattServer;
import com.example.argeniecompanion.bluetooth.protocol.BleGattServerService;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_PERMISSIONS = 101;
    public static final String ACTION_LEAVE = "com.example.argeniecompanion.ACTION_LEAVE";
    public static final String EXTRA_LINK_CODE = "linkCode";

    private Button startServerBtn;
    private Button stopServerBtn;
    private TextView statusTv;
    private TextView messageLogTv;
    private ScrollView messageScrollView;

    private StringBuilder messageLog = new StringBuilder();

    // BLE Service binding
    private BleGattServerService bleService;
    private boolean bleServiceBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BleGattServerService.LocalBinder binder = (BleGattServerService.LocalBinder) service;
            bleService = binder.getService();
            bleServiceBound = true;

            // Set command listener for JOIN/LEAVE when not in a call
            bleService.setCommandListener(commandListener);

            // Set connection listener
            bleService.setConnectionListener(connectionListener);

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
                statusTv.setText("Status: Connected to " + (deviceName != null ? deviceName : "device"));
                addLog("Connected to: " + deviceName + " [" + deviceAddress + "]");
            });
        }

        @Override
        public void onDeviceDisconnected() {
            runOnUiThread(() -> {
                statusTv.setText("Status: Disconnected - Waiting for connection...");
                addLog("Disconnected");
            });
        }
    };

    private final BleCommandListener commandListener = new BleCommandListener() {
        @Override
        public boolean onJoinRoom(String linkCode, String userName) {
            runOnUiThread(() -> {
                addLog("JOIN_ROOM: linkCode=" + linkCode + ", userName=" + userName);
                Intent intent = new Intent(MainActivity.this, LiveKitCallActivity.class);
                intent.putExtra(EXTRA_LINK_CODE, linkCode);
                startActivity(intent);
            });
            return true;
        }

        @Override
        public void onLeaveRoom() {
            runOnUiThread(() -> {
                addLog("LEAVE_ROOM received");
                Intent leaveIntent = new Intent(ACTION_LEAVE);
                LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast(leaveIntent);
            });
        }

        @Override
        public void onMicMute() {
            runOnUiThread(() -> addLog("MIC_MUTE (not in call, ignored)"));
        }

        @Override
        public void onMicUnmute() {
            runOnUiThread(() -> addLog("MIC_UNMUTE (not in call, ignored)"));
        }

        @Override
        public void onVideoMute() {
            runOnUiThread(() -> addLog("VIDEO_MUTE (not in call, ignored)"));
        }

        @Override
        public void onVideoUnmute() {
            runOnUiThread(() -> addLog("VIDEO_UNMUTE (not in call, ignored)"));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startServerBtn = findViewById(R.id.start_server_btn);
        stopServerBtn = findViewById(R.id.stop_server_btn);
        statusTv = findViewById(R.id.status_tv);
        messageLogTv = findViewById(R.id.message_log_tv);
        messageScrollView = findViewById(R.id.message_scroll_view);

        stopServerBtn.setEnabled(false);

        startServerBtn.setOnClickListener(v -> {
            if (checkBluetoothPermissions()) {
                startBluetoothService();
            } else {
                requestBluetoothPermissions();
            }
        });

        stopServerBtn.setOnClickListener(v -> stopBluetoothService());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-register listener when returning from LiveKitCallActivity
        // (LiveKitCallActivity clears the listener when it's destroyed)
        if (bleServiceBound && bleService != null) {
            bleService.setCommandListener(commandListener);
            bleService.setConnectionListener(connectionListener);
            addLog("Listener re-registered");
        }
    }

    private void startBluetoothService() {
        Intent serviceIntent = new Intent(this, BleGattServerService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent);
        } else {
            startService(serviceIntent);
        }

        // Bind to service to get callbacks
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        statusTv.setText("Status: Server Running (Listening...)");
        startServerBtn.setEnabled(false);
        stopServerBtn.setEnabled(true);

        addLog("BLE GATT Server started (binary protocol)");
        addLog("Advertising...");
        addLog("Waiting for Controller to connect...");

        Toast.makeText(this, "Bluetooth Server Started", Toast.LENGTH_SHORT).show();
    }

    private void stopBluetoothService() {
        // Unbind first
        if (bleServiceBound) {
            if (bleService != null) {
                bleService.setCommandListener(null);
                bleService.setConnectionListener(null);
            }
            unbindService(serviceConnection);
            bleServiceBound = false;
            bleService = null;
        }

        // Stop service
        Intent serviceIntent = new Intent(this, BleGattServerService.class);
        stopService(serviceIntent);

        statusTv.setText("Status: Server Stopped");
        startServerBtn.setEnabled(true);
        stopServerBtn.setEnabled(false);

        addLog("Server stopped");

        Toast.makeText(this, "Bluetooth Server Stopped", Toast.LENGTH_SHORT).show();
    }

    private void addLog(String message) {
        messageLog.append(message).append("\n");
        messageLogTv.setText(messageLog.toString());
        messageScrollView.post(() -> messageScrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    // -------------------- PERMISSIONS --------------------

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
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startBluetoothService();
            } else {
                Toast.makeText(this, "Permissions required for Bluetooth functionality", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bleServiceBound) {
            if (bleService != null) {
                bleService.setCommandListener(null);
                bleService.setConnectionListener(null);
            }
            unbindService(serviceConnection);
            bleServiceBound = false;
        }
    }
}
