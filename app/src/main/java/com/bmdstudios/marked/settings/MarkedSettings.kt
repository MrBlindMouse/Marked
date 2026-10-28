package com.bmdstudios.marked.settings

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * User-tunable export defaults for Marked (Phase 1 options screen).
 *
 * @property defaultExportFolder MediaStore relative folder (e.g. "Marked") the marked media is
 * exported into; images land under Pictures/<folder>, videos under Movies/<folder>.
 * @property exportFilePrefix Prefix prepended to the auto-generated export file name.
 * @property exportFileSuffix Suffix appended to the auto-generated export file name.
 */
@Serializable
data class MarkedSettings(
    val defaultExportFolder: String = "Marked",
    val exportFilePrefix: String = "Marked_",
    val exportFileSuffix: String = "",
) {
    companion object {
        private val settingsJson = Json { ignoreUnknownKeys = true }

        /** Loads the persisted settings, falling back to defaults when absent or corrupt. */
        fun load(context: Context): MarkedSettings {
            val file = File(context.filesDir, "marked_settings.json")
            if (!file.exists()) return MarkedSettings()
            return runCatching {
                settingsJson.decodeFromString(MarkedSettings.serializer(), file.readText())
            }.getOrDefault(MarkedSettings())
        }

        /** Persists the given settings. */
        fun save(context: Context, settings: MarkedSettings) {
            val file = File(context.filesDir, "marked_settings.json")
            file.parentFile?.mkdirs()
            file.writeText(settingsJson.encodeToString(MarkedSettings.serializer(), settings))
        }
    }
}
