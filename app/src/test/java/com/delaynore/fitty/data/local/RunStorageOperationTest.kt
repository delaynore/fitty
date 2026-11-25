package com.delaynore.fitty.data.local

import android.database.sqlite.SQLiteException
import com.delaynore.fitty.domain.StorageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RunStorageOperationTest {
    @Test
    fun mapsSQLiteFailureToStorageException() = runTest {
        val sqlite = SQLiteException("disk full")
        val failure = runCatching { runStorageOperation<Unit> { throw sqlite } }.exceptionOrNull() as StorageException
        assertSame(sqlite, failure.cause)
    }

    @Test
    fun preservesResultAndPropagatesOtherFailures() = runTest {
        assertEquals(false, runStorageOperation { false })
        assertEquals(null, runStorageOperation<String?> { null })
        for (failure in listOf(CancellationException("cancelled"), IllegalStateException("broken"))) {
            assertSame(failure, runCatching { runStorageOperation<Unit> { throw failure } }.exceptionOrNull())
        }
    }
}
