package com.jarm.app.api.util

import com.jarm.app.api.ProviderConfig
import com.jarm.app.model.ModelThinkingCapabilities
import com.jarm.app.model.ResolvedThinking
import com.jarm.app.model.ThinkingProviderFamily
import com.jarm.app.model.ThinkingResolution

/**
 * Single place where a request's thinking settings meet the selected model's capability.
 *
 * Providers call this instead of interpreting model names. The result never blocks a request: a
 * model that cannot disable thinking receives its lowest accepted setting, and an effort the model
 * does not accept is resolved to its nearest accepted level.
 */
internal fun ProviderConfig.resolvedThinking(family: ThinkingProviderFamily): ResolvedThinking =
    ThinkingResolution.resolve(
        capability = ModelThinkingCapabilities.forModel(
            family = family,
            modelId = modelId,
        ),
        requestedEnabled = thinkingEnabled,
        requestedEffort = thinkingLevel,
        requestedBudgetEnabled = thinkingBudgetEnabled,
        requestedBudgetTokens = thinkingBudgetTokens,
    )
