package com.jarm.app.ui.chat

import com.jarm.app.ui.common.JARMHaptics
import com.jarm.app.viewmodel.ChatViewModel

internal fun updateOpenAiNativeSearch(
    viewModel: ChatViewModel,
    conversationId: String?,
    haptics: JARMHaptics,
    enabled: Boolean,
) {
    haptics.toggle(enabled)
    viewModel.updateConversationSetting(conversationId) {
        it.copy(openAiWebSearchEnabled = enabled)
    }
}
