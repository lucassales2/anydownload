/*
 * AnyDownload ViewModel factory — AnyDownload
 *
 * T-121: the MetroX `MetroViewModelFactory` contributed to the AppScope. The
 * maps are the multibindings `ViewModelGraph` exposes; T-122 adds the first
 * entry (`QueueViewModel`). Hosts install this factory through
 * `LocalMetroViewModelFactory` so `metroViewModel()` can resolve the queues.
 */
package com.anydownload.ui.viewmodel

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import kotlin.reflect.KClass

@Inject
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class AnyDownloadViewModelFactory(
    override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel>,
    override val assistedFactoryProviders: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory>,
    override val manualAssistedFactoryProviders:
        Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory>,
) : MetroViewModelFactory()
