package com.example.argeniecompanion.bluetooth.protocol;

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
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

/**
 * BLE GATT Server implementation for receiving binary commands from a Controller app.
 *
 * This server:
 * - Advertises the service UUID for discovery
 * - Accepts write requests on the write characteristic
 * - Parses binary commands using the defined protocol
 * - Sends responses via notifications on the read characteristic
 * - Delegates command handling to a BleCommandListener
 *
 * Thread Safety: All callbacks are posted to the main thread.
 *
 * Usage:
 * 1. Create instance: BleGattServer server = new BleGattServer(context);
 * 2. Set listener: server.setCommandListener(listener);
 * 3. Start: server.start();
 * 4. Stop: server.stop();
 */
@SuppressLint("MissingPermission")
public class BleGattServer {

    private static final String TAG = "BleGattServer";

    private final Context context;
    private final Handler mainHandler;

    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGattServer gattServer;
    private BluetoothLeAdvertiser advertiser;
    private BluetoothGattCharacteristic writeCharacteristic;
    private BluetoothGattCharacteristic readCharacteristic;
    private BluetoothDevice connectedDevice;

    private BleCommandListener commandListener;
    private ConnectionListener connectionListener;

    // State tracking
    private boolean micMuted = false;
    private boolean videoMuted = false;
    private boolean inRoom = false;
    private boolean isRunning = false;

    /**
     * Listener for connection state changes.
     */
    public interface ConnectionListener {
        void onDeviceConnected(String deviceName, String deviceAddress);
        void onDeviceDisconnected();
    }

    /**
     * Create a new BLE GATT Server.
     *
     * @param context Application or Activity context
     */
    public BleGattServer(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Set the command listener to handle incoming commands.
     */
    public void setCommandListener(BleCommandListener listener) {
        this.commandListener = listener;
    }

    /**
     * Set the connection listener to receive connection state updates.
     */
    public void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
    }

    /**
     * Update the mic muted state (for GET_STATUS responses).
     */
    public void setMicMuted(boolean muted) {
        this.micMuted = muted;
    }

    /**
     * Update the video muted state (for GET_STATUS responses).
     */
    public void setVideoMuted(boolean muted) {
        this.videoMuted = muted;
    }

    /**
     * Update the in-room state (for GET_STATUS responses).
     */
    public void setInRoom(boolean inRoom) {
        this.inRoom = inRoom;
    }

    /**
     * Check if the server is currently running.
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Check if a device is currently connected.
     */
    public boolean isConnected() {
        return connectedDevice != null;
    }

