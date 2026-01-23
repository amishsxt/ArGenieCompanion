package com.example.argeniecompanion.bluetooth;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.argeniecompanion.ui.MainActivity;

public class BluetoothServerService extends Service {

    private static final String TAG = "BT_ServerService";
    private static final String CHANNEL_ID = "ArGenie_Companion_Channel";
    private static final int NOTIFICATION_ID = 1;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service Created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service Started");

        // 1. Create and show the Foreground Notification (Required by Android)
        showNotification();

        // 2. Start the Bluetooth Server Listening logic
        // This method should be implemented in your BluetoothHelper
        BluetoothHelper.startServerMode(this);

        // START_STICKY tells Android to recreate the service if it's killed by memory pressure
        return START_STICKY;
    }

    /**
     * Creates and starts the foreground notification for the service.
     */
    private void showNotification() {
        createNotificationChannel();

        // Intent to open MainActivity when clicking the notification
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("ArGenie Companion Active")
                .setContentText("Listening for Bluetooth connection...")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth) // Use your app's icon here
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW) // Keeps it non-intrusive
                .build();

        // This call makes the service a "Foreground Service"
        startForeground(NOTIFICATION_ID, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "ArGenie Bluetooth Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service Destroyed");
        // Properly close sockets when the service is stopped
        BluetoothHelper.stopServerMode();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // We don't need to bind this service to an activity for now
        return null;
    }
}
