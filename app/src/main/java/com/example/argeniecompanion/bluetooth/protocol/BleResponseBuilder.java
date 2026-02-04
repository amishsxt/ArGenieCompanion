package com.example.argeniecompanion.bluetooth.protocol;

/**
 * Builder for BLE binary response packets.
 *
 * Response Packet Structure:
 * ┌─────────┬─────────┬────────┬───────────┬──────────┐
 * │ VERSION │ COMMAND │ LENGTH │  PAYLOAD  │ CHECKSUM │
 * │  1 byte │  1 byte │ 1 byte │  0-N bytes│  1 byte  │
 * └─────────┴─────────┴────────┴───────────┴──────────┘
 *
 * Response command = request command | 0x80
 *
 * Simple ACK Response Payload: [status:1]
 * Status Response Payload: [status:1][batteryLevel:1][flags:1]
 *   flags: bit0=micMuted, bit1=videoMuted, bit2=inRoom
 */
public final class BleResponseBuilder {

    private BleResponseBuilder() {
        // Prevent instantiation
    }

    /**
     * Build a simple ACK response for a command.
     *
     * @param command The original command byte (will be OR'd with 0x80)
     * @param status  The status code to send back
     * @return The complete response packet
     */
    public static byte[] buildAckResponse(byte command, byte status) {
        // Response: VERSION(1) + RESPONSE_CMD(1) + LENGTH(1) + STATUS(1) + CHECKSUM(1) = 5 bytes
        byte[] response = new byte[5];

        response[0] = BleProtocol.PROTOCOL_VERSION;
        response[1] = (byte) (command | BleProtocol.RESPONSE_MASK);
        response[2] = 1; // Payload length (just status byte)
        response[3] = status;
        response[4] = BleProtocol.calculateChecksum(response, 4);

        return response;
    }

    /**
     * Build a status response for GET_STATUS command.
     *
     * @param status       The status code (usually STATUS_OK)
     * @param batteryLevel Battery level percentage (0-100)
     * @param micMuted     True if microphone is muted
     * @param videoMuted   True if video is muted
     * @param inRoom       True if currently in a room
     * @return The complete response packet
     */
    public static byte[] buildStatusResponse(byte status, int batteryLevel,
                                              boolean micMuted, boolean videoMuted, boolean inRoom) {
        // Response: VERSION(1) + RESPONSE_CMD(1) + LENGTH(1) + STATUS(1) + BATTERY(1) + FLAGS(1) + CHECKSUM(1) = 7 bytes
        byte[] response = new byte[7];

        // Build flags byte
        byte flags = 0;
        if (micMuted) {
            flags |= BleProtocol.FLAG_MIC_MUTED;
        }
        if (videoMuted) {
            flags |= BleProtocol.FLAG_VIDEO_MUTED;
        }
        if (inRoom) {
            flags |= BleProtocol.FLAG_IN_ROOM;
        }

        // Clamp battery level to valid range
        int clampedBattery = Math.max(0, Math.min(100, batteryLevel));

        response[0] = BleProtocol.PROTOCOL_VERSION;
        response[1] = (byte) (BleProtocol.CMD_GET_STATUS | BleProtocol.RESPONSE_MASK);
        response[2] = 3; // Payload length (status + battery + flags)
        response[3] = status;
        response[4] = (byte) clampedBattery;
        response[5] = flags;
        response[6] = BleProtocol.calculateChecksum(response, 6);

        return response;
    }

    /**
     * Build a PING response (PONG).
     *
     * @return The complete PONG response packet
     */
    public static byte[] buildPongResponse() {
        return buildAckResponse(BleProtocol.CMD_PING, BleProtocol.STATUS_OK);
    }
}
