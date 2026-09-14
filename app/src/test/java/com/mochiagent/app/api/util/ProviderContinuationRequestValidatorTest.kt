package com.mochiagent.app.api.util

import com.mochiagent.app.api.OpenAiChatRequest
import com.mochiagent.app.api.OpenAiFunctionCall
import com.mochiagent.app.api.OpenAiMessage
import com.mochiagent.app.api.OpenAiRequestFunction
import com.mochiagent.app.api.OpenAiRequestToolCall
import com.mochiagent.app.api.OpenAiToolCall
import com.mochiagent.app.api.anthropic.AnthropicContentPart
import com.mochiagent.app.api.anthropic.AnthropicMessage
import com.mochiagent.app.api.anthropic.AnthropicRequest
import com.mochiagent.app.api.anthropic.requireValidWireFormat
import com.mochiagent.app.api.gemini.ApiGenerateContentRequest
import com.mochiagent.app.api.gemini.ApiRequestContent
import com.mochiagent.app.api.gemini.ApiRequestPart
import com.mochiagent.app.api.gemini.GeminiFunctionCall
import com.mochiagent.app.api.gemini.GeminiFunctionResponse
import com.mochiagent.app.api.gemini.requireValidWireFormat
import com.mochiagent.app.api.ollama.OllamaChatRequest
import com.mochiagent.app.api.ollama.OllamaMessage
import com.mochiagent.app.api.ollama.requireValidWireFormat
import com.mochiagent.app.api.openai.requireValidWireFormat
import com.mochiagent.app.model.ChatMessage
import com.mochiagent.app.model.Participant
import kotlinx.serialization.json.JsonObject
import org.junit.Test

/** Regression coverage for provider requests that terminate in tool or Compact input. */
class ProviderContinuationRequestValidatorTest {
    private val emptyObject = JsonObject(emptyMap())

    @Test
    fun openAiAcceptsCompleteToolResultAsTerminalInput() {
        OpenAiChatRequest(
            model = "deepseek-chat",
            messages = listOf(
                OpenAiMessage("user", content = listOf(text("start"))),
                OpenAiMessage(
                    "assistant",
                    toolCalls = listOf(
                        OpenAiRequestToolCall(
                            id = "call_1",
                            function = OpenAiRequestFunction("file_read", "{}"),
                        )
                    ),
                ),
                OpenAiMessage("tool", content = listOf(text("result")), toolCallId = "call_1"),
            ),
        ).requireValidWireFormat("DeepSeek")
    }

    @Test
    fun anthropicAcceptsCompleteToolResultAsTerminalInput() {
        AnthropicRequest(
            model = "claude-sonnet-5",
            messages = listOf(
                AnthropicMessage("user", listOf(AnthropicContentPart("text", text = "start"))),
                AnthropicMessage(
                    "assistant",
                    listOf(
                        AnthropicContentPart(
                            type = "tool_use",
                            id = "call_1",
                            name = "file_read",
                            input = emptyObject,
                        )
                    ),
                ),
                AnthropicMessage(
                    "user",
                    listOf(
                        AnthropicContentPart(
                            type = "tool_result",
                            toolUseId = "call_1",
                            content = "result",
                        )
                    ),
                ),
            ),
        ).requireValidWireFormat()
    }

    @Test
    fun geminiAcceptsCompleteFunctionResponseAsTerminalInput() {
        ApiGenerateContentRequest(
            contents = listOf(
                ApiRequestContent("user", listOf(ApiRequestPart(text = "start"))),
                ApiRequestContent(
                    "model",
                    listOf(
                        ApiRequestPart(
                            functionCall = GeminiFunctionCall(
                                id = "call_1",
                                name = "file_read",
                                args = emptyObject,
                            )
                        )
                    ),
                ),
                ApiRequestContent(
                    "user",
                    listOf(
                        ApiRequestPart(
                            functionResponse = GeminiFunctionResponse(
                                id = "call_1",
                                name = "file_read",
                                response = emptyObject,
                            )
                        )
                    ),
                ),
            ),
        ).requireValidWireFormat("gemini-2.5-pro")
    }

    @Test
    fun ollamaAcceptsCompleteToolResultAsTerminalInput() {
        OllamaChatRequest(
            model = "qwen3",
            messages = listOf(
                OllamaMessage("user", content = "start"),
                OllamaMessage(
                    "assistant",
                    toolCalls = listOf(
                        OpenAiToolCall(
                            index = 0,
                            id = "call_1",
                            type = "function",
                            function = OpenAiFunctionCall("file_read", emptyObject),
                        )
                    ),
                ),
                OllamaMessage("tool", content = "result", toolName = "file_read"),
            ),
        ).requireValidWireFormat()
    }

    @Test
    fun compactContinuationIsValidTerminalUserInputForEveryProvider() {
        val content = prepareMessages(
            listOf(
                ChatMessage(
                    id = "compact_boundary",
                    text = "summary",
                    participant = Participant.MODEL,
                )
            ),
            contextTokenBudget = 4_096,
        ).single().text

        OpenAiChatRequest(
            model = "deepseek-chat",
            messages = listOf(OpenAiMessage("user", content = listOf(text(content)))),
        ).requireValidWireFormat("DeepSeek")
        AnthropicRequest(
            model = "claude-sonnet-5",
            messages = listOf(
                AnthropicMessage("user", listOf(AnthropicContentPart("text", text = content)))
            ),
        ).requireValidWireFormat()
        ApiGenerateContentRequest(
            contents = listOf(
                ApiRequestContent("user", listOf(ApiRequestPart(text = content)))
            ),
        ).requireValidWireFormat("gemini-2.5-pro")
        OllamaChatRequest(
            model = "qwen3",
            messages = listOf(OllamaMessage("user", content = content)),
        ).requireValidWireFormat()
    }

    private fun text(value: String) = com.mochiagent.app.api.OpenAiContentPart(
        type = "text",
        text = value,
    )
}
