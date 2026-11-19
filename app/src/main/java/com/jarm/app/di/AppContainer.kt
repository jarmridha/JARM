package com.jarm.app.di

import android.app.Application
import android.content.Context
import com.jarm.app.PHONE_USE_SUPPORTED
import com.jarm.app.data.MemoryManager
import com.jarm.app.data.SkillManager
import com.jarm.app.data.SettingsManager
import com.jarm.app.data.local.ChatDao
import com.jarm.app.data.local.ChatDatabase
import com.jarm.app.data.local.semanticModelSnapshot
import com.jarm.app.data.repository.ConversationRepository
import com.jarm.app.data.repository.ConversationSettingsTransferCoordinator
import com.jarm.app.data.repository.SettingsRepository
import com.jarm.app.data.repository.TaskRepository
import com.jarm.app.data.AutoBackupManager
import com.jarm.app.api.LocalModelRuntime
import com.jarm.app.api.local.LocalProvider
import com.jarm.app.automation.AutomationScheduler
import com.jarm.app.automation.AutomationExecutionGate
import com.jarm.app.automation.ConversationExecutionCoordinator
import com.jarm.app.automation.LoopManager
import com.jarm.app.automation.TaskExecutionEngine
import com.jarm.app.automation.TaskManager
import com.jarm.app.tool.AutomationToolProvider
import com.jarm.app.tool.McpToolProvider
import com.jarm.app.mcp.McpRegistry
import com.jarm.app.sandbox.SandboxManagerFactory
import com.jarm.app.service.MaintenanceDebtWorker
import com.jarm.app.service.AskUserNotifier
import com.jarm.app.service.ShellConfirmationNotifier
import com.jarm.app.service.TaskWorker
import com.jarm.app.viewmodel.ChatRuntime
import com.jarm.app.viewmodel.ChatViewModel
import com.jarm.app.viewmodel.ChatViewModelFactory
import com.jarm.app.viewmodel.ConversationStateRegistry
import com.jarm.app.viewmodel.ProviderRegistry
import com.jarm.app.viewmodel.ShellConfirmationController
import com.jarm.app.data.dataStore
import com.jarm.app.webui.WebUiController
import com.jarm.app.webui.WebUiSettingsStore
import com.jarm.app.util.appLanguageResources
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Centralized dependency container (manual DI).
 *
 * Replaces the ad-hoc dependency creation previously spread across MainActivity.
 * The validated database is injected by JARMApplication's startup gate; all shared
 * dependencies are then created once and reused.
 *
 * This is a stepping stone toward a full DI framework (Hilt/Koin);
 * for a single-module project it provides sufficient decoupling and
 * testability without annotation processing overhead.
 */
