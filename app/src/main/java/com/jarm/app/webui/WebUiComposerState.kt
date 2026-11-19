package com.jarm.app.webui

import com.jarm.app.data.ConversationSettings
import com.jarm.app.data.SystemPromptEntry
import com.jarm.app.model.SelectedAttachment
import com.jarm.app.presentation.EffectiveConversationControls
import com.jarm.app.viewmodel.CompactRequest
import com.jarm.app.viewmodel.ConversationComposerSubmissionSnapshot
import com.jarm.app.viewmodel.QueuedSend

/** The composer owner the browser shows and that owner's submission phase. */
internal data class WebUiComposerState(
    val conversationId: String?,
    val snapshot: ConversationComposerSubmissionSnapshot,
    val seq: Long = 0L,
    val text: String = "",
    val editRevision: Long = 0L,
    val actionId: Long = 0L,
    val modelValid: Boolean = false,
    val generating: Boolean = false,
    val stopping: Boolean = false,
    val modelId: String = "",
    val models: Map<String, String> = emptyMap(),
    val queue: List<QueuedSend> = emptyList(),
    val attachments: List<SelectedAttachment> = emptyList(),
    val pdfProgress: Map<String, Pair<Int, Int>> = emptyMap(),
    val controls: EffectiveConversationControls? = null,
    val generationParameters: ConversationSettings = ConversationSettings(),
    val generationDefaults: ConversationSettings = ConversationSettings(),
    val compactDefaults: CompactRequest? = null,
    val compacting: Boolean = false,
    val systemPromptId: String? = null,
    val systemPrompts: List<SystemPromptEntry> = emptyList(),
    val activeSystemPromptId: String? = null,
    val sandboxAttachments: Boolean = false,
)
