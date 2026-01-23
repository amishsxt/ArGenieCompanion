package com.example.argeniecompanion.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import java.nio.charset.StandardCharsets;

/**
 * BLE GATT Server for Device B (Companion/Glasses)
 * Receives messages from Device A and can send responses back
 */
@SuppressLint("MissingPermission")
public class BluetoothHelper {

    private static final String TAG = "ArGenie_BT_Helper";

    private static BluetoothGattServer gattServer;
    private static BluetoothLeAdvertiser advertiser;
    private static BluetoothGattCharacteristic writeCharacteristic;
    private static BluetoothGattCharacteristic readCharacteristic;
    private static BluetoothDevice connectedDevice;
    private static MessageListener messageListener;

    /**
     * Starts the BLE GATT Server and advertising
     */
    public static void startServerMode(Context context) {
        stopServerMode();

        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth not available or disabled.");
            return;
        }

        // Create GATT Server
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback);
        if (gattServer == null) {
            Log.e(TAG, "Failed to create GATT server");
            return;
        }

        // Setup service and characteristics
        setupGattService();

        // Start advertising
        startAdvertising(bluetoothAdapter);

        Log.d(TAG, "GATT Server started and advertising...");
    }

    /**
     * Setup GATT service with characteristics
     * Must match the UUIDs used in Device A
     */
    private static void setupGattService() {
        BluetoothGattService service = new BluetoothGattService(
                BluetoothConstants.SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
        );

        // Write Characteristic - Device A writes to this, Device B reads from it
        writeCharacteristic = new BluetoothGattCharacteristic(
                BluetoothConstants.CHARACTERISTIC_WRITE_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
        );

        // Read/Notify Characteristic - Device B writes to this, Device A reads from it
        readCharacteristic = new BluetoothGattCharacteristic(
                BluetoothConstants.CHARACTERISTIC_READ_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ
        );

        // Add Client Characteristic Configuration Descriptor for notifications
        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(
                BluetoothConstants.CLIENT_CHARACTERISTIC_CONFIG,
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE
        );
        readCharacteristic.addDescriptor(descriptor);

        service.addCharacteristic(writeCharacteristic);
        service.addCharacteristic(readCharacteristic);

        gattServer.addService(service);
        Log.d(TAG, "GATT Service added with characteristics");
    }

    /**
     * Start BLE advertising so Device A can discover this device
     */
    private static void startAdvertising(BluetoothAdapter adapter) {
        advertiser = adapter.getBluetoothLeAdvertiser();
        if (advertiser == null) {
            Log.e(TAG, "Advertiser not available");
            return;
        }

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(new ParcelUuid(BluetoothConstants.SERVICE_UUID))
                .build();

        advertiser.startAdvertising(settings, data, advertiseCallback);
    }

    /**
     * Stop GATT server and advertising
     */
    public static void stopServerMode() {
        if (advertiser != null) {
            try {
                advertiser.stopAdvertising(advertiseCallback);
            } catch (Exception e) {
                Log.e(TAG, "Error stopping advertising", e);
            }
            advertiser = null;
        }

        if (gattServer != null) {
            gattServer.close();
            gattServer = null;
        }

        connectedDevice = null;
        Log.d(TAG, "GATT Server stopped");
    }

    /**
     * Send a message/response back to Device A
     */
    public static boolean sendResponse(String message) {
        if (gattServer == null || connectedDevice == null || readCharacteristic == null) {
            Log.e(TAG, "Cannot send response: Not connected");
            return false;
        }

        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        readCharacteristic.setValue(data);

        boolean success = gattServer.notifyCharacteristicChanged(
                connectedDevice,
                readCharacteristic,
                false
        );

        if (success) {
            Log.d(TAG, "Response sent: " + message);
        } else {
            Log.e(TAG, "Failed to send response");
        }

        return success;
    }

    /**
     * Set listener for incoming messages
     */
    public static void setMessageListener(MessageListener listener) {
        messageListener = listener;
    }

    // ==================== CALLBACKS ====================

    private static final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.d(TAG, "BLE Advertising started successfully");
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.e(TAG, "BLE Advertising failed: " + errorCode);
        }
    };

    private static final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                connectedDevice = device;
                Log.i(TAG, "✅ CONNECTED to Device A: " + device.getName() + " [" + device.getAddress() + "]");

                if (messageListener != null) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            messageListener.onConnectionStateChanged(true, device.getName())
                    );
                }
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.i(TAG, "❌ DISCONNECTED from Device A");
                connectedDevice = null;

                if (messageListener != null) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            messageListener.onConnectionStateChanged(false, null)
                    );
                }
            }
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device,
                                                 int requestId,
                                                 BluetoothGattCharacteristic characteristic,
                                                 boolean preparedWrite,
                                                 boolean responseNeeded,
                                                 int offset,
                                                 byte[] value) {

            if (BluetoothConstants.CHARACTERISTIC_WRITE_UUID.equals(characteristic.getUuid())) {
                String message = new String(value, StandardCharsets.UTF_8);

                Log.d(TAG, "📩 MESSAGE RECEIVED from Device A: " + message);

                // Process the command
                handleIncomingCommand(message);

                // Notify listener
                if (messageListener != null) {
                    String finalMessage = message;
                    new Handler(Looper.getMainLooper()).post(() ->
                            messageListener.onMessageReceived(finalMessage)
                    );
                }

                // Send response if needed
                if (responseNeeded) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                }
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device,
                                                int requestId,
                                                int offset,
                                                BluetoothGattCharacteristic characteristic) {

            if (BluetoothConstants.CHARACTERISTIC_READ_UUID.equals(characteristic.getUuid())) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset,
                        characteristic.getValue());
            }
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device,
                                             int requestId,
                                             BluetoothGattDescriptor descriptor,
                                             boolean preparedWrite,
                                             boolean responseNeeded,
                                             int offset,
                                             byte[] value) {

            if (BluetoothConstants.CLIENT_CHARACTERISTIC_CONFIG.equals(descriptor.getUuid())) {
                Log.d(TAG, "Client subscribed to notifications");

                if (responseNeeded) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                }
            }
        }
    };

    /**
     * Handle incoming commands from Device A
     */
    private static void handleIncomingCommand(String command) {
        Log.i(TAG, "🔧 PROCESSING COMMAND: " + command);

        // Parse and handle different command types
        if (command.startsWith(BluetoothConstants.MESSAGE_TYPE_COMMAND + ":")) {
            String cmd = command.substring(command.indexOf(":") + 1);

            if (BluetoothConstants.CMD_PING.equals(cmd)) {
                sendResponse("PONG");
                Log.d(TAG, "Responded to PING with PONG");
            } else if (BluetoothConstants.CMD_GET_STATUS.equals(cmd)) {
                sendResponse("STATUS: Device B is ready");
                Log.d(TAG, "Sent status response");
            }
        } else {
            // Regular text message - echo it back
            sendResponse("Echo: " + command);
            Log.d(TAG, "Echoed message back to Device A");
        }
    }

    // ==================== LISTENER INTERFACE ====================

    public interface MessageListener {
        void onConnectionStateChanged(boolean connected, String deviceName);
        void onMessageReceived(String message);
    }
}