// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.host

import com.xuncorp.spw.workshop.api.PluginContext
import com.xuncorp.spw.workshop.api.SpwPlugin
import io.github.gaboron.spwisland.ui.IslandMenuActions

open class IslandPlugin(context: PluginContext) : SpwPlugin(context) {
    protected open fun menuActions(): IslandMenuActions? = null

    open override fun start() {
        if (runtime != null) return
        val created = IslandRuntime(menuActions())
        runtime = created
        try {
            created.start()
            playback.attach(created)
        } catch (error: Throwable) {
            playback.detach(created)
            runtime = null
            created.close()
            throw error
        }
    }
    open override fun stop() {
        val old = runtime ?: return
        playback.detach(old)
        runtime = null
        old.close()
    }
    open override fun update() = stop()
    open override fun delete() = stop()

    companion object {
        // The single bridge required by PF4J's independently constructed extension instance.
        @Volatile private var runtime: IslandRuntime? = null
        internal val playback = PlaybackCallbackBridge()
        @JvmStatic @JvmName("recover") fun recover() { runtime?.recover() }
        @JvmStatic @JvmName("about") fun about() { runtime?.about() }
        @JvmStatic @JvmName("chooseFont") fun chooseFont() { runtime?.chooseFont() }
        @JvmStatic @JvmName("resetSettings") fun resetSettings() { runtime?.resetSettings() }
        @JvmStatic @JvmName("installGnomePointer") fun installGnomePointer() { runtime?.installGnomePointer() }
        @JvmStatic @JvmName("openSource") fun openSource() { runtime?.openSource() }
    }
}
