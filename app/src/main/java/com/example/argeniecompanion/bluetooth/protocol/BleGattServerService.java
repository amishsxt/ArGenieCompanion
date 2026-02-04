package com.example.argeniecompanion.bluetooth.protocol;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.argeniecompanion.ui.MainActivity;

/**
 * Android Service that wraps BleGattServer for background operation.
 *
 * This service:
 * - Runs as a foreground service (required for BLE operations)
 * - Manages the BleGattServer lifecycle
 * - Provides binding for Activity communication
 * - Handles command callbacks and forwards them to bound clients
 *
 * Usage in AndroidManifest.xml:
 * <service
 *     android:name=".bluetooth.protocol.BleGattServerService"
 *     android:enabled="true"
 *     android:exported="false"
 *     android:foregroundServiceType="connectedDevice" />
 *
 * Starting the service:
 *     Intent intent = new Intent(context, BleGattServerService.class);
 *     ContextCompat.startForegroundService(context, intent);
 *
 * Binding to get callbacks:
 *     bindService(intent, connection, Context.BIND_AUTO_CREATE);
 */
public class BleGattServerService extends Service {

    private static final String TAG = "BleGattServerService";
    private static final String CHANNEL_ID = "BleGattServer_Channel";
    private static final int NOTIFICATION_ID = 2;

    private BleGattServer gattServer;
    private final LocalBinder binder = new LocalBinder();

    // Callback for bound clients
    private BleCommandListener commandListener;
    private BleGattServer.ConnectionListener connectionListener;

    /**
     * Binder for local (same process) clients.
     */
    public class LocalBinder extends Binder {
        public BleGattServerService getService() {
            return BleGattServerService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");

        gattServer = new BleGattServer(this);

        // Set up internal command listener that forwards to registered listener
        gattServer.setCommandListener(new BleCommandListener() {
            @Override
            public boolean onJoinRoom(String linkCode, String userName) {
                Log.i(TAG, "JOIN_ROOM: linkCode=" + linkCode + ", userName=" + userName);
                if (commandListener != null) {
                    return commandListener.onJoinRoom(linkCode, userName);
                }
                return true;
            }

            @Override
            public void onLeaveRoom() {
                Log.i(TAG, "LEAVE_ROOM");
                if (commandListener != null) {
                    commandListener.onLeaveRoom();
                }
            }

            @Override
            public void onMicMute() {
                Log.i(TAG, "MIC_MUTE");
                if (commandListener != null) {
                    commandListener.onMicMute();
                }
            }

            @Override
            public void onMicUnmute() {
                Log.i(TAG, "MIC_UNMUTE");
                if (commandListener != null) {
                    commandListener.onMicUnmute();
                }
            }

            @Override
            public void onVideoMute() {
                Log.i(TAG, "VIDEO_MUTE");
                if (commandListener != null) {
                    commandListener.onVideoMute();
                }
            }

            @Override
            public void onVideoUnmute() {
                Log.i(TAG, "VIDEO_UNMUTE");
                if (commandListener != null) {
                    commandListener.onVideoUnmute();
                }
            }
        });

        // Forward connection events
        gattServer.setConnectionListener(new BleGattServer.ConnectionListener() {
            @Override
            public void onDeviceConnected(String deviceName, String deviceAddress) {
                Log.i(TAG, "Device connected: " + deviceName);
                updateNotification("Connected to " + (deviceName != null ? deviceName : "device"));
                if (connectionListener != null) {
                    connectionListener.onDeviceConnected(deviceName, deviceAddress);
                }
            }

            @Override
            public void onDeviceDisconnected() {
                Log.i(TAG, "Device disconnected");
                updateNotification("Waiting for connection...");
                if (connectionListener != null) {
                    connectionListener.onDeviceDisconnected();
                }
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");

        createNotificationChannel();
        showForegroundNotification();

        if (!gattServer.isRunning()) {
            boolean started = gattServer.start();
            if (!started) {
                Log.e(TAG, "Failed to start GATT server");
                stopSelf();
                return START_NOT_STICKY;
            }
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");

        if (gattServer != null) {
            gattServer.stop();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // ==================== Public Methods for Bound Clients ====================

    /**
     * Set the command listener to receive command callbacks.
     */
    public void setCommandListener(BleCommandListener listener) {
        this.commandListener = listener;
    }

    /**
     * Set the connection listener to receive connection state callbacks.
     */
    public void setConnectionListener(BleGattServer.ConnectionListener listener) {
        this.connectionListener = listener;
    }

    /**
     * Update the mic muted state for GET_STATUS responses.
     */
    public void setMicMuted(boolean muted) {
        if (gattServer != null) {
            gattServer.setMicMuted(muted);
        }
    }

    /**
     * Update the video muted state for GET_STATUS responses.
     */
    public void setVideoMuted(boolean muted) {
        if (gattServer != null) {
            gattServer.setVideoMuted(muted);
        }
    }

    /**
     * Update the in-room state for GET_STATUS responses.
     */
    public void setInRoom(boolean inRoom) {
        if (gattServer != null) {
            gattServer.setInRoom(inRoom);
        }
    }

    /**
     * Check if a device is connected.
     */
    public boolean isConnected() {
        return gattServer != null && gattServer.isConnected();
    }

    /**
     * Check if the GATT server is running.
     */
    public boolean isRunning() {
        return gattServer != null && gattServer.isRunning();
    }

    // ==================== Notification Methods ====================

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "BLE GATT Server",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows when BLE server is running");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void showForegroundNotification() {
        Notification notification = buildNotification("Waiting for connection...");
        startForeground(NOTIFICATION_ID, notification);
    }

    private void updateNotification(String text) {
        Notification notification = buildNotification(text);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification(String contentText) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("ArGenie BLE Server")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
}
