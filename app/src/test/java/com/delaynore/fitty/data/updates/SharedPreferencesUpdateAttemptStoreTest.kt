package com.delaynore.fitty.data.updates

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SharedPreferencesUpdateAttemptStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun roundTripClearAndDatabaseIsolation() {
        val first = SharedPreferencesUpdateAttemptStore(context, "first.db")
        val second = SharedPreferencesUpdateAttemptStore(context, "second.db")
        val attempt = UpdateAttempt(42, 9, InstallOutcome.Failure)

        first.write(attempt)

        assertEquals(attempt, first.read())
        assertNull(second.read())
        SharedPreferencesUpdateAttemptStore.clear(context, "first.db")
        assertNull(first.read())
    }

    @Test
    fun incompleteOrCorruptValuesAreIgnored() {
        val name = "broken.db.update-attempt"
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        preferences.edit().putInt("sessionId", 1).commit()
        assertNull(SharedPreferencesUpdateAttemptStore(context, "broken.db").read())

        preferences.edit().putLong("expectedVersionCode", 2).putString("outcome", "UNKNOWN").commit()
        assertNull(SharedPreferencesUpdateAttemptStore(context, "broken.db").read())
    }
}
