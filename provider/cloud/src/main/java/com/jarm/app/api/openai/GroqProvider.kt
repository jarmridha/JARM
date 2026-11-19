package com.jarm.app.api.openai

import com.jarm.app.api.OpenAiChatRequest
import com.jarm.app.api.ProviderConfig
import com.jarm.app.api.util.resolvedThinking
import com.jarm.app.model.ThinkingProviderFamily
import com.jarm.app.model.ModelConstants as Constants

class GroqProvider : BaseOpenAiProvider() {
    override val name: String = Constants.PROVIDER_GROQ
    override val thinkingProviderFamily = ThinkingProviderFamily.GROQ
    override val defaultBaseUrl: String = "https://api.groq.com/openai/v1"

    /**
     * Groq's `reasoning_effort` accepts low/medium/high, plus none/default on the models documented
     * for them. A model that cannot turn reasoning off keeps its lowest effort and hides the
     * reasoning with `include_reasoning: false` instead of failing the request.
     */
    override fun customizeRequest(
        request: OpenAiChatRequest,
        config: ProviderConfig,
    ): OpenAiChatRequest {
        val resolved = config.resolvedThinking(ThinkingProviderFamily.GROQ)
        val capability = resolved.capability
        if (!config.thinkingEnabled && !capability.canDisableThinking) {
            return request.copy(
                reasoningEffort = resolved.effort,
                includeReasoning = false,
            )
        }
        val effort = if (resolved.disabled) {
            "none".takeIf { capability.supportsEffort }
        } else {
            resolved.effort
        }
        return request.copy(reasoningEffort = effort)
    }
}
