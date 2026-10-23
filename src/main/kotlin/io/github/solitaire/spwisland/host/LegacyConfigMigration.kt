@file:OptIn(com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi::class)

package io.github.solitaire.spwisland.host

import com.xuncorp.spw.workshop.api.WorkshopApi
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Copies settings once when a renamed plugin starts with a fresh data directory. */
internal object LegacyConfigMigration {
    fun migrate(
        currentPluginId: String,
        legacyPluginIds: List<String>,
        fileNames: List<String>,
    ) {
        if (legacyPluginIds.isEmpty()) return
        val current = WorkshopApi.manager.createConfigManager(currentPluginId)
        for (fileName in fileNames) {
            val target = current.getConfig(fileName).getConfigPath()
            if (!canInitialize(target)) continue
            for (legacyId in legacyPluginIds) {
                val source = WorkshopApi.manager.createConfigManager(legacyId)
                    .getConfig(fileName)
                    .getConfigPath()
                if (!hasSettings(source)) continue
                val copied = runCatching {
                    Files.createDirectories(target.parent)
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                }.isSuccess
                if (copied) break
            }
        }
    }

    private fun canInitialize(path: Path): Boolean =
        !Files.exists(path) || runCatching {
            val text = Files.readString(path).trim()
            text.isEmpty() || text == "{}"
        }.getOrDefault(false)

    private fun hasSettings(path: Path): Boolean =
        Files.isRegularFile(path) && runCatching { Files.readString(path).isNotBlank() }.getOrDefault(false)
}