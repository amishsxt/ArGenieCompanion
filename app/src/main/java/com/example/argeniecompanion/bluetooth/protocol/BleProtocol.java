package com.example.argeniecompanion.bluetooth.protocol;

import java.util.UUID;

/**
 * Binary protocol constants for BLE communication.
 *
 * Packet Structure:
 * ┌─────────┬─────────┬────────┬───────────┬──────────┐
 * │ VERSION │ COMMAND │ LENGTH │  PAYLOAD  │ CHECKSUM │
 * │  1 byte │  1 byte │ 1 byte │  0-N bytes│  1 byte  │
 * └─────────┴─────────┴────────┴───────────┴──────────┘
 */
public final class BleProtocol {

    private BleProtocol() {
        // Prevent instantiation
    }

    // Protocol version
    public static final byte PROTOCOL_VERSION = 0x01;

    // Minimum packet size (version + command + length + checksum)
    public static final int MIN_PACKET_SIZE = 4;

    // Maximum payload size (fits in MTU of 20 with header)
    public static final int MAX_PAYLOAD_SIZE = 16;

    // ==================== BLE UUIDs ====================

    public static final UUID SERVICE_UUID =
            UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");

    public static final UUID WRITE_CHARACTERISTIC_UUID =
            UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");

    public static final UUID READ_CHARACTERISTIC_UUID =
            UUID.fromString("0000ffe2-0000-1000-8000-00805f9b34fb");

    public static final UUID CCCD_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // ==================== Request Commands (from Controller) ====================

    public static final byte CMD_JOIN_ROOM = 0x01;
    public static final byte CMD_LEAVE_ROOM = 0x02;
    public static final byte CMD_MIC_MUTE = 0x03;
    public static final byte CMD_MIC_UNMUTE = 0x04;
    public static final byte CMD_VIDEO_MUTE = 0x05;
    public static final byte CMD_VIDEO_UNMUTE = 0x06;
    public static final byte CMD_PING = 0x07;
    public static final byte CMD_GET_STATUS = 0x08;

    // Response command mask (command | 0x80)
    public static final byte RESPONSE_MASK = (byte) 0x80;

    // ==================== Status Codes ====================

    public static final byte STATUS_OK = 0x00;
    public static final byte STATUS_ERROR = 0x01;
    public static final byte STATUS_INVALID_COMMAND = 0x02;
    public static final byte STATUS_INVALID_PAYLOAD = 0x03;
    public static final byte STATUS_NOT_CONNECTED = 0x04;
    public static final byte STATUS_ALREADY_IN_ROOM = 0x05;

    // ==================== Status Response Flags ====================

    public static final byte FLAG_MIC_MUTED = 0x01;      // bit 0
    public static final byte FLAG_VIDEO_MUTED = 0x02;    // bit 1
    public static final byte FLAG_IN_ROOM = 0x04;        // bit 2

    // ==================== Utility Methods ====================

    /**
     * Calculate XOR checksum of all bytes in the array up to (but not including) the last byte.
     */
    public static byte calculateChecksum(byte[] data, int length) {
        byte checksum = 0;
        for (int i = 0; i < length; i++) {
            checksum ^= data[i];
        }
        return checksum;
    }

    /**
     * Get human-readable command name for logging.
     */
    public static String getCommandName(byte command) {
        switch (command) {
            case CMD_JOIN_ROOM:
                return "JOIN_ROOM";
            case CMD_LEAVE_ROOM:
                return "LEAVE_ROOM";
            case CMD_MIC_MUTE:
                return "MIC_MUTE";
            case CMD_MIC_UNMUTE:
                return "MIC_UNMUTE";
            case CMD_VIDEO_MUTE:
                return "VIDEO_MUTE";
            case CMD_VIDEO_UNMUTE:
                return "VIDEO_UNMUTE";
            case CMD_PING:
                return "PING";
            case CMD_GET_STATUS:
                return "GET_STATUS";
            default:
                return "UNKNOWN(0x" + String.format("%02X", command) + ")";
        }
    }

    /**
     * Get human-readable status name for logging.
     */
    public static String getStatusName(byte status) {
        switch (status) {
            case STATUS_OK:
                return "OK";
            case STATUS_ERROR:
                return "ERROR";
            case STATUS_INVALID_COMMAND:
                return "INVALID_COMMAND";
            case STATUS_INVALID_PAYLOAD:
                return "INVALID_PAYLOAD";
            case STATUS_NOT_CONNECTED:
                return "NOT_CONNECTED";
            case STATUS_ALREADY_IN_ROOM:
                return "ALREADY_IN_ROOM";
            default:
                return "UNKNOWN(0x" + String.format("%02X", status) + ")";
        }
    }
}
