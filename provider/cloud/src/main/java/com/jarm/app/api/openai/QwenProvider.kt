package com.jarm.app.api.openai

import com.jarm.app.api.OpenAiChatRequest
import com.jarm.app.api.ProviderConfig
import com.jarm.app.api.util.resolvedThinking
import com.jarm.app.model.ThinkingProviderFamily
import com.jarm.app.model.ModelConstants as Constants

class QwenProvider : BaseOpenAiProvider() {
    override val name: String = Constants.PROVIDER_QWEN
    override val thinkingProviderFamily = ThinkingProviderFamily.QWEN
    override val defaultBaseUrl: String = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1"

    /**
     * DashScope's compatible mode has two documented thinking shapes, and the model's capability
     * entry decides which one applies:
     *  - effort models take `reasoning_effort` (with `none` turning thinking off) and reject
     *    `reasoning_effort` together with `thinking_budget`;
     *  - hybrid models take `enable_thinking` plus an optional `thinking_budget`.
     */
    override fun customizeRequest(
        request: OpenAiChatRequest,
        config: ProviderConfig,
    ): OpenAiChatRequest {
        val resolved = config.resolvedThinking(ThinkingProviderFamily.QWEN)
        return if (resolved.capability.supportsEffort) {
            request.copy(
                reasoningEffort = if (resolved.disabled) "none" else resolved.effort,
                thinkingBudget = resolved.budgetTokens,
            )
        } else {
            request.copy(
                enableThinking = resolved.enabled,
                thinkingBudget = resolved.budgetTokens,
            )
        }
    }

    // Reasoning/content parsing uses BaseOpenAiProvider's default (reasoning_content + content).
}
