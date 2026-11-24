package com.delaynore.fitty.data.updates

import android.content.Context

internal enum class InstallOutcome { Success, Aborted, Failure }
internal enum class UpdateAttemptPhase { PendingConfirmation, Installing }

internal data class UpdateAttempt(
    val sessionId: Int,
    val expectedVersionCode: Long,
    val outcome: InstallOutcome? = null,
    val requestId: Long = 0,
    val phase: UpdateAttemptPhase = UpdateAttemptPhase.PendingConfirmation,
)

internal interface UpdateAttemptStore {
    fun read(): UpdateAttempt?
    fun write(attempt: UpdateAttempt)
    fun clear()
}

internal class SharedPreferencesUpdateAttemptStore(context: Context, databaseName: String) : UpdateAttemptStore {
    private val preferences = context.getSharedPreferences("$databaseName.update-attempt", Context.MODE_PRIVATE)

    override fun read(): UpdateAttempt? = runCatching {
        if (!preferences.contains(SESSION_ID) || !preferences.contains(EXPECTED_VERSION)) return null
        val sessionId = preferences.getInt(SESSION_ID, -1)
        val expectedVersion = preferences.getLong(EXPECTED_VERSION, -1)
        if (sessionId < 0 || expectedVersion < 0) return null
        val outcomeName = preferences.getString(OUTCOME, null)
        val outcome = outcomeName?.let { runCatching { InstallOutcome.valueOf(it) }.getOrNull() }
        if (outcomeName != null && outcome == null) return null
        val phaseName = preferences.getString(PHASE, null)
        val phase = phaseName?.let { runCatching { UpdateAttemptPhase.valueOf(it) }.getOrNull() }
            ?: UpdateAttemptPhase.PendingConfirmation
        if (phaseName != null && phaseName != phase.name) return null
        UpdateAttempt(sessionId, expectedVersion, outcome, preferences.getLong(REQUEST_ID, 0), phase)
    }.getOrNull()

    override fun write(attempt: UpdateAttempt) {
        preferences.edit()
            .putInt(SESSION_ID, attempt.sessionId)
            .putLong(EXPECTED_VERSION, attempt.expectedVersionCode)
            .putLong(REQUEST_ID, attempt.requestId)
            .putString(PHASE, attempt.phase.name)
            .apply { if (attempt.outcome == null) remove(OUTCOME) else putString(OUTCOME, attempt.outcome.name) }
            .commit()
    }

    override fun clear() { preferences.edit().clear().commit() }

    companion object {
        private const val SESSION_ID = "sessionId"
        private const val EXPECTED_VERSION = "expectedVersionCode"
        private const val OUTCOME = "outcome"
        private const val REQUEST_ID = "requestId"
        private const val PHASE = "phase"

        fun clear(context: Context, databaseName: String) {
            context.getSharedPreferences("$databaseName.update-attempt", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
