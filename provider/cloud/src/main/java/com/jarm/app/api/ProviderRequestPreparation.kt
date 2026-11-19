package com.jarm.app.api

import com.jarm.app.api.util.prepareMessages
import com.jarm.app.api.util.tokens.ContextCostModel
import com.jarm.app.api.util.tokens.ContextCostModels
import com.jarm.app.model.ChatMessage

suspend fun ProviderConfig.resolveRequest(
    messages: List<ChatMessage>,
    fallbackCosts: ContextCostModel = ContextCostModels.forModel(modelId),
): ProviderRequestInput {
    val resolved = requestResolver?.resolve(messages, this)
        ?: ProviderRequestInput(
            messages = prepareMessages(
                messages,
                maxContextWindow,
                includeAssistantReasoning = includeAssistantReasoning,
                // Local execution supplies its resident tokenizer; cloud requests use model rules.
                // Trimming and the indicator must use the same selected-model cost configuration.
                costs = fallbackCosts,
            ),
            systemPrompt = systemPrompt,
        )
    // Custom request resolvers must obey the same wire-name compatibility boundary.
    return resolved.copy(messages = com.jarm.app.api.util.canonicalToolNames(resolved.messages))
}
