package com.example.argeniecompanion.bluetooth;

import java.util.UUID;

/**
 * Shared constants for Bluetooth communication between Device A (client) and Device B (server)
 * Both apps must use the same UUIDs for successful communication
 */
public class BluetoothConstants {

    /**
     * Service UUID - Main GATT service that Device B will advertise
     * Device A will look for this service when connecting
     */
    public static final UUID SERVICE_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");

    /**
     * Write Characteristic UUID - Used to send messages from Device A to Device B
     * Device A writes to this characteristic
     * Device B reads from this characteristic
     */
    public static final UUID CHARACTERISTIC_WRITE_UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");

    /**
     * Read/Notify Characteristic UUID - Used to receive responses from Device B
     * Device B writes to this characteristic
     * Device A reads/subscribes to notifications from this characteristic
     */
    public static final UUID CHARACTERISTIC_READ_UUID = UUID.fromString("0000ffe2-0000-1000-8000-00805f9b34fb");

    /**
     * Client Characteristic Configuration Descriptor
     * Used to enable notifications on the read characteristic
     */
    public static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // Message types/commands you can send between devices
    public static final String MESSAGE_TYPE_TEXT = "TEXT";
    public static final String MESSAGE_TYPE_COMMAND = "COMMAND";

    // Example commands
    public static final String CMD_PING = "PING";
    public static final String CMD_GET_STATUS = "GET_STATUS";
    public static final String CMD_DISCONNECT = "DISCONNECT";
}
