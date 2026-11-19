package com.jarm.app

import android.app.Application
import com.jarm.app.api.util.tokens.bpe.O200kBase
import com.jarm.app.data.local.ChatDatabase
import com.jarm.app.di.AppContainer
import com.jarm.app.diagnostics.DeveloperDiagnostics
import com.jarm.app.util.CrashReporter
import com.jarm.app.util.DebugLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Application entry point. Installs the crash reporter before any other component runs so
 * that crashes occurring during startup are captured as well.
 *
 * Owns the process-scoped AppContainer, but publishes it only after the durable database has
 * passed compatibility checks, supported migrations, and Room schema validation.
 */
class JARMApplication : Application() {
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val startupGate = DatabaseStartupGate(
        inspectDatabase = {
            withContext(Dispatchers.IO) {
                ChatDatabase.inspectCompatibility(this@JARMApplication)
            }
        },
        openResource = {
            withContext(Dispatchers.IO) {
                val database = ChatDatabase.build(this@JARMApplication)
                AppContainer(this@JARMApplication, database)
            }
        },
        closeResource = { container -> container.database.close() },
        deleteDatabase = {
            withContext(Dispatchers.IO) {
                val databasePath = getDatabasePath(ChatDatabase.DB_NAME)
                !databasePath.exists() ||
                    this@JARMApplication.deleteDatabase(ChatDatabase.DB_NAME)
            }
        },
        reportFailure = { error ->
            DebugLog.e(
                "JARMApplication",
                "Database startup gate failed closed",
                error,
            )
        },
    )

    val databaseStartupState: StateFlow<DatabaseStartupState>
        get() = startupGate.state

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        if (PHONE_USE_SUPPORTED) {
            com.jarm.app.phone.PhoneScreenTimeout.process(this).recoverOnAppEvents(this, startupScope)
        }
        // The context indicator counts remote models' text with a real vocabulary once it is in
        // memory. Loading it in its own job keeps the first estimate off the heuristic without
        // delaying the database gate behind it.
        startupScope.launch { O200kBase.ensureLoaded(this@JARMApplication) }
        startupScope.launch {
            try {
                DeveloperDiagnostics.initialize(noBackupFilesDir, startupScope)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                DebugLog.e(
                    "JARMApplication",
                    "Diagnostic capture initialization failed closed",
                    error,
                )
            }
            startupGate.initialize()
        }
    }

    suspend fun awaitDatabaseStartup(): DatabaseStartupState =
        startupGate.awaitState()

    suspend fun awaitContainer(): AppContainer? =
        startupGate.awaitReadyResource()

    fun requireContainer(): AppContainer =
        startupGate.requireReadyResource()

    suspend fun clearIncompatibleDatabase(): Boolean =
        startupGate.clearBlockedDatabase()
}
