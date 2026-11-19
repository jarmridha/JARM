package com.jarm.app.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.jarm.app.data.CustomProviderConfig
import com.jarm.app.data.ConversationSettings
import com.jarm.app.model.OpenAiServiceTiers
import com.jarm.app.presentation.OpenAiConversationServiceTierState
import com.jarm.app.presentation.resolveOpenAiConversationServiceTier
import com.jarm.app.ui.common.JARMHaptics
import com.jarm.app.viewmodel.ChatViewModel

@Composable
internal fun openAiConversationServiceTierState(
    viewModel: ChatViewModel,
    conversationOverride: ConversationSettings?,
    providerName: String,
    builtInOpenAiResponsesEnabled: Boolean,
    customProviders: List<CustomProviderConfig>,
): OpenAiConversationServiceTierState {
    val globalEnabled by viewModel.settings.openAiServiceTierEnabled.collectAsState()
    val globalTier by viewModel.settings.openAiServiceTier.collectAsState()
    return resolveOpenAiConversationServiceTier(
        globalEnabled = globalEnabled,
        globalTier = globalTier,
        conversationOverride = conversationOverride,
        providerName = providerName,
        builtInOpenAiResponsesEnabled = builtInOpenAiResponsesEnabled,
        customProviders = customProviders,
    )
}

internal fun updateOpenAiConversationServiceTierEnabled(
    viewModel: ChatViewModel,
    conversationId: String?,
    haptics: JARMHaptics,
    enabled: Boolean,
) {
    haptics.toggle(enabled)
    viewModel.updateConversationSetting(conversationId) {
        it.copy(openAiServiceTierEnabled = enabled)
    }
}

internal fun updateOpenAiConversationServiceTier(
    viewModel: ChatViewModel,
    conversationId: String?,
    haptics: JARMHaptics,
    tier: String,
) {
    haptics.selection()
    viewModel.updateConversationSetting(conversationId) {
        it.copy(openAiServiceTier = OpenAiServiceTiers.normalize(tier))
    }
}
