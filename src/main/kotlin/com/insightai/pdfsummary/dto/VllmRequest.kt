package com.insightai.pdfsummary.dto

data class VllmRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double = 0.3,
    val max_tokens: Int = 4096
) {
    data class Message(val role: String, val content: String)
}