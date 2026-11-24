package com.delaynore.fitty.data.updates

import android.content.Context

internal enum class InstallOutcome { Success, Aborted, Failure }

internal data class UpdateAttempt(
    val sessionId: Int,
    val expectedVersionCode: Long,
    val outcome: InstallOutcome? = null,
)

internal interface UpdateAttemptStore {
    fun read(): UpdateAttempt?
    fun write(attempt: UpdateAttempt)
    fun clear()
}

internal class SharedPreferencesUpdateAttemptStore(context: Context, databaseName: String) : UpdateAttemptStore {
    private val preferences = context.getSharedPreferences("$databaseName.update-attempt", Context.MODE_PRIVATE)

    override fun read(): UpdateAttempt? {
        if (!preferences.contains(SESSION_ID) || !preferences.contains(EXPECTED_VERSION)) return null
        val sessionId = preferences.getInt(SESSION_ID, -1)
        val expectedVersion = preferences.getLong(EXPECTED_VERSION, -1)
        if (sessionId < 0 || expectedVersion < 0) return null
        val outcomeName = preferences.getString(OUTCOME, null)
        val outcome = outcomeName?.let { runCatching { InstallOutcome.valueOf(it) }.getOrNull() }
        if (outcomeName != null && outcome == null) return null
        return UpdateAttempt(sessionId, expectedVersion, outcome)
    }

    override fun write(attempt: UpdateAttempt) {
        preferences.edit()
            .putInt(SESSION_ID, attempt.sessionId)
            .putLong(EXPECTED_VERSION, attempt.expectedVersionCode)
            .apply { if (attempt.outcome == null) remove(OUTCOME) else putString(OUTCOME, attempt.outcome.name) }
            .commit()
    }

    override fun clear() { preferences.edit().clear().commit() }

    companion object {
        private const val SESSION_ID = "sessionId"
        private const val EXPECTED_VERSION = "expectedVersionCode"
        private const val OUTCOME = "outcome"

        fun clear(context: Context, databaseName: String) {
            context.getSharedPreferences("$databaseName.update-attempt", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
