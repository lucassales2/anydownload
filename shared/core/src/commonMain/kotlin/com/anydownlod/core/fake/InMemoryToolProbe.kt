package com.anydownlod.core.fake

import com.anydownlod.core.ToolProbe
import com.anydownlod.core.domain.ToolStatus

/**
 * Reports both external tools as missing. Desktop replaces this probe in
 * T-033; shared code never looks at PATH itself.
 */
object InMemoryToolProbe : ToolProbe {
    override suspend fun probe(): ToolStatus = ToolStatus()
}
