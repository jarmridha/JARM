package com.jarm.app.automation

import android.content.Context
import com.jarm.app.data.repository.ConversationRepository
import com.jarm.app.model.ChatMessage
import com.jarm.app.model.MessageStatus
import com.jarm.app.model.RunEffect
import com.jarm.app.viewmodel.AutomaticCompactContinuationRequest
import com.jarm.app.viewmodel.BoundRunGenerationLauncher
import com.jarm.app.viewmodel.ContextCompactor
import com.jarm.app.viewmodel.ConversationCompactController
import com.jarm.app.viewmodel.GenerationManager
import com.jarm.app.viewmodel.GenerationFinalizer
import com.jarm.app.viewmodel.GenerationTerminalSettlementController
import com.jarm.app.viewmodel.ConversationGenerationState
import com.jarm.app.viewmodel.StandardGenerationContinuationLauncher
import com.jarm.app.viewmodel.StandardGenerationContinuationRequest
import com.jarm.app.viewmodel.automaticCompactAllowsHandoff
import com.jarm.app.viewmodel.launchStandardContinuationAfterGuidance
import com.jarm.app.viewmodel.toUiChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

internal suspend fun GenerationFinalizer.settleTaskStopEffect(
    state: ConversationGenerationState,
    effect: RunEffect.FinalizeStop,
    messages: List<ChatMessage>,
) = withContext(NonCancellable) {
    launchStopFinalization(
        scope = state.scope,
        identity = effect.identity,
        messages = messages,
    ) { completion ->
        val result = state.finishStopFinalization(completion)
        if (result.accepted && completion.success) state.clearStoppedOverlay()
    }.join()
}

internal data class StandardCompactContinuationResult(
    val modelMessageId: String?,
    val aborted: Boolean = false,
)

/**
 * Runs every post-tool boundary as ordinary generations: terminal Assistant -> Compact Run ->
 * fresh Assistant Run. No provider stream, Assistant row, or Run identity is resumed.
 */
internal suspend fun ConversationCompactController.continueTaskGenerations(
    initialRequest: AutomaticCompactContinuationRequest,
    state: ConversationGenerationState,
    convRepo: ConversationRepository,
    executionCoordinator: ConversationExecutionCoordinator,
    terminalSettlement: GenerationTerminalSettlementController,
    generationManager: GenerationManager,
    contextCompactor: ContextCompactor,
    appContext: Context,
): StandardCompactContinuationResult {
    val pendingRequest = AtomicReference<AutomaticCompactContinuationRequest?>()
    lateinit var boundLauncher: BoundRunGenerationLauncher
    val continuationLauncher = StandardGenerationContinuationLauncher(
        conversations = convRepo,
        executionCoordinator = executionCoordinator,
        terminalSettlement = terminalSettlement,
        boundRunGenerationLauncher = { boundLauncher },
        toUiMessage = { it.toUiChatMessage(appContext) },
        isConversationOpen = { false },
        projectGraph = { _, _, _, _ -> },
    )
    boundLauncher = BoundRunGenerationLauncher(
        conversations = convRepo,
        generationManagerProvider = { generationManager },
        automaticCompactNeeded = contextCompactor::automaticNeeded,
        terminalSettlement = terminalSettlement,
        toUiMessage = { it.toUiChatMessage(appContext) },
        onAutomaticCompactContinuation = { request, generationState ->
            generationState.retainRunHandoff(request.generationRequest.uiToken)
            generationState.deferNextQueueDrain()
            check(pendingRequest.compareAndSet(null, request)) {
                "A standard generation produced overlapping continuation requests"
            }
        },
    )

    var originOwnerToken = initialRequest.generationRequest.uiToken
    try {
        var request: AutomaticCompactContinuationRequest? = initialRequest
        var lastModelMessageId: String? = null
        while (request != null) {
            val current = request
            originOwnerToken = current.generationRequest.uiToken
            val guidanceClaimRevision = state.guidanceClaimRevision()
            val compactLaunch = startAutomaticStandard(
                conversationId = current.generationRequest.conversationId,
                contextLimit = current.generationRequest.snapshot.config.maxContextWindow,
                config = current.config,
                state = state,
                // The Task run already holds this conversation's automation lease.
                alreadyHoldsConversationLock = true,
                continuesAfterSuccess = true,
            ) ?: return StandardCompactContinuationResult(lastModelMessageId, aborted = true)
            try {
                compactLaunch.job.join()
                state.awaitGenerationSettled(compactLaunch.uiToken)
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) {
                    compactLaunch.job.cancel(cancelled)
                    compactLaunch.job.join()
                }
                state.finishRunHandoff(compactLaunch.uiToken)
                throw cancelled
            }
            val launch = try {
                val compactStatus = convRepo.getMessage(compactLaunch.messageId)?.status
                if (!automaticCompactAllowsHandoff(compactStatus)) {
                    state.finishRunHandoff(compactLaunch.uiToken)
                    return StandardCompactContinuationResult(lastModelMessageId, aborted = true)
                }
                launchStandardContinuationAfterGuidance(
                    state = state,
                    guidanceClaimRevision = guidanceClaimRevision,
                    handoffOwnerToken = compactLaunch.uiToken,
                ) {
                    pendingRequest.set(null)
                    continuationLauncher.launch(
                        request = StandardGenerationContinuationRequest(
                            conversationId = current.generationRequest.conversationId,
                            parentMessageId = compactLaunch.messageId,
                            snapshot = current.generationRequest.snapshot,
                            alreadyHoldsConversationLock = true,
                            touchConversationOnAdmission = false,
                        ),
                        state = state,
                    )
                }
            } catch (error: Exception) {
                state.finishRunHandoff(compactLaunch.uiToken)
                throw error
            } ?: return StandardCompactContinuationResult(lastModelMessageId)
            try {
                launch.job.join()
                state.awaitGenerationSettled(launch.uiToken)
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) {
                    launch.job.cancel(cancelled)
                    launch.job.join()
                }
                state.finishRunHandoff(launch.uiToken)
                throw cancelled
            }
            val continuationMessage = convRepo.getMessage(launch.modelMessageId)
            if (continuationMessage?.status == MessageStatus.STOPPED) {
                return StandardCompactContinuationResult(lastModelMessageId, aborted = true)
            }
            if (continuationMessage != null) lastModelMessageId = launch.modelMessageId
            request = pendingRequest.getAndSet(null)
        }
        return StandardCompactContinuationResult(lastModelMessageId)
    } finally {
        state.finishRunHandoff(originOwnerToken)
        pendingRequest.get()?.let { state.finishRunHandoff(it.generationRequest.uiToken) }
    }
}
