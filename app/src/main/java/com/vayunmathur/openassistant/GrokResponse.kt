
package com.vayunmathur.openassistant

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GrokResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage
)

@Serializable
data class Choice(
    val index: Int,
    val message: ResponseMessage,
    val finish_reason: String
)

@Serializable
data class ResponseMessage(
    val role: String,
    val content: String
)

@Serializable
data class Usage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int,
)

// For streaming
@Serializable
data class GrokChunk(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<ChunkChoice>
)

@Serializable
data class ChunkChoice(
    val index: Int,
    val delta: Delta
)

@Serializable
data class Delta(
    val role: String? = null,
    val content: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<ToolCall>? = null,
)

@Serializable
data class ToolCall(
    val id: String,
    val type: String,
    val function: Function
) {
    @Serializable
    data class Function(
        val name: String,
        val arguments: String
    )
}
