package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String, // "user" or "humayra"
    val text: String,
    val timestamp: Long,
    val expression: String // "idle", "thinking", "speaking", "surprised", "happy"
)

@Entity(tableName = "business_pages")
data class BusinessPage(
    @PrimaryKey val pageId: String,
    val name: String,
    val category: String,
    val createdTime: Long,
    val fbBusinessId: String
)
