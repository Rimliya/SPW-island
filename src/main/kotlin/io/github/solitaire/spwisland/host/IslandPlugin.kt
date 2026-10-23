// SPDX-License-Identifier: GPL-3.0-only
package io.github.solitaire.spwisland.host

import com.xuncorp.spw.workshop.api.PluginContext
import com.xuncorp.spw.workshop.api.SpwPlugin
import io.github.solitaire.spwisland.diagnostics.IslandLog
import io.github.solitaire.spwisland.ui.IslandMenuActions

open class IslandPlugin(context: PluginContext) : SpwPlugin(context) {
    protected open fun menuActions(): IslandMenuActions? = null

    protected open fun migrateLegacyConfig() {
        LegacyConfigMigration.migrate(
            currentPluginId = pluginContext.pluginId,
            legacyPluginIds = listOf(LEGACY_PLUGIN_ID),
            fileNames = listOf("island.json"),
        )
    }

    private fun startDiagnostics() = IslandLog.start("host", mapOf("pluginVersion" to pluginContext.pluginVersion,
        "spwVersion" to pluginContext.spwVersion, "spwChannel" to pluginContext.spwChannel))
    init { startDiagnostics() }

    open override fun start() {
        if (runtime != null) return
        startDiagnostics()
        migrateLegacyConfig()
        val operation = IslandLog.operation("lifecycle", "plugin.start")
        var created: IslandRuntime? = null
        try {
            val next = IslandRuntime(menuActions())
            created = next
            runtime = next
            next.start()
            playback.attach(next)
            operation.close()
        } catch (error: Throwable) {
            IslandLog.failure("IslandPlugin", "caught.error", error)
            operation.failed(error)
            created?.let(playback::detach)
            runtime = null
            IslandLog.attempt("lifecycle", "plugin.start_cleanup_failed") { created?.close() }
            throw error
        }
    }
    open override fun stop() {
        val old = runtime ?: run { IslandLog.finish(); return }
        playback.detach(old)
        runtime = null
        val operation = IslandLog.operation("lifecycle", "plugin.stop")
        try { old.close(); operation.close() }
        catch (error: Throwable) {
            IslandLog.failure("IslandPlugin", "caught.error", error); operation.failed(error); throw error
        } finally { IslandLog.finish() }
    }
    open override fun update() = stop()
    open override fun delete() = stop()

    companion object {
        // The single bridge required by PF4J's independently constructed extension instance.
        @Volatile private var runtime: IslandRuntime? = null
        internal val playback = PlaybackCallbackBridge()
        private const val LEGACY_PLUGIN_ID = "io.github.gaboron.spwisland"
        @JvmStatic @JvmName("recover") fun recover() { runtime?.recover() }
        @JvmStatic @JvmName("about") fun about() { runtime?.about() }
        @JvmStatic @JvmName("chooseFont") fun chooseFont() { runtime?.chooseFont() }
        @JvmStatic @JvmName("resetSettings") fun resetSettings() { runtime?.resetSettings() }
        @JvmStatic @JvmName("installGnomePointer") fun installGnomePointer() { runtime?.installGnomePointer() }
        @JvmStatic @JvmName("openSource") fun openSource() { runtime?.openSource() }
        @JvmStatic @JvmName("openLogs") fun openLogs() { HostDiagnosticActions.openLogs() }
        @JvmStatic @JvmName("exportDiagnostics") fun exportDiagnostics() { HostDiagnosticActions.export() }
    }
}