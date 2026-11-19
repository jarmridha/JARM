package com.jarm.app.api.openai

import com.jarm.app.api.OpenAiMessage
import com.jarm.app.api.OpenAiReasoning
import com.jarm.app.api.OpenAiResponseTool
import com.jarm.app.api.OpenAiResponsesRequest
import com.jarm.app.api.ProviderConfig
import com.jarm.app.api.util.resolvedThinking
import com.jarm.app.model.ResolvedThinking
import com.jarm.app.model.ThinkingProviderFamily
import com.jarm.app.model.isQwen38ModelId
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Qwen's official template controls: https://huggingface.co/Qwen/Qwen3.8-27B */
internal fun ResolvedThinking.qwenTemplateKwargs(
    modelId: String,
    family: ThinkingProviderFamily,
): JsonObject? = if (family == ThinkingProviderFamily.OPENAI_COMPATIBLE && isQwen38ModelId(modelId)) {
    buildJsonObject {
        put("enable_thinking", enabled)
        if (enabled) effort?.let { put("reasoning_effort", it) }
    }
} else null

/** Responses uses the same resolved capability as Chat, including mandatory thinking and effort. */
internal fun buildOpenAiResponsesRequest(
    config: ProviderConfig,
    messages: List<OpenAiMessage>,
    providerName: String,
    family: ThinkingProviderFamily,
): OpenAiResponsesRequest {
    val resolved = config.resolvedThinking(family)
    val templateKwargs = resolved.qwenTemplateKwargs(config.modelId, family)
    return OpenAiResponsesRequest(
        model = config.modelId,
        input = messages.toResponsesInput(providerName = providerName, targetModel = config.modelId),
        tools = buildList {
            addAll(config.tools.orEmpty().toResponsesTools())
            if (config.openAiWebSearchEnabled) add(OpenAiResponseTool(type = "web_search"))
        }.ifEmpty { null },
        reasoning = if (resolved.enabled && (resolved.effort != null || resolved.budgetTokens != null)) {
            OpenAiReasoning(effort = resolved.effort, maxTokens = resolved.budgetTokens, summary = "auto")
        } else null,
        chatTemplateKwargs = templateKwargs,
        reasoningEffort = if (templateKwargs != null) {
            if (resolved.disabled) "none" else resolved.effort
        } else null,
        thinkingBudget = resolved.budgetTokens.takeIf { templateKwargs != null },
        serviceTier = config.openAiServiceTier,
        temperature = config.temperature.takeIf { resolved.capability.supportsSamplingParams },
        maxOutputTokens = config.effectiveOpenAiMaxTokens(),
        topP = config.topP.takeIf { resolved.capability.supportsSamplingParams },
        promptCacheKey = config.promptCacheKey,
    )
}
