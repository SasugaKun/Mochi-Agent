package com.mochiagent.app.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mochiagent.app.data.AutoBackupManager
import com.mochiagent.app.data.MemoryManager
import com.mochiagent.app.data.SkillManager
import com.mochiagent.app.data.SettingsManager
import com.mochiagent.app.api.local.LocalProvider
import com.mochiagent.app.automation.TaskExecutionEngine
import com.mochiagent.app.automation.TaskManager
import com.mochiagent.app.automation.LoopManager
import com.mochiagent.app.automation.ConversationExecutionCoordinator
import com.mochiagent.app.automation.AutomationExecutionGate
import com.mochiagent.app.tool.AutomationToolProvider
import com.mochiagent.app.tool.McpToolProvider
import com.mochiagent.app.mcp.McpRegistry
import com.mochiagent.app.data.local.ChatDao
import com.mochiagent.app.data.local.ChatDatabase
import com.mochiagent.app.data.repository.ConversationRepository
import com.mochiagent.app.data.repository.ConversationSettingsTransferCoordinator
import com.mochiagent.app.data.repository.SettingsRepository
import com.mochiagent.app.sandbox.SandboxManagerFactory

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
    private val localProvider: LocalProvider,
    private val providerRegistry: ProviderRegistry,
    private val taskManager: TaskManager,
    private val loopManager: LoopManager,
    private val automationToolProvider: AutomationToolProvider,
    private val conversationExecutionCoordinator: ConversationExecutionCoordinator,
    private val automationExecutionGate: AutomationExecutionGate,
    private val conversationStateRegistry: ConversationStateRegistry,
    private val shellConfirmationController: ShellConfirmationController,
    private val mcpRegistry: McpRegistry,
    private val mcpToolProvider: McpToolProvider,
    private val taskExecutionEngine: TaskExecutionEngine,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(
                application, database, chatDao, settingsManager, memoryManager, skillManager, context, sandboxFactory,
                autoBackupManager, conversationRepository, settingsRepository,
                conversationSettingsTransfers, startProcessServices, localProvider, providerRegistry,
                taskManager, loopManager, automationToolProvider, conversationExecutionCoordinator,
                automationExecutionGate, conversationStateRegistry, shellConfirmationController,
                mcpRegistry, mcpToolProvider, taskExecutionEngine,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
