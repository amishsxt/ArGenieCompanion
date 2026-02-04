package com.example.argeniecompanion.bluetooth.protocol;

import java.nio.charset.StandardCharsets;

/**
 * Parser for incoming BLE binary commands.
 *
 * Packet Structure:
 * ┌─────────┬─────────┬────────┬───────────┬──────────┐
 * │ VERSION │ COMMAND │ LENGTH │  PAYLOAD  │ CHECKSUM │
 * │  1 byte │  1 byte │ 1 byte │  0-N bytes│  1 byte  │
 * └─────────┴─────────┴────────┴───────────┴──────────┘
 *
 * JOIN_ROOM Payload Structure:
 * [linkCodeLen:1][linkCode:N][userNameLen:1][userName:M]
 */
public final class BleCommandParser {

    private static final String TAG = "BleCommandParser";

    private BleCommandParser() {
        // Prevent instantiation
    }

    /**
     * Parse a binary packet and extract the command.
     *
     * @param data The raw binary data received from BLE
     * @return ParsedCommand with either valid command data or error status
     */
    public static ParsedCommand parse(byte[] data) {
        if (data == null || data.length < BleProtocol.MIN_PACKET_SIZE) {
            return ParsedCommand.invalid(BleProtocol.STATUS_INVALID_COMMAND);
        }

        // Extract header fields
        byte version = data[0];
        byte command = data[1];
        int payloadLength = data[2] & 0xFF; // Unsigned byte
        byte receivedChecksum = data[data.length - 1];

        // Validate version
        if (version != BleProtocol.PROTOCOL_VERSION) {
            return ParsedCommand.invalid(command, BleProtocol.STATUS_INVALID_COMMAND);
        }

        // Validate packet length
        int expectedLength = BleProtocol.MIN_PACKET_SIZE + payloadLength;
        if (data.length != expectedLength) {
            return ParsedCommand.invalid(command, BleProtocol.STATUS_INVALID_PAYLOAD);
        }

        // Validate checksum (XOR of all bytes except checksum itself)
        byte calculatedChecksum = BleProtocol.calculateChecksum(data, data.length - 1);
        if (receivedChecksum != calculatedChecksum) {
            return ParsedCommand.invalid(command, BleProtocol.STATUS_INVALID_COMMAND);
        }

        // Extract payload
        byte[] payload = null;
        if (payloadLength > 0) {
            payload = new byte[payloadLength];
            System.arraycopy(data, 3, payload, 0, payloadLength);
        }

        // Validate command type
        if (!isValidCommand(command)) {
            return ParsedCommand.invalid(command, BleProtocol.STATUS_INVALID_COMMAND);
        }

        // Create valid parsed command
        ParsedCommand parsedCommand = ParsedCommand.valid(command, payload);

        // Parse command-specific payload
        if (command == BleProtocol.CMD_JOIN_ROOM) {
            if (!parseJoinRoomPayload(parsedCommand, payload)) {
                return ParsedCommand.invalid(command, BleProtocol.STATUS_INVALID_PAYLOAD);
            }
        } else {
            // Commands without payload should have empty payload
            if (payloadLength > 0) {
                return ParsedCommand.invalid(command, BleProtocol.STATUS_INVALID_PAYLOAD);
            }
        }

        return parsedCommand;
    }

    /**
     * Check if the command byte is a valid known command.
     */
    private static boolean isValidCommand(byte command) {
        switch (command) {
            case BleProtocol.CMD_JOIN_ROOM:
            case BleProtocol.CMD_LEAVE_ROOM:
            case BleProtocol.CMD_MIC_MUTE:
            case BleProtocol.CMD_MIC_UNMUTE:
            case BleProtocol.CMD_VIDEO_MUTE:
            case BleProtocol.CMD_VIDEO_UNMUTE:
            case BleProtocol.CMD_PING:
            case BleProtocol.CMD_GET_STATUS:
                return true;
            default:
                return false;
        }
    }

    /**
     * Parse JOIN_ROOM payload: [linkCodeLen:1][linkCode:N][userNameLen:1][userName:M]
     *
     * @param parsedCommand The command to populate with link code and user name
     * @param payload The raw payload bytes
     * @return true if parsing succeeded, false otherwise
     */
    private static boolean parseJoinRoomPayload(ParsedCommand parsedCommand, byte[] payload) {
        if (payload == null || payload.length < 2) {
            return false;
        }

        int offset = 0;

        // Parse link code length
        int linkCodeLen = payload[offset++] & 0xFF;
        if (linkCodeLen == 0 || offset + linkCodeLen > payload.length) {
            return false;
        }

        // Parse link code
        String linkCode;
        try {
            linkCode = new String(payload, offset, linkCodeLen, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return false;
        }
        offset += linkCodeLen;

        // Parse user name length
        if (offset >= payload.length) {
            return false;
        }
        int userNameLen = payload[offset++] & 0xFF;
        if (userNameLen == 0 || offset + userNameLen > payload.length) {
            return false;
        }

        // Parse user name
        String userName;
        try {
            userName = new String(payload, offset, userNameLen, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return false;
        }

        // Validate we consumed exactly the right amount of payload
        if (offset + userNameLen != payload.length) {
            return false;
        }

        parsedCommand.setLinkCode(linkCode);
        parsedCommand.setUserName(userName);

        return true;
    }

    /**
     * Convert binary data to hex string for logging.
     */
    public static String toHexString(byte[] data) {
        if (data == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}
