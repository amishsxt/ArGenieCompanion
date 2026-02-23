package com.example.argeniecompanion.model

import org.json.JSONObject

data class ChatMessage(
    val message: String,
    val messageId: String,
    val messageTime: String,
    val mimeType: String,
    val senderType: String,
    val sender: String
) {
    companion object {
        @JvmStatic
        fun fromJson(json: JSONObject): ChatMessage = ChatMessage(
            message = json.optString("message"),
            messageId = json.optString("messageId"),
            messageTime = json.optString("messageTime"),
            mimeType = json.optString("mimeType", "text/plain"),
            senderType = json.optString("senderType"),
            sender = json.optString("sender")
        )

        @JvmStatic
        fun fromJsonString(jsonString: String): ChatMessage? = try {
            fromJson(JSONObject(jsonString))
        } catch (e: Exception) {
            null
        }

        /** Maps a GraphQL chatMessages edge node (uses `createdAt` instead of `messageTime`). */
        @JvmStatic
        fun fromGraphQlNode(node: JSONObject): ChatMessage = ChatMessage(
            message = node.optString("message"),
            messageId = node.optString("messageId"),
            messageTime = node.optString("createdAt"),
            mimeType = node.optString("mimeType", "text/plain"),
            senderType = node.optString("senderType"),
            sender = node.optString("sender")
        )
    }

    val isFromUser: Boolean get() = senderType == "user"
    val isTextMessage: Boolean get() = mimeType == "text/plain"

    fun fileTypeLabel(): String = when {
        mimeType.startsWith("image/") -> "IMG"
        mimeType == "application/pdf" -> "PDF"
        mimeType == "application/msword" ||
        mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "DOC"
        else -> "FILE"
    }
}
