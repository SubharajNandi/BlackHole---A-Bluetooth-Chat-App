package com.example.bluetoothchat

data class ChatMessage(
    val text: String,
    val isMe: Boolean,
    val senderName: String = ""
)
