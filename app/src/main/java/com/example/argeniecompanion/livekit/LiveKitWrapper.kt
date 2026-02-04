package com.example.argeniecompanion.livekit

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.argeniecompanion.app.ArGenieApp
import com.example.argeniecompanion.logger.AppLogger
import com.example.argeniecompanion.network.PostJson
import io.livekit.android.ConnectOptions
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.RoomOptions
import io.livekit.android.room.Room
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.LocalVideoTrackOptions
import kotlinx.coroutines.*
import livekit.org.webrtc.EglBase
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

/**
 * Simplified LiveKit wrapper.
 * Only responsible for: generating a LiveKit token, connecting to a room,
 * and streaming the device camera video.
 */
class LiveKitWrapper(private val context: Context) {

    private val TAG = "LiveKitWrapper"

    private var room: Room? = null
    private var isConnectedToRoom = false
    private var isJoiningRoom = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val eglBase: EglBase = EglBase.create()

    var connectionCallback: ConnectionCallback? = null

    // ----------------------------------------------------

    fun start(linkCode: String) {
        if (isConnectedToRoom || isJoiningRoom) return
        isJoiningRoom = true

        PostJson.postJson(
            "${ArGenieApp.getInstance().config.apiUrl}/livekit/token",
            ArGenieApp.userId,
            linkCode,
            ArGenieApp.userName,
            ArGenieApp.getUserDeviceId(),
            object : Callback {

                override fun onFailure(call: Call, e: IOException) {
                    isJoiningRoom = false
                    postToMain {
                        connectionCallback?.onFailure(e.message ?: "Token fetch failed")
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val body = response.body?.string()

                        if (!response.isSuccessful || body == null) {
                            isJoiningRoom = false
                            postToMain {
                                connectionCallback?.onFailure("Token API error: $body")
                            }
                            return
                        }

                        val token = JSONObject(body).getString("token")
                        isJoiningRoom = false
                        connectToRoom(token)

                    } catch (ex: Exception) {
                        isJoiningRoom = false
                        postToMain {
                            connectionCallback?.onFailure(ex.message ?: "Parse error")
                        }
                    }
                }
            }
        )
    }

    // ----------------------------------------------------

    private fun connectToRoom(token: String) {
        scope.launch {
            try {
                // SAME pattern as version 1
                val roomOptions = RoomOptions(adaptiveStream = true, dynacast = true)
                val overrides = LiveKitOverrides(null, null, null, null, eglBase, null)

                room = LiveKit.create(
                    context.applicationContext,
                    roomOptions,
                    overrides
                )

                startEventListening()   // VERY IMPORTANT (missing in #2)

                val connectOptions = ConnectOptions()
                room?.connect(
                    ArGenieApp.getInstance().config.livekitUrl,
                    token,
                    connectOptions
                )

                isConnectedToRoom = true

                // Enable back camera to stream video track
                room?.videoTrackCaptureDefaults = LocalVideoTrackOptions(
                    position = CameraPosition.BACK
                )
                room?.localParticipant?.setCameraEnabled(true)

                postToMain {
                    connectionCallback?.onConnected()
                }

            } catch (t: Throwable) {
                isConnectedToRoom = false
                postToMain {
                    connectionCallback?.onFailure(t.message ?: "Connect failed")
                }
            }
        }
    }

    // ----------------------------------------------------

    private fun startEventListening() {
        scope.launch {
            try {
                room?.events?.events?.collect {
                    // just pumping LiveKit state machine like version 1
                }
            } catch (_: Exception) {
            }
        }
    }

    // ----------------------------------------------------

    fun enableCamera(enable: Boolean) {
        scope.launch {
            try {
                room?.localParticipant?.setCameraEnabled(enable)
            } catch (_: Exception) {
            }
        }
    }

    fun enableMicrophone(enable: Boolean) {
        scope.launch {
            try {
                room?.localParticipant?.setMicrophoneEnabled(enable)
            } catch (_: Exception) {
            }
        }
    }

    fun isCameraEnabled(): Boolean {
        return room?.localParticipant?.isCameraEnabled() ?: false
    }

    fun isMicrophoneEnabled(): Boolean {
        return room?.localParticipant?.isMicrophoneEnabled() ?: false
    }

    fun stop() {
        scope.launch {
            try {
                room?.localParticipant?.setCameraEnabled(false)
                room?.disconnect()
            } catch (_: Exception) {
            }
        }
        scope.cancel("Wrapper stopped")
    }

    private fun postToMain(block: () -> Unit) {
        Handler(Looper.getMainLooper()).post { block() }
    }

    interface ConnectionCallback {
        fun onConnected()
        fun onFailure(error: String)
    }
}
