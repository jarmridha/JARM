package com.jarm.app.data.local

import com.jarm.app.model.MessageStatus
import com.jarm.app.model.Participant

/** Payload-free durable fields used to resolve one canonical selected Provider path. */
data class MessageContextTopology(
    val id: String,
    val conversationId: String,
    val parentId: String?,
    val status: MessageStatus,
    val participant: Participant,
    val timestamp: Long,
    val tokenCount: Int = 0,
    val modelName: String?,
    val runId: String,
    val runSequence: Long,
    val consumedAtPass: Int?,
)

/**
 * Payload-free row behind a task's execution list. [preview] is a bounded text prefix, so one
 * oversized message cannot exceed the Android CursorWindow while the list is read.
 */
data class ExecutionMessageSummaryRow(
    val id: String,
    val conversationId: String,
    val participant: Participant,
    val status: MessageStatus,
    val timestamp: Long,
    val preview: String,
)

/** Characters of message text an execution row reads; the list shows at most two lines. */
const val EXECUTION_PREVIEW_MAX_CHARS = 500

data class ConversationProviderContextState(
    val selectedBranchesJson: String?,
)

data class ProviderContextTopologySnapshot(
    val selectedBranchesJson: String?,
    val messages: List<MessageContextTopology>,
)
