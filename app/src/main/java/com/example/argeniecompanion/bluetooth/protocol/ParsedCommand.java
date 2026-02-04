package com.example.argeniecompanion.bluetooth.protocol;

/**
 * Represents a parsed BLE command with its extracted data.
 */
public class ParsedCommand {

    private final byte command;
    private final byte[] payload;
    private final boolean valid;
    private final byte errorStatus;

    // Parsed JOIN_ROOM data (only populated for JOIN_ROOM commands)
    private String linkCode;
    private String userName;

    private ParsedCommand(byte command, byte[] payload, boolean valid, byte errorStatus) {
        this.command = command;
        this.payload = payload;
        this.valid = valid;
        this.errorStatus = errorStatus;
    }

    /**
     * Create a valid parsed command.
     */
    public static ParsedCommand valid(byte command, byte[] payload) {
        return new ParsedCommand(command, payload, true, BleProtocol.STATUS_OK);
    }

    /**
     * Create an invalid parsed command with error status.
     */
    public static ParsedCommand invalid(byte errorStatus) {
        return new ParsedCommand((byte) 0, null, false, errorStatus);
    }

    /**
     * Create an invalid parsed command with error status and partial command info.
     */
    public static ParsedCommand invalid(byte command, byte errorStatus) {
        return new ParsedCommand(command, null, false, errorStatus);
    }

    public byte getCommand() {
        return command;
    }

    public byte[] getPayload() {
        return payload;
    }

    public boolean isValid() {
        return valid;
    }

    public byte getErrorStatus() {
        return errorStatus;
    }

    public String getLinkCode() {
        return linkCode;
    }

    public void setLinkCode(String linkCode) {
        this.linkCode = linkCode;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    @Override
    public String toString() {
        if (valid) {
            String result = "ParsedCommand{cmd=" + BleProtocol.getCommandName(command);
            if (linkCode != null) {
                result += ", linkCode='" + linkCode + "'";
            }
            if (userName != null) {
                result += ", userName='" + userName + "'";
            }
            return result + "}";
        } else {
            return "ParsedCommand{invalid, error=" + BleProtocol.getStatusName(errorStatus) + "}";
        }
    }
}
