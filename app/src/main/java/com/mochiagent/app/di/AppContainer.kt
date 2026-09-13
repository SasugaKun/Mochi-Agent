package com.mochiagent.app.di

import android.app.Application
import android.content.Context
import com.mochiagent.app.data.MemoryManager
import com.mochiagent.app.data.SkillManager
import com.mochiagent.app.data.SettingsManager
import com.mochiagent.app.data.local.ChatDao
import com.mochiagent.app.data.local.ChatDatabase
import com.mochiagent.app.data.local.semanticModelSnapshot
import com.mochiagent.app.data.repository.ConversationRepository
import com.mochiagent.app.data.repository.ConversationSettingsTransferCoordinator
import com.mochiagent.app.data.repository.SettingsRepository
import com.mochiagent.app.data.repository.TaskRepository
import com.mochiagent.app.data.AutoBackupManager
import com.mochiagent.app.api.LocalModelRuntime
import com.mochiagent.app.api.local.LocalProvider
import com.mochiagent.app.automation.AutomationScheduler
import com.mochiagent.app.automation.AutomationExecutionGate
import com.mochiagent.app.automation.ConversationExecutionCoordinator
import com.mochiagent.app.automation.LoopManager
import com.mochiagent.app.automation.TaskExecutionEngine
import com.mochiagent.app.automation.TaskManager
import com.mochiagent.app.tool.AutomationToolProvider
import com.mochiagent.app.tool.McpToolProvider
import com.mochiagent.app.mcp.McpRegistry
import com.mochiagent.app.sandbox.SandboxManagerFactory
import com.mochiagent.app.service.MaintenanceDebtWorker
import com.mochiagent.app.service.TaskWorker
import com.mochiagent.app.viewmodel.ChatViewModel
import com.mochiagent.app.viewmodel.ChatViewModelFactory
import com.mochiagent.app.viewmodel.ConversationStateRegistry
import com.mochiagent.app.viewmodel.ProviderRegistry
import com.mochiagent.app.viewmodel.ShellConfirmationController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Centralized dependency container (manual DI).
 *
 * Replaces the ad-hoc dependency creation previously spread across MainActivity.
 * The validated database is injected by AgoraApplication's startup gate; all shared
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
                com.mochiagent.app.util.DebugLog.e("AppContainer", "Uncaught in appScope", e)
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
                com.mochiagent.app.util.DebugLog.e(
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
        SettingsRepository(settingsManager, appScope).also {
            LocalModelRuntime.bindIdleRetention(it.localModelIdleRetentionMinutes, appScope)
        }
    }
    val conversationSettingsTransfers: ConversationSettingsTransferCoordinator by lazy {
        ConversationSettingsTransferCoordinator(conversationRepository, settingsRepository)
    }

    /** One process-wide confirmation queue shared by Chat, Task, and Loop generation. */
    val shellConfirmationController: ShellConfirmationController by lazy {
        ShellConfirmationController(settingsRepository)
    }

    // ── Generation singletons (process-scoped) ────────────────
    // Shared by both the foreground ChatViewModel and background task execution.
    // [localProvider] must be unique per process; LocalModelRuntime owns the one embedded model
    // lifecycle. [providerRegistry] holds the live provider map the
    // generation pipeline reads and runs the long-lived credential/model sync jobs.

    val localProvider: LocalProvider by lazy { LocalProvider(appContext, settingsRepository) }

    val providerRegistry: ProviderRegistry by lazy {
        ProviderRegistry(settingsRepository, conversationRepository, localProvider, appScope)
    }

    /** Serializes every foreground/background generation touching the same conversation. */
    val conversationExecutionCoordinator: ConversationExecutionCoordinator by lazy {
        ConversationExecutionCoordinator()
    }

    /** Foreground generation slots survive Activity/ViewModel recreation within this process. */
    val conversationStateRegistry: ConversationStateRegistry by lazy {
        ConversationStateRegistry()
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
            Class.forName("com.mochiagent.app.sandbox.FdroidSandboxManagerFactory")
                .getDeclaredConstructor(
                    android.content.Context::class.java,
                    com.mochiagent.app.data.repository.SettingsRepository::class.java,
                )
                .newInstance(appContext, settingsRepository) as SandboxManagerFactory
        } catch (_: ClassNotFoundException) {
            // play flavor provides PlaySandboxManagerFactory
            try {
                Class.forName("com.mochiagent.app.sandbox.PlaySandboxManagerFactory")
                    .getDeclaredConstructor()
                    .newInstance() as SandboxManagerFactory
            } catch (_: ClassNotFoundException) {
                null
            } catch (e: Exception) {
                // Class exists but failed to construct — this is a real error, not a flavor miss.
                com.mochiagent.app.util.DebugLog.e("AppContainer", "PlaySandboxManagerFactory init failed", e)
                null
            }
        } catch (e: Exception) {
            // FdroidSandboxManagerFactory exists but failed to construct.
            com.mochiagent.app.util.DebugLog.e("AppContainer", "FdroidSandboxManagerFactory init failed", e)
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
            generationRegistry = conversationStateRegistry,
            pauseConversationLoop = { conversationId -> loopManager.stopLoop(conversationId) },
        )
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
                com.mochiagent.app.service.LoopWorker.cancel(appContext, conversationId)
            },
            cancelAlarm = { conversationId -> automationScheduler.cancelLoop(conversationId) },
            executionCoordinator = conversationExecutionCoordinator,
            executionGate = automationExecutionGate,
        )
    }

    /** Foreground-only provider: headless automation cannot recursively create automation. */
    val automationToolProvider: AutomationToolProvider by lazy {
        AutomationToolProvider(taskManager, loopManager) {
            settingsManager.automationToolsEnabled.first()
        }
    }

    val automationScheduler: AutomationScheduler by lazy {
        AutomationScheduler(appContext, taskRepository, settingsRepository, appScope)
    }

    // ── Auto Backup ───────────────────────────────────────────

    val autoBackupManager: AutoBackupManager by lazy {
        AutoBackupManager(appContext, database, settingsManager, chatDao, memoryManager, skillManager)
    }

    // ── ViewModel Factory ─────────────────────────────────────

    fun chatViewModelFactory(): ChatViewModelFactory =
        ChatViewModelFactory(
            application, database, chatDao, settingsManager, memoryManager, skillManager, appContext, sandboxManagerFactory,
            autoBackupManager, conversationRepository, settingsRepository, conversationSettingsTransfers,
            ::startProcessServices, localProvider, providerRegistry,
            taskManager, loopManager, automationToolProvider, conversationExecutionCoordinator,
            automationExecutionGate, conversationStateRegistry, shellConfirmationController,
            mcpRegistry, mcpToolProvider, taskExecutionEngine,
        )
}
