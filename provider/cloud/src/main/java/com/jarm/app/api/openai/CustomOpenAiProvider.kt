package com.jarm.app.api.openai

import com.jarm.app.api.OpenAiChatRequest
import com.jarm.app.api.ProviderConfig
import com.jarm.app.api.util.resolvedThinking
import com.jarm.app.model.ThinkingProviderFamily

class CustomOpenAiProvider(
    override val name: String,
    override val defaultBaseUrl: String
) : BaseOpenAiProvider() {

    override val retryableStatusCodes: Set<Int> = setOf(429, 502, 503, 504)

    override val retryMissingV1BaseUrl: Boolean = true

    /**
     * Resolve documented model capabilities while retaining the permissive fallback for unknown
     * relay IDs. The endpoint protocol still owns the spelling of every serialized parameter.
     */
    override fun customizeRequest(
        request: OpenAiChatRequest,
        config: ProviderConfig,
    ): OpenAiChatRequest {
        val resolved = config.resolvedThinking(ThinkingProviderFamily.OPENAI_COMPATIBLE)
        val effort = if (resolved.disabled) {
            "none".takeIf { resolved.capability.supportsEffort }
        } else {
            resolved.effort
        }
        return request.copy(
            reasoningEffort = effort,
            thinkingBudget = resolved.budgetTokens,
            chatTemplateKwargs = resolved.qwenTemplateKwargs(config.modelId, thinkingProviderFamily),
        )
    }

    /** Same rule as the built-in provider: a relayed DeepSeek model replays chain of thought. */
    override fun forwardsAssistantReasoningContent(config: ProviderConfig): Boolean =
        config.includeAssistantReasoning
}
