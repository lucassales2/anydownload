package com.anydownlod.core.platform

actual fun <T> engineCriticalSection(lock: Any, block: () -> T): T = block()