package com.delaynore.fitty.data.local

import android.database.sqlite.SQLiteException
import com.delaynore.fitty.domain.StorageException

internal inline fun <T> runStorageOperation(operation: () -> T): T = try {
    operation()
} catch (failure: SQLiteException) {
    throw StorageException(failure)
}
