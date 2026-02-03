package com.example.argeniecompanion.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
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
import com.example.argeniecompanion.bluetooth.BluetoothConstants;
import com.example.argeniecompanion.bluetooth.BluetoothHelper;
import com.example.argeniecompanion.bluetooth.BluetoothServerService;

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
            handleMessage("COMMAND:JOIN:352-669-991");
//            if (checkBluetoothPermissions()) {
//                startBluetoothService();
//            } else {
//                requestBluetoothPermissions();
//            }
        });

        stopServerBtn.setOnClickListener(v -> stopBluetoothService());

        // Set message listener
        BluetoothHelper.setMessageListener(new BluetoothHelper.MessageListener() {
            @Override
            public void onConnectionStateChanged(boolean connected, String deviceName) {
                runOnUiThread(() -> {
                    if (connected) {
                        statusTv.setText("Status: Connected to " + deviceName);
                        addLog("Connected to: " + deviceName);
                    } else {
                        statusTv.setText("Status: Disconnected - Waiting for connection...");
                        addLog("Disconnected");
                    }
                });
            }

            @Override
            public void onMessageReceived(String message) {
                runOnUiThread(() -> handleMessage(message));
            }
        });
    }

    private void handleMessage(String message) {
        if (message != null && message.startsWith(BluetoothConstants.MESSAGE_TYPE_COMMAND + ":")) {
            String[] parts = message.split(":", 3);
            if (parts.length >= 2) {
                String action = parts[1];
                String linkCode = parts.length == 3 ? parts[2] : null;

                if (BluetoothConstants.CMD_JOIN.equals(action) && linkCode != null) {
                    addLog("Command: JOIN with code " + linkCode);
                    Intent intent = new Intent(this, LiveKitCallActivity.class);
                    intent.putExtra(EXTRA_LINK_CODE, linkCode);
                    startActivity(intent);
                } else if (BluetoothConstants.CMD_LEAVE.equals(action)) {
                    addLog("Command: LEAVE");
                    Intent leaveIntent = new Intent(ACTION_LEAVE);
                    if (linkCode != null) {
                        leaveIntent.putExtra(EXTRA_LINK_CODE, linkCode);
                    }
                    LocalBroadcastManager.getInstance(this).sendBroadcast(leaveIntent);
                } else {
                    addLog("Unknown command: " + message);
                }
            }
        } else {
            addLog("Received: " + message);
        }
    }

    private void startBluetoothService() {
        Intent serviceIntent = new Intent(this, BluetoothServerService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent);
        } else {
            startService(serviceIntent);
        }

        statusTv.setText("Status: Server Running (Listening...)");
        startServerBtn.setEnabled(false);
        stopServerBtn.setEnabled(true);

        addLog("BLE GATT Server started");
        addLog("Advertising...");
        addLog("Waiting for Device A to connect...");

        Toast.makeText(this, "Bluetooth Server Started", Toast.LENGTH_SHORT).show();
    }

    private void stopBluetoothService() {
        Intent serviceIntent = new Intent(this, BluetoothServerService.class);
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
        BluetoothHelper.setMessageListener(null);
    }
}
