package com.jarm.app.api.openai

import com.jarm.app.api.OpenAiChatRequest
import com.jarm.app.api.OpenAiThinking
import com.jarm.app.api.ProviderConfig
import com.jarm.app.api.util.resolvedThinking
import com.jarm.app.model.ResolvedThinking
import com.jarm.app.model.ThinkingProviderFamily
import com.jarm.app.model.ModelConstants as Constants

class DeepSeekProvider : BaseOpenAiProvider() {
    override val name: String = Constants.PROVIDER_DEEPSEEK
    override val thinkingProviderFamily = ThinkingProviderFamily.DEEPSEEK
    override val defaultBaseUrl: String = "https://api.deepseek.com"

    /**
     * DeepSeek controls thinking through `thinking.type` and `reasoning_effort`. Neither used to
     * be sent, so the app toggle did nothing and the server default (thinking on, effort high)
     * always applied.
     */
    override fun customizeRequest(
        request: OpenAiChatRequest,
        config: ProviderConfig,
    ): OpenAiChatRequest =
        request.withDeepSeekThinking(config.resolvedThinking(ThinkingProviderFamily.DEEPSEEK))

    /** A thinking-mode DeepSeek request with tools fails with 400 unless earlier turns replay CoT. */
    override fun forwardsAssistantReasoningContent(config: ProviderConfig): Boolean =
        config.includeAssistantReasoning

    // Reasoning/content parsing uses BaseOpenAiProvider's default (reasoning_content + content).
}

/** True when a model id names a DeepSeek model, including hub- and relay-style prefixes. */
fun isDeepSeekModel(modelId: String): Boolean =
    modelId.contains("deepseek", ignoreCase = true)

/**
 * Applies the DeepSeek thinking controls to an OpenAI-format request. `thinking.type` toggles
 * thinking and `reasoning_effort` carries the level the model accepts.
 */
internal fun OpenAiChatRequest.withDeepSeekThinking(
    resolved: ResolvedThinking,
): OpenAiChatRequest = if (resolved.disabled) {
    copy(thinking = OpenAiThinking(type = "disabled"))
} else {
    copy(
        thinking = OpenAiThinking(type = "enabled"),
        reasoningEffort = resolved.effort,
    )
}
