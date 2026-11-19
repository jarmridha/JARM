package com.jarm.app.api.util

import com.jarm.app.api.OpenAiChatRequest
import com.jarm.app.api.OpenAiFunctionCall
import com.jarm.app.api.OpenAiMessage
import com.jarm.app.api.OpenAiRequestFunction
import com.jarm.app.api.OpenAiRequestToolCall
import com.jarm.app.api.OpenAiToolCall
import com.jarm.app.api.anthropic.AnthropicContentPart
import com.jarm.app.api.anthropic.AnthropicImageSource
import com.jarm.app.api.anthropic.coalesceAnthropicMessages
import com.jarm.app.api.anthropic.AnthropicMessage
import com.jarm.app.api.anthropic.AnthropicRequest
import com.jarm.app.api.anthropic.requireValidWireFormat
import com.jarm.app.api.gemini.ApiGenerateContentRequest
import com.jarm.app.api.gemini.ApiInlineData
import com.jarm.app.api.gemini.coalesceGeminiContents
import com.jarm.app.api.gemini.ApiRequestContent
import com.jarm.app.api.gemini.ApiRequestPart
import com.jarm.app.api.gemini.GeminiFunctionCall
import com.jarm.app.api.gemini.GeminiFunctionResponse
import com.jarm.app.api.gemini.requireValidWireFormat
import com.jarm.app.api.ollama.OllamaChatRequest
import com.jarm.app.api.ollama.OllamaMessage
import com.jarm.app.api.ollama.requireValidWireFormat
import com.jarm.app.api.openai.requireValidWireFormat
import com.jarm.app.model.ChatMessage
import com.jarm.app.model.Participant
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun anthropicCoalescesAdjacentUserPayloadsLosslesslyBeforeValidation() {
        val payloads = listOf(
            AnthropicMessage("user", listOf(AnthropicContentPart("text", text = "typed"))),
            AnthropicMessage("user", listOf(
                AnthropicContentPart("text", text = "<ask_user_answers>answer</ask_user_answers>"),
                AnthropicContentPart("image", source = AnthropicImageSource(
                    mediaType = "image/png", data = "aW1hZ2U=",
                )),
            )),
            AnthropicMessage("user", listOf(
                AnthropicContentPart("text", text = "<subagent_result>done</subagent_result>"),
            )),
        )

        val adapted = coalesceAnthropicMessages(payloads)

        assertEquals(payloads.flatMap { it.content }, adapted.single().content)
        assertEquals(3, payloads.size)
        AnthropicRequest(model = "claude-sonnet-5", messages = adapted).requireValidWireFormat()
    }

    @Test
    fun geminiCoalescesAdjacentUserPayloadsLosslesslyBeforeValidation() {
        val payloads = listOf(
            ApiRequestContent("user", listOf(ApiRequestPart(text = "typed"))),
            ApiRequestContent("user", listOf(
                ApiRequestPart(text = "<ask_user_answers>answer</ask_user_answers>"),
                ApiRequestPart(inlineData = ApiInlineData(mimeType = "image/png", data = "aW1hZ2U=")),
            )),
            ApiRequestContent("user", listOf(
                ApiRequestPart(text = "<subagent_result>done</subagent_result>"),
            )),
        )

        val adapted = coalesceGeminiContents(payloads)

        assertEquals(payloads.flatMap { it.parts }, adapted.single().parts)
        assertEquals(3, payloads.size)
        ApiGenerateContentRequest(contents = adapted).requireValidWireFormat("gemini-2.5-pro")
    }

    @Test
    fun anthropicCoalescingPreservesLeadingToolResultsAndRejectsInterruptedPairing() {
        val call = AnthropicMessage("assistant", listOf(AnthropicContentPart(
            type = "tool_use", id = "call_1", name = "file_read", input = emptyObject,
        )))
        val result = AnthropicMessage("user", listOf(AnthropicContentPart(
            type = "tool_result", toolUseId = "call_1", content = "result",
        )))
        val first = AnthropicMessage("user", listOf(AnthropicContentPart("text", text = "first")))
        val last = AnthropicMessage("user", listOf(AnthropicContentPart("text", text = "last")))
        val adapted = coalesceAnthropicMessages(listOf(first, call, result, first, last))

        assertEquals(result.content + first.content + last.content, adapted.last().content)
        AnthropicRequest(model = "claude-sonnet-5", messages = adapted).requireValidWireFormat()
        val error = runCatching {
            AnthropicRequest(
                model = "claude-sonnet-5",
                messages = coalesceAnthropicMessages(listOf(first, call, first, result, last)),
            ).requireValidWireFormat()
        }.exceptionOrNull()
        assertTrue(error is RequestFormatException)
    }

    @Test
    fun geminiCoalescingPreservesLeadingResponsesAndRejectsInterruptedPairing() {
        val call = ApiRequestContent("model", listOf(ApiRequestPart(
            functionCall = GeminiFunctionCall(id = "call_1", name = "file_read", args = emptyObject),
        )))
        val result = ApiRequestContent("user", listOf(ApiRequestPart(
            functionResponse = GeminiFunctionResponse(
                id = "call_1", name = "file_read", response = emptyObject,
            ),
        )))
        val first = ApiRequestContent("user", listOf(ApiRequestPart(text = "first")))
        val last = ApiRequestContent("user", listOf(ApiRequestPart(text = "last")))
        val adapted = coalesceGeminiContents(listOf(first, call, result, first, last))

        assertEquals(result.parts + first.parts + last.parts, adapted.last().parts)
        ApiGenerateContentRequest(contents = adapted).requireValidWireFormat("gemini-2.5-pro")
        val error = runCatching {
            ApiGenerateContentRequest(
                contents = coalesceGeminiContents(listOf(first, call, first, result, last)),
            ).requireValidWireFormat("gemini-2.5-pro")
        }.exceptionOrNull()
        assertTrue(error is RequestFormatException)
    }

    private fun text(value: String) = com.jarm.app.api.OpenAiContentPart(
        type = "text",
        text = value,
    )
}
