package com.msi.data.db

import kotlinx.coroutines.flow.MutableSharedFlow

class DatabaseTrigger {
    val triggers = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
    fun notifyChanged() {
        triggers.tryEmit(Unit)
    }
}
