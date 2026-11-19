package com.jarm.app.viewmodel

import com.jarm.app.api.HttpClient
import com.jarm.app.api.LlmProvider
import com.jarm.app.api.ProviderConfig
import com.jarm.app.api.StreamEvent
import com.jarm.app.model.ChatMessage
import com.jarm.app.model.ProviderPassResult
import com.jarm.app.model.RunEffect
import com.jarm.app.model.RunEffectIdentity
import kotlinx.coroutines.CancellationException

internal data class ProviderPassExecutionRequest(
    val proposedIdentity: RunEffectIdentity,
    val provider: LlmProvider,
    val messages: List<ChatMessage>,
    val config: ProviderConfig,
    val requestTrace: HttpClient.RequestTrace? = null,
)

internal data class ProviderPassExecutionCallbacks(
    val requestEffect: suspend (RunEffectIdentity) -> RunEffect.StartProviderPass?,
    val returnConsumerFailure: suspend (RunEffectIdentity, ProviderPassResult) -> Unit,
    val onFirstEvent: (() -> Unit)?,
    val onEvent: suspend (StreamEvent) -> Unit,
)

/** Executes exactly one mailbox-authorized, protocol-validated Provider pass. */
internal class ProviderPassEffectExecutor(
    private val runner: ProviderPassRunner = ProviderPassRunner(),
) {
    suspend fun execute(
        request: ProviderPassExecutionRequest,
        callbacks: ProviderPassExecutionCallbacks,
    ): ProviderPassOutcome {
        val startEffect = callbacks.requestEffect(request.proposedIdentity)
            ?.takeIf { it.identity == request.proposedIdentity }
            ?: throw CancellationException(
                "Provider pass ${request.proposedIdentity.effectId} is no longer authorized",
            )
        var firstEventPending = callbacks.onFirstEvent != null
        try {
            return HttpClient.withStreamScope(
                scope = HttpClient.boundStreamScope(),
                requestTrace = request.requestTrace,
            ) {
                runner.run(
                    identity = startEffect.identity,
                    provider = request.provider,
                    messages = request.messages,
                    config = request.config,
                ) { event ->
                    request.requestTrace?.recordParsedEvent(event)
                    if (firstEventPending) {
                        firstEventPending = false
                        callbacks.onFirstEvent?.invoke()
                    }
                    callbacks.onEvent(event)
                }
            }
        } catch (error: Exception) {
            // Runner normally closes failures into an outcome. A consumer failure must still close
            // the exact Running pass so the mailbox never retains a phantom Provider operation.
            callbacks.returnConsumerFailure(startEffect.identity, ProviderPassResult.FAILED)
            throw error
        }
    }
}

internal fun ProviderPassOutcome.resultType(): ProviderPassResult = when (this) {
    is ProviderPassOutcome.CompletedText -> ProviderPassResult.COMPLETED_TEXT
    is ProviderPassOutcome.CompletedToolCalls -> ProviderPassResult.COMPLETED_TOOL_CALLS
    is ProviderPassOutcome.Truncated -> ProviderPassResult.TRUNCATED
    is ProviderPassOutcome.Failed -> ProviderPassResult.FAILED
    is ProviderPassOutcome.Cancelled -> ProviderPassResult.CANCELLED
}
