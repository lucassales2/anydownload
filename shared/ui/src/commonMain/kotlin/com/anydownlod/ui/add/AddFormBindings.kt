package com.anydownlod.ui.add

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import kotlin.random.Random

/** The random batch-id source [AddFormViewModel] uses when a test does not pass one. */
@BindingContainer
object AddFormBindings {
    @Provides
    fun addBatchIds(): AddBatchIds = AddBatchIds {
        "add-${Random.nextLong().toULong().toString(16)}"
    }
}
