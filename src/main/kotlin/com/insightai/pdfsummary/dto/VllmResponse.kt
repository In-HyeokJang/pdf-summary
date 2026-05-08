package com.insightai.pdfsummary.dto

data class VllmResponse(
    val choices: List<Choice>
) {
    data class Choice(val message: Message)
    data class Message(val content: String)

    fun text(): String = choices.firstOrNull()?.message?.content?.trim() ?: ""
}