package com.delaynore.fitty.data.updates

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