    /**
     * Start the BLE GATT server and begin advertising.
     *
     * @return true if started successfully, false otherwise
     */
    public boolean start() {
        if (isRunning) {
            Log.w(TAG, "Server already running");
            return true;
        }

        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            Log.e(TAG, "BluetoothManager not available");
            return false;
        }

        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth not available or disabled");
            return false;
        }

        // Create GATT server
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback);
        if (gattServer == null) {
            Log.e(TAG, "Failed to create GATT server");
            return false;
        }

        // Setup service and characteristics
        if (!setupGattService()) {
            Log.e(TAG, "Failed to setup GATT service");
            stop();
            return false;
        }

        // Start advertising
        if (!startAdvertising()) {
            Log.e(TAG, "Failed to start advertising");
            stop();
            return false;
        }

        isRunning = true;
        Log.i(TAG, "BLE GATT Server started successfully");
        return true;
    }

    /**
     * Stop the BLE GATT server and advertising.
     */
    public void stop() {
        isRunning = false;

        // Stop advertising
        if (advertiser != null) {
            try {
                advertiser.stopAdvertising(advertiseCallback);
            } catch (Exception e) {
                Log.e(TAG, "Error stopping advertising", e);
            }
            advertiser = null;
        }

        // Close GATT server
        if (gattServer != null) {
            try {
                gattServer.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing GATT server", e);
            }
            gattServer = null;
        }

        connectedDevice = null;
        writeCharacteristic = null;
        readCharacteristic = null;

        Log.i(TAG, "BLE GATT Server stopped");
    }

    /**
     * Send a binary response to the connected device.
     *
     * @param response The response packet bytes
     * @return true if sent successfully, false otherwise
     */
    public boolean sendResponse(byte[] response) {
        if (gattServer == null || connectedDevice == null || readCharacteristic == null) {
            Log.e(TAG, "Cannot send response: Not connected");
            return false;
        }

        readCharacteristic.setValue(response);

        boolean success = gattServer.notifyCharacteristicChanged(
                connectedDevice,
                readCharacteristic,
                false
        );

        if (success) {
            Log.d(TAG, "Response sent: " + BleCommandParser.toHexString(response));
        } else {
            Log.e(TAG, "Failed to send response");
        }

        return success;
    }

    // ==================== Private Methods ====================

    /**
     * Setup the GATT service with write and read characteristics.
     */
    private boolean setupGattService() {
        BluetoothGattService service = new BluetoothGattService(
                BleProtocol.SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
        );

        // Write Characteristic - Controller writes commands here
        writeCharacteristic = new BluetoothGattCharacteristic(
                BleProtocol.WRITE_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE |
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
        );

        // Read/Notify Characteristic - Server sends responses here
        readCharacteristic = new BluetoothGattCharacteristic(
                BleProtocol.READ_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ |
                        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ
        );

        // Add CCCD for notifications
        BluetoothGattDescriptor cccd = new BluetoothGattDescriptor(
                BleProtocol.CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE
        );
        readCharacteristic.addDescriptor(cccd);

        service.addCharacteristic(writeCharacteristic);
        service.addCharacteristic(readCharacteristic);

        return gattServer.addService(service);
    }

    /**
     * Start BLE advertising.
     */
    private boolean startAdvertising() {
        advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        if (advertiser == null) {
            Log.e(TAG, "Advertiser not available");
            return false;
        }

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(new ParcelUuid(BleProtocol.SERVICE_UUID))
                .build();

        advertiser.startAdvertising(settings, data, advertiseCallback);
        return true;
    }

    /**
     * Get the current battery level.
     */
    private int getBatteryLevel() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, filter);

        if (batteryStatus == null) {
            return 50; // Default if unknown
        }

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        if (level < 0 || scale <= 0) {
            return 50; // Default if unknown
        }

        return (int) ((level / (float) scale) * 100);
    }

    /**
     * Process an incoming binary command.
     */
    private void processCommand(byte[] data) {
        Log.d(TAG, "Received data: " + BleCommandParser.toHexString(data));

        ParsedCommand parsed = BleCommandParser.parse(data);
        Log.d(TAG, "Parsed: " + parsed);

        if (!parsed.isValid()) {
            // Send error response
            byte[] errorResponse = BleResponseBuilder.buildAckResponse(
                    parsed.getCommand(),
                    parsed.getErrorStatus()
            );
            sendResponse(errorResponse);
            return;
        }

        // Handle the command
        byte responseStatus = executeCommand(parsed);

        // Send appropriate response
        byte[] response;
        if (parsed.getCommand() == BleProtocol.CMD_GET_STATUS) {
            response = BleResponseBuilder.buildStatusResponse(
                    responseStatus,
                    getBatteryLevel(),
                    micMuted,
                    videoMuted,
                    inRoom
            );
        } else if (parsed.getCommand() == BleProtocol.CMD_PING) {
            response = BleResponseBuilder.buildPongResponse();
        } else {
            response = BleResponseBuilder.buildAckResponse(
                    parsed.getCommand(),
                    responseStatus
            );
        }

        sendResponse(response);
    }

    /**
     * Execute a parsed command and return the status.
     */
    private byte executeCommand(ParsedCommand parsed) {
        byte command = parsed.getCommand();
        Log.i(TAG, "Executing command: " + BleProtocol.getCommandName(command));

        switch (command) {
            case BleProtocol.CMD_JOIN_ROOM:
                return handleJoinRoom(parsed);

            case BleProtocol.CMD_LEAVE_ROOM:
                return handleLeaveRoom();

            case BleProtocol.CMD_MIC_MUTE:
                return handleMicMute();

            case BleProtocol.CMD_MIC_UNMUTE:
                return handleMicUnmute();

            case BleProtocol.CMD_VIDEO_MUTE:
                return handleVideoMute();

            case BleProtocol.CMD_VIDEO_UNMUTE:
                return handleVideoUnmute();

            case BleProtocol.CMD_PING:
                // PING always succeeds
                return BleProtocol.STATUS_OK;

            case BleProtocol.CMD_GET_STATUS:
                // GET_STATUS always succeeds
                return BleProtocol.STATUS_OK;

            default:
                return BleProtocol.STATUS_INVALID_COMMAND;
        }
    }

    private byte handleJoinRoom(ParsedCommand parsed) {
        if (inRoom) {
            return BleProtocol.STATUS_ALREADY_IN_ROOM;
        }

        if (commandListener != null) {
            boolean success = commandListener.onJoinRoom(
                    parsed.getLinkCode(),
                    parsed.getUserName()
            );

            if (success) {
                inRoom = true;
                return BleProtocol.STATUS_OK;
            } else {
                return BleProtocol.STATUS_ERROR;
            }
        }

        return BleProtocol.STATUS_OK;
    }

    private byte handleLeaveRoom() {
        if (!inRoom) {
            return BleProtocol.STATUS_NOT_CONNECTED;
        }

        if (commandListener != null) {
            commandListener.onLeaveRoom();
        }

        inRoom = false;
        return BleProtocol.STATUS_OK;
    }

    private byte handleMicMute() {
        if (commandListener != null) {
            commandListener.onMicMute();
        }
        micMuted = true;
        return BleProtocol.STATUS_OK;
    }

    private byte handleMicUnmute() {
        if (commandListener != null) {
            commandListener.onMicUnmute();
        }
        micMuted = false;
        return BleProtocol.STATUS_OK;
    }

    private byte handleVideoMute() {
        if (commandListener != null) {
            commandListener.onVideoMute();
        }
        videoMuted = true;
        return BleProtocol.STATUS_OK;
    }

    private byte handleVideoUnmute() {
        if (commandListener != null) {
            commandListener.onVideoUnmute();
        }
        videoMuted = false;
        return BleProtocol.STATUS_OK;
    }

    // ==================== Callbacks ====================

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.i(TAG, "BLE advertising started successfully");
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.e(TAG, "BLE advertising failed: " + getAdvertiseErrorString(errorCode));
            isRunning = false;
        }

        private String getAdvertiseErrorString(int errorCode) {
            switch (errorCode) {
                case ADVERTISE_FAILED_DATA_TOO_LARGE:
                    return "DATA_TOO_LARGE";
                case ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                    return "TOO_MANY_ADVERTISERS";
                case ADVERTISE_FAILED_ALREADY_STARTED:
                    return "ALREADY_STARTED";
                case ADVERTISE_FAILED_INTERNAL_ERROR:
                    return "INTERNAL_ERROR";
                case ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                    return "FEATURE_UNSUPPORTED";
                default:
                    return "UNKNOWN(" + errorCode + ")";
            }
        }
    };

    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                connectedDevice = device;
                String deviceName = device.getName();
                String deviceAddress = device.getAddress();

                Log.i(TAG, "Device connected: " + deviceName + " [" + deviceAddress + "]");

                if (connectionListener != null) {
                    mainHandler.post(() ->
                            connectionListener.onDeviceConnected(deviceName, deviceAddress)
                    );
                }

            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.i(TAG, "Device disconnected");
                connectedDevice = null;

                if (connectionListener != null) {
                    mainHandler.post(() -> connectionListener.onDeviceDisconnected());
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

            if (BleProtocol.WRITE_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                // Process the command on the main thread
                mainHandler.post(() -> processCommand(value));

                // Send GATT response if needed
                if (responseNeeded) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                }
            } else {
                if (responseNeeded) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null);
                }
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device,
                                                int requestId,
                                                int offset,
                                                BluetoothGattCharacteristic characteristic) {

            if (BleProtocol.READ_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                byte[] value = characteristic.getValue();
                if (value == null) {
                    value = new byte[0];
                }
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
            } else {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null);
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

            if (BleProtocol.CCCD_UUID.equals(descriptor.getUuid())) {
                if (java.util.Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                    Log.d(TAG, "Notifications enabled by client");
                } else if (java.util.Arrays.equals(value, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                    Log.d(TAG, "Notifications disabled by client");
                }

                if (responseNeeded) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                }
            } else {
                if (responseNeeded) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null);
                }
            }
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device,
                                            int requestId,
                                            int offset,
                                            BluetoothGattDescriptor descriptor) {

            if (BleProtocol.CCCD_UUID.equals(descriptor.getUuid())) {
                byte[] value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
            } else {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null);
            }
        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            Log.d(TAG, "MTU changed to: " + mtu);
        }
    };
}
