package io.nekohasekai.sfa

import android.util.Log
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.database.TypedProfile
import io.nekohasekai.sfa.utils.HTTPClient
import java.io.File
import java.util.Date

object AmirBootstrap {
    private const val TAG = "AmirVPN"
    const val REMOTE_URL = "https://api.jsonstorage.net/v1/json/amirvpn-core-9d8c1b6e/servers-4f7a2c91"

    /**
     * Fetch and install the shared Manager configuration.
     * Returns true only when the local AmirVPN profile contents changed.
     */
    suspend fun ensure(): Boolean {
        var changed = false

        runCatching {
            val content = HTTPClient().use { client ->
                runCatching {
                    client.getString("$REMOTE_URL?amir_refresh=${System.currentTimeMillis()}")
                }.getOrElse {
                    client.getString(REMOTE_URL)
                }
            }

            Libbox.checkConfig(content)

            val profiles = ProfileManager.list()
            val existing = profiles.firstOrNull { it.name == "AmirVPN" }

            if (existing == null) {
                val typedProfile = TypedProfile().apply {
                    type = TypedProfile.Type.Remote
                    remoteURL = REMOTE_URL
                    autoUpdate = true
                    autoUpdateInterval = 15
                    lastUpdated = Date()
                }

                val profile = Profile(name = "AmirVPN", typed = typedProfile).apply {
                    userOrder = ProfileManager.nextOrder()
                }

                val fileId = ProfileManager.nextFileID()
                val configDirectory = File(Application.application.filesDir, "configs").also { it.mkdirs() }
                val configFile = File(configDirectory, "$fileId.json")
                typedProfile.path = configFile.path
                configFile.writeText(content)

                ProfileManager.create(profile, andSelect = true)
                changed = true
                Log.i(TAG, "Shared configuration imported")
            } else {
                val configFile = File(existing.typed.path)
                configFile.parentFile?.mkdirs()
                changed = !configFile.exists() || configFile.readText() != content

                if (changed) {
                    configFile.writeText(content)
                }

                existing.typed.remoteURL = REMOTE_URL
                existing.typed.type = TypedProfile.Type.Remote
                existing.typed.autoUpdate = true
                existing.typed.autoUpdateInterval = 15
                existing.typed.lastUpdated = Date()
                ProfileManager.update(existing)

                val selectedExists = ProfileManager.list().any { it.id == Settings.selectedProfile }
                if (!selectedExists) {
                    Settings.selectedProfile = existing.id
                }

                Log.i(TAG, if (changed) "Shared configuration refreshed" else "Shared configuration unchanged")
            }
        }.onFailure {
            Log.w(TAG, "Remote configuration not ready: ${it.message}")
        }

        return changed
    }
}
