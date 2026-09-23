package io.nekohasekai.sfa

import android.util.Log
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.TypedProfile
import io.nekohasekai.sfa.utils.HTTPClient
import java.io.File
import java.util.Date

object AmirBootstrap {
    private const val TAG = "AmirVPN"
    const val REMOTE_URL = "https://api.jsonstorage.net/v1/json/amirvpn-core-9d8c1b6e/servers-4f7a2c91"

    suspend fun ensure() {
        runCatching {
            val profiles = ProfileManager.list()
            val existing = profiles.firstOrNull { it.name == "AmirVPN" }
            if (existing != null) {
                return
            }

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

            val content = HTTPClient().use { it.getString(REMOTE_URL) }
            Libbox.checkConfig(content)
            configFile.writeText(content)

            ProfileManager.create(profile, andSelect = true)
            Log.i(TAG, "Manager configuration imported")
        }.onFailure {
            Log.w(TAG, "Remote configuration not ready: ${it.message}")
        }
    }
}
