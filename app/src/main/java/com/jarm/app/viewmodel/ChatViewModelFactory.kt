package com.jarm.app.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.jarm.app.data.AutoBackupManager
import com.jarm.app.data.MemoryManager
import com.jarm.app.data.SkillManager
import com.jarm.app.data.SettingsManager
import com.jarm.app.automation.TaskManager
import com.jarm.app.automation.LoopManager
import com.jarm.app.automation.ConversationExecutionCoordinator
import com.jarm.app.automation.AutomationExecutionGate
import com.jarm.app.mcp.McpRegistry
import com.jarm.app.data.local.ChatDao
import com.jarm.app.data.local.ChatDatabase
import com.jarm.app.data.repository.ConversationRepository
import com.jarm.app.data.repository.ConversationSettingsTransferCoordinator
import com.jarm.app.data.repository.SettingsRepository
import com.jarm.app.sandbox.SandboxManagerFactory

class ChatViewModelFactory(
    private val application: Application,
    private val database: ChatDatabase,
    private val chatDao: ChatDao,
    private val settingsManager: SettingsManager,
    private val memoryManager: MemoryManager,
    private val skillManager: SkillManager,
    private val context: Context,
    private val sandboxFactory: SandboxManagerFactory? = null,
    private val autoBackupManager: AutoBackupManager,
    private val conversationRepository: ConversationRepository,
    private val settingsRepository: SettingsRepository,
    private val conversationSettingsTransfers: ConversationSettingsTransferCoordinator,
    private val startProcessServices: () -> Unit,
    private val providerRegistry: ProviderRegistry,
    private val taskManager: TaskManager,
    private val loopManager: LoopManager,
    private val conversationExecutionCoordinator: ConversationExecutionCoordinator,
    private val automationExecutionGate: AutomationExecutionGate,
    private val conversationStateRegistry: ConversationStateRegistry,
    private val shellConfirmationController: ShellConfirmationController,
    private val askUserController: AskUserController,
    private val mcpRegistry: McpRegistry,
    private val chatRuntime: ChatRuntime,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(
                application, database, chatDao, settingsManager, memoryManager, skillManager, context, sandboxFactory,
                autoBackupManager, conversationRepository, settingsRepository,
                conversationSettingsTransfers, startProcessServices, providerRegistry,
                taskManager, loopManager, conversationExecutionCoordinator,
                automationExecutionGate, conversationStateRegistry, shellConfirmationController,
                askUserController,
                mcpRegistry, chatRuntime,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
