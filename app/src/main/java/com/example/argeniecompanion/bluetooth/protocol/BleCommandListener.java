package com.example.argeniecompanion.bluetooth.protocol;

/**
 * Listener interface for handling BLE commands from the Controller app.
 * Implementations should handle the business logic for each command.
 */
public interface BleCommandListener {

    /**
     * Called when a JOIN_ROOM command is received.
     * The result must be reported asynchronously via BleGattServerService.sendDeferredCommandResult().
     *
     * @param linkCode The room link code to join
     * @param userName The user's display name
     */
    void onJoinRoom(String linkCode, String userName);

    /**
     * Called when a LEAVE_ROOM command is received.
     */
    void onLeaveRoom();

    /**
     * Called when a MIC_MUTE command is received.
     */
    void onMicMute();

    /**
     * Called when a MIC_UNMUTE command is received.
     */
    void onMicUnmute();

    /**
     * Called when a VIDEO_MUTE command is received.
     */
    void onVideoMute();

    /**
     * Called when a VIDEO_UNMUTE command is received.
     */
    void onVideoUnmute();
}