class AppContainer(
    private val appContext: Context,
    val database: ChatDatabase,
) {
    private val application = appContext.applicationContext as Application

    private fun currentAppVersion(): String =
        try {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }

    init {
        LocalModelRuntime.initialize(application.applicationInfo.nativeLibraryDir)
    }

    /** App-lifetime scope that backs the shared settings StateFlows.
     *  The handler is the last line of defense: children launched directly on this scope
     *  (settings sync, scheduler, task runners) have no other parent to report to, and an
     *  uncaught exception here would otherwise kill the whole process. */
    private val appScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default +
            kotlinx.coroutines.CoroutineExceptionHandler { _, e ->
                com.jarm.app.util.DebugLog.e("AppContainer", "Uncaught in appScope", e)
            }
    )

    // ── Data Layer ────────────────────────────────────────────

    val settingsManager: SettingsManager by lazy { SettingsManager(appContext) }
    val memoryManager: MemoryManager by lazy { MemoryManager(appContext) }
    val skillManager: SkillManager by lazy { SkillManager(appContext) }
    val chatDao: ChatDao by lazy { database.chatDao() }

    // ── Repositories ──────────────────────────────────────────

    val conversationRepository: ConversationRepository by lazy {
        ConversationRepository(
            chatDao = chatDao,
            database = database,
            snapshotContext = appContext,
            subagentGenerationIds = conversationExecutionCoordinator.admittedGenerationIds,
            generationErrorFormatter = { raw ->
                com.jarm.app.viewmodel.normalizePersistedGenerationErrorText(
                    appContext, raw.ifBlank { appContext.getString(com.jarm.app.R.string.failed_to_generate) },
                )
            },
            semanticModelSnapshotProvider = {
                settingsRepository.awaitInitialLoad()
                semanticModelSnapshot(
                    activeModelId = settingsRepository.activeEmbeddingModelId.value,
                    configuredModelIds = settingsRepository.embeddingModels.value.map { it.id },
                )
            },
        )
    }

    @Volatile
    private var processServicesStarted = false

    /** Starts necessary process work after the narrow conversation list has published. */
    @Synchronized
    fun startProcessServices() {
        if (processServicesStarted) return
        providerRegistry.ensureStarted()
        taskManager.start()
        automationScheduler.start()
        processServicesStarted = true
        appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (database.maintenanceDebtDao().hasDebt()) MaintenanceDebtWorker.schedule()
            } catch (error: Exception) {
                com.jarm.app.util.DebugLog.e(
                    "AppContainer",
                    "Failed to schedule maintenance debt",
                    error,
                )
            }
        }
        appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            conversationSettingsTransfers.replayPending()
        }
    }
    val taskRepository: TaskRepository by lazy {
        TaskRepository(chatDao)
    }
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(
            settingsManager,
            appScope,
            touchConversationData = { conversationId ->
                conversationRepository.touchConversationData(conversationId)
            },
        ).also {
            LocalModelRuntime.bindIdleRetention(it.localModelIdleRetentionMinutes, appScope)
        }
    }
    val conversationSettingsTransfers: ConversationSettingsTransferCoordinator by lazy {
        ConversationSettingsTransferCoordinator(conversationRepository, settingsRepository)
    }

    /** One process-wide ask_user queue, so a background run can reach the same interaction bar. */
    val askUserController: com.jarm.app.viewmodel.AskUserController by lazy {
        com.jarm.app.viewmodel.AskUserController().also {
            // The interaction bar only exists inside the chat screen, so a question asked from
            // anywhere else needs the notification to stay answerable.
            AskUserNotifier.start(appScope, appContext, it)
            // Answers to non-blocking questions leave through the send queue. The controller is
            // process-wide, so its answers get exactly one process-wide collector; one per chat
            // screen delivered each answer once per live screen.
            com.jarm.app.viewmodel.DeferredAskUserAnswerDelivery(
                askUser = it,
                registry = conversationStateRegistry,
                scope = appScope,
                conversationModelId = { id -> conversationRepository.getConversation(id)?.modelId },
                fallbackModelId = { settingsRepository.selectedModel.value },
            ).start()
        }
    }

    /** One process-wide confirmation queue shared by Chat, Task, and Loop generation. */
    val shellConfirmationController: ShellConfirmationController by lazy {
        ShellConfirmationController(settingsRepository).also {
            // Observe from the moment the queue exists: a background automation run must be able
            // to offer its decision on the notification shade without any Activity having started.
            ShellConfirmationNotifier.start(appScope, appContext, it)
        }
    }

    // ── Generation singletons (process-scoped) ────────────────
    // Shared by both the foreground ChatViewModel and background task execution.
    // [localProvider] must be unique per process; LocalModelRuntime owns the one embedded model
    // lifecycle. [providerRegistry] holds the live provider map the
    // generation pipeline reads and runs the long-lived credential/model sync jobs.

    val localProvider: LocalProvider by lazy { LocalProvider(appContext, settingsRepository) }

    val providerRegistry: ProviderRegistry by lazy {
        ProviderRegistry(
            settingsRepository,
            conversationRepository,
            localProvider,
            appScope,
            currentAppVersion(),
        )
    }

    /** Serializes every foreground/background generation touching the same conversation. */
    val conversationExecutionCoordinator: ConversationExecutionCoordinator by lazy {
        ConversationExecutionCoordinator(
            parentConversationId = { id ->
                chatDao.observeParentConversationId(id).first()?.also {
                    settingsRepository.awaitInitialLoad()
                }
            },
            subagentMaxConcurrent = settingsRepository.subagentMaxConcurrent,
        )
    }

    /** Foreground generation slots survive Activity/ViewModel recreation within this process. */
    private val phoneLease by lazy { com.jarm.app.phone.PhoneControlLease(
        recordRelease = { release -> com.jarm.app.util.DebugLog.phoneRelease(
            release.reason, release.epoch, release.heldMs, release.mode) },
        now = android.os.SystemClock::elapsedRealtime,
    ) }
    internal val phoneUse by lazy {
        com.jarm.app.phone.PhoneUseRuntime(appContext, settingsRepository, phoneLease,
            askUserController, conversationStateRegistry, conversationRepository, appScope)
    }
    val conversationStateRegistry: ConversationStateRegistry by lazy {
        ConversationStateRegistry(
            onRunStateChanged = phoneLease::onRunStateChanged,
            onRunHandoffFinished = phoneLease::finishHandoff,
        ) { attachments ->
            appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                conversationRepository.deleteUnreferencedDraftAttachmentFiles(attachments)
            }
        }
    }

    val mcpRegistry: McpRegistry by lazy {
        McpRegistry(appContext, settingsRepository, appScope)
    }

    val mcpToolProvider: McpToolProvider by lazy {
        McpToolProvider(mcpRegistry)
    }

    /** Lets native import quiesce Task/Loop generation without serializing ordinary executions. */
    val automationExecutionGate: AutomationExecutionGate by lazy { AutomationExecutionGate() }

    // ── Sandbox (flavor-specific) ─────────────────────────────

    val sandboxManagerFactory: SandboxManagerFactory? by lazy {
        try {
            // fdroid flavor provides FdroidSandboxManagerFactory
            Class.forName("com.jarm.app.sandbox.FdroidSandboxManagerFactory")
                .getDeclaredConstructor(
                    android.content.Context::class.java,
                    com.jarm.app.data.repository.SettingsRepository::class.java,
                )
                .newInstance(appContext, settingsRepository) as SandboxManagerFactory
        } catch (_: ClassNotFoundException) {
            // play flavor provides PlaySandboxManagerFactory
            try {
                Class.forName("com.jarm.app.sandbox.PlaySandboxManagerFactory")
                    .getDeclaredConstructor()
                    .newInstance() as SandboxManagerFactory
            } catch (_: ClassNotFoundException) {
                null
            } catch (e: Exception) {
                // Class exists but failed to construct — this is a real error, not a flavor miss.
                com.jarm.app.util.DebugLog.e("AppContainer", "PlaySandboxManagerFactory init failed", e)
                null
            }
        } catch (e: Exception) {
            // FdroidSandboxManagerFactory exists but failed to construct.
            com.jarm.app.util.DebugLog.e("AppContainer", "FdroidSandboxManagerFactory init failed", e)
            null
        }
    }

    // ── Headless task execution (process-scoped) ──────────────
    // Drives a full generation with no ViewModel/UI, reusing the shared generation
    // singletons above. Background Task/Loop runners call its runOnce(...).

    val taskExecutionEngine: TaskExecutionEngine by lazy {
        TaskExecutionEngine(
            application = application,
            appContext = appContext,
            convRepo = conversationRepository,
            settings = settingsRepository,
            memoryManager = memoryManager,
            skillManager = skillManager,
            providerRegistry = providerRegistry,
            localProvider = localProvider,
            sandboxFactory = sandboxManagerFactory,
            appScope = appScope,
            executionCoordinator = conversationExecutionCoordinator,
            shellConfirmation = shellConfirmationController,
            automationExecutionGate = automationExecutionGate,
            mcpToolProvider = mcpToolProvider,
            askUser = askUserController,
            generationRegistry = conversationStateRegistry,
            pauseConversationLoop = { conversationId -> loopManager.stopLoop(conversationId) },
            additionalTools = {
                listOfNotNull(
                    automationToolProvider,
                    subagentToolProvider,
                    if (PHONE_USE_SUPPORTED) phoneUse.provider else null,
                )
            },
            phoneReady = { PHONE_USE_SUPPORTED && phoneUse.readiness(it) },
        )
    }

    internal val subagentCompletionDelivery by lazy {
        com.jarm.app.viewmodel.SubagentCompletionQueueDelivery(
            conversationRepository, conversationStateRegistry,
            fallbackModelId = { settingsRepository.selectedModel.value },
        )
    }

    val subagentManager: com.jarm.app.automation.SubagentManager by lazy {
        com.jarm.app.automation.SubagentManager(
            conversations = conversationRepository,
            settings = settingsRepository,
            transfers = conversationSettingsTransfers,
            engine = taskExecutionEngine,
            registry = conversationStateRegistry,
            coordinator = conversationExecutionCoordinator,
            gate = automationExecutionGate,
            scope = appScope,
            enqueueCompletions = { parentId ->
                // Evaluated only on a completion/open event, after AppContainer lazy creation.
                chatRuntime
                subagentCompletionDelivery.deliver(parentId)
            },
            enqueueMessage = { id, text, context -> chatRuntime.enqueueSubagentMessage(id, text, context) },
            stopLoop = { loopManager.stopLoop(it) },
            deleteIdleChild = { id, parentId -> chatRuntime.deleteIdleSubagent(id, parentId) },
        )
    }
    val subagentToolProvider: com.jarm.app.tool.SubagentToolProvider by lazy {
        com.jarm.app.tool.SubagentToolProvider(subagentManager)
    }

    val taskManager: TaskManager by lazy {
        TaskManager(
            taskRepository = taskRepository,
            conversationRepository = conversationRepository,
            engine = taskExecutionEngine,
            scope = appScope,
            cancelScheduledExecution = { taskId ->
                TaskWorker.cancel(appContext, taskId)
                automationScheduler.cancelTask(taskId)
            },
            cancelConversationLoop = { conversationId ->
                loopManager.stopLoop(conversationId)
            },
            refreshScheduling = { automationScheduler.refresh() },
            conversationExecutionCoordinator = conversationExecutionCoordinator,
            automationExecutionGate = automationExecutionGate,
            titleExecutionConversation = taskExecutionEngine::updateTaskExecutionTitle,
        )
    }

    val loopManager: LoopManager by lazy {
        LoopManager(
            taskRepository = taskRepository,
            conversationRepository = conversationRepository,
            engine = taskExecutionEngine,
            cancelWork = { conversationId ->
                com.jarm.app.service.LoopWorker.cancel(appContext, conversationId)
            },
            cancelAlarm = { conversationId -> automationScheduler.cancelLoop(conversationId) },
            executionCoordinator = conversationExecutionCoordinator,
            executionGate = automationExecutionGate,
        )
    }

    /** Scheduled contexts disable this provider; delegated contexts inherit the parent's switch. */
    val automationToolProvider: AutomationToolProvider by lazy {
        AutomationToolProvider(taskManager, loopManager, { settingsManager.systemPrompts.first() }) {
            settingsManager.automationToolsEnabled.first()
        }
    }

    val automationScheduler: AutomationScheduler by lazy {
        AutomationScheduler(appContext, taskRepository, settingsRepository, appScope)
    }

    // ── Auto Backup ───────────────────────────────────────────

    val autoBackupManager: AutoBackupManager by lazy {
        AutoBackupManager(appContext, settingsManager, memoryManager, skillManager)
    }

    // Process-scoped chat runtime shared by every client (phone UI now, WebUI later).
    val chatRuntime: ChatRuntime by lazy {
        ChatRuntime(
            application = application,
            appContext = appContext,
            conversations = conversationRepository,
            settings = settingsRepository,
            memoryManager = memoryManager,
            skillManager = skillManager,
            sandboxFactory = sandboxManagerFactory,
            additionalToolProviders = run {
                // Capture the same process owners now; build the list and Ask User wrapper lazily.
                val phone = if (PHONE_USE_SUPPORTED) phoneUse.provider else null
                val automation = automationToolProvider
                val subagent = subagentToolProvider
                val mcp = mcpToolProvider
                val askUser = askUserController
                return@run {
                    listOfNotNull(
                        phone,
                        automation,
                        subagent,
                        mcp,
                        com.jarm.app.tool.AskUserToolProvider(askUser),
                    )
                }
            },
            phoneReady = { PHONE_USE_SUPPORTED && phoneUse.readiness(it) },
            onConversationOpened = subagentManager::conversationOpened,
            onConversationIdle = subagentManager::conversationIdle,
            onConfirmShellCommand = { server, summary, conversationId, detail ->
                shellConfirmationController.confirm(server, summary, conversationId, detail)
            },
            registry = conversationStateRegistry,
            providerRegistry = providerRegistry,
            localProvider = localProvider,
            executionCoordinator = conversationExecutionCoordinator,
            stopConversationLoop = { loopManager.stopLoop(it) },
            scope = appScope,
        ).also { runtime ->
            // Capture this completed runtime; do not re-enter the chatRuntime lazy initializer.
            com.jarm.app.viewmodel.ForegroundAutomationBridgeController(
                isConversationOpen = runtime.clients::isConversationOpen,
                send = { conversationId, userText, modelId, requestKind ->
                    runtime.messageGeneration.sendMessageFromAutomationAwaitingCompletion(
                        conversationId,
                        userText,
                        modelId,
                        requestKind,
                    )
                },
                loadMessage = conversationRepository::getMessage,
                attach = taskExecutionEngine::attachForegroundSendBridge,
                detach = taskExecutionEngine::detachForegroundSendBridge,
            ).start()
        }
    }
    // WebUI remote control: settings, authentication and the embedded server.
    internal val webUi: WebUiController by lazy {
        val previewMedia = com.jarm.app.ui.chat.message.ShellMarkdownMedia(
            context = appContext,
            devices = { settingsRepository.shellDevices.value },
            sandboxEnabled = { settingsRepository.sandboxEnabled.value },
            sandbox = { sandboxManagerFactory?.takeIf { it.isAvailable() }?.create() },
            scope = appScope,
        )
        WebUiController(
            appContext = appContext,
            store = WebUiSettingsStore(appContext.dataStore),
            scope = appScope,
            // No-backup storage: the TLS key never leaves the device.
            certificates = com.jarm.app.webui.WebUiCertificateStore(
                directory = java.io.File(appContext.noBackupFilesDir, "webui"),
                seal = com.jarm.app.util.SecretCrypto::encrypt,
                unseal = com.jarm.app.util.SecretCrypto::decrypt,
                addresses = WebUiController::interfaceAddresses,
            ),
            syncSession = { login, incoming, send -> webUiSync.serve(login, incoming, send) },
            upload = { login, id, seq, name, mime, type, size, input -> webUiSync.upload(login, id, seq, name, mime, type, size, input) },
            previewAttachment = { login, connection, seq, id, kind, index, consume ->
                webUiSync.previewAttachment(login, connection, seq, id, kind, index, consume)
            },
            toolImages = com.jarm.app.webui.WebUiToolImages(
                directory = java.io.File(appContext.filesDir, "tool-media"),
                attachmentDirectory = appContext.filesDir,
                markdownImage = previewMedia::previewImage,
                loadMessage = { conversationId, messageId ->
                    val persisted = conversationRepository.getMessagesByIds(listOf(messageId))
                        .any { it.id == messageId && it.conversationId == conversationId }
                    if (persisted) conversationStateRegistry.get(conversationId)?.generationSnapshot?.value
                        ?.streamingMessage?.takeIf { it.id == messageId }
                        ?: com.jarm.app.viewmodel.ConversationMessagePayloadHydration(
                            conversations = conversationRepository,
                            appContext = appContext,
                        ).loadMessages(conversationId, listOf(messageId)).singleOrNull()
                    else null
                },
            ),
            htmlPreviewImage = { previewMedia.previewImage(it) },
        )
    }
    // Built on the first browser connection, so app start never touches conversation data here.
    private val webUiSync: com.jarm.app.webui.WebUiSync by lazy {
        com.jarm.app.webui.WebUiSync(
            conversations = conversationRepository,
            registry = conversationStateRegistry,
            executionCoordinator = conversationExecutionCoordinator,
            hydration = com.jarm.app.viewmodel.ConversationMessagePayloadHydration(
                conversations = conversationRepository,
                appContext = appContext,
            ),
            customProviders = settingsRepository.customProviders,
            openChatSession = { scope ->
                com.jarm.app.webui.WebUiChatSession(
                    generation = chatRuntime.messageGeneration,
                    generationStop = chatRuntime.generationStop,
                    clients = chatRuntime.clients,
                    conversations = conversationRepository,
                    registry = conversationStateRegistry,
                    executionCoordinator = conversationExecutionCoordinator,
                    settings = settingsRepository,
                    transfers = conversationSettingsTransfers,
                    attachmentProcessor = com.jarm.app.viewmodel.AttachmentImportProcessor(application),
                    scope = scope,
                    uploadDirectory = appContext.filesDir,
                    compactFailureMessage = { com.jarm.app.viewmodel.compactFailureMessage(appContext, it) },
                    onConversationOpened = chatRuntime.onConversationOpened,
                    allowLocalSandbox = { sandboxManagerFactory?.isAvailable() == true && settingsRepository.sandboxEnabled.value },
                    sandboxHomeDir = { sandboxManagerFactory?.takeIf { it.isAvailable() }?.let { java.io.File(appContext.filesDir, "sandbox-home") } },
                )
            },
            forkShare = chatRuntime.conversationForkShare,
            conversationLifecycle = chatRuntime.conversationLifecycle,
            generation = chatRuntime.messageGeneration,
            askUser = askUserController,
            shellConfirmation = shellConfirmationController,
            contextAccounting = com.jarm.app.webui.WebUiContextAccounting(
                conversations = conversationRepository,
                appContext = appContext,
                requestBuilder = chatRuntime.requestBuilder,
                generationManager = { chatRuntime.generationManager },
                generationErrorFormatter = { raw ->
                    com.jarm.app.viewmodel.normalizePersistedGenerationErrorText(appContext, raw)
                },
                compactThresholdPercent = settingsRepository.contextCompactThresholdPercent,
                compactEnabled = settingsRepository.contextCompactEnabled,
            ),
            search = { query ->
                if (settingsRepository.manualSearchMethod.value == com.jarm.app.util.Constants.SEARCH_METHOD_RAG) {
                    com.jarm.app.viewmodel.SemanticSearchService(
                        settings = settingsRepository,
                        activeEmbeddingConfig = { chatRuntime.ragManager.activeEmbeddingModel.value },
                        resolveEmbeddingApiKey = chatRuntime.ragManager::resolveEmbeddingApiKey,
                        search = chatRuntime.generationManager::semanticSearch,
                    ).search(query, 20)
                } else conversationRepository.searchMessages(query, 20).map { it to 0f }
            },
            display = kotlinx.coroutines.flow.combine(
                settingsRepository.appLanguage,
                settingsRepository.toolCallDisplayMode,
                settingsRepository.thinkingSegmentDisplayMode,
                settingsRepository.autoExpandActiveGroup,
                // combine takes at most five typed flows, so the two Markdown switches travel as a pair.
                kotlinx.coroutines.flow.combine(
                    settingsRepository.parseInlineDollarMath,
                    settingsRepository.autoWrapCodeBlocks,
                    ::Pair,
                ),
            ) { language, toolMode, thinkingMode, autoExpand, (inlineMath, autoWrap) ->
                com.jarm.app.webui.WebDisplayContext(
                    resources = appContext.appLanguageResources(language),
                    toolCallDisplayMode = toolMode,
                    thinkingSegmentDisplayMode = thinkingMode,
                    autoExpandActiveGroup = autoExpand,
                    parseInlineDollarMath = inlineMath,
                    autoWrapCodeBlocks = autoWrap,
                )
            }.combine(
                kotlinx.coroutines.flow.combine(
                    settingsRepository.blurEffectsEnabled,
                    settingsRepository.reduceMotion,
                    ::Pair,
                ),
            ) { context, (blur, reduceMotion) ->
                context.copy(blurEffectsEnabled = blur, reduceMotion = reduceMotion)
            }.combine(
                kotlinx.coroutines.flow.combine(
                    settingsRepository.modelAliases,
                    settingsRepository.modelProviderNames,
                    ::Pair,
                ),
            ) { context, (aliases, providerNames) ->
                context.copy(modelAliases = aliases, modelProviderNames = providerNames)
            }.combine(
                settingsRepository.subagentCapsuleDisplayMode,
            ) { context, capsuleDisplayMode ->
                context.copy(subagentCapsuleDisplayMode = capsuleDisplayMode)
            }.combine(
                settingsRepository.stickToBottom,
            ) { context, stickToBottom ->
                context.copy(stickToBottom = stickToBottom)
            },
        )
    }
    // ── ViewModel Factory ─────────────────────────────────────

    fun chatViewModelFactory(): ChatViewModelFactory =
        ChatViewModelFactory(
            application, database, chatDao, settingsManager, memoryManager, skillManager, appContext, sandboxManagerFactory,
            autoBackupManager, conversationRepository, settingsRepository, conversationSettingsTransfers,
            ::startProcessServices, providerRegistry,
            taskManager, loopManager, conversationExecutionCoordinator,
            automationExecutionGate, conversationStateRegistry, shellConfirmationController,
            askUserController,
            mcpRegistry, chatRuntime,
        )
}
