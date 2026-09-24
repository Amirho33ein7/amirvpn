from pathlib import Path
import shutil

root = Path("client")

# Branding
strings = root / "app/src/main/res/values/strings.xml"
text = strings.read_text(encoding="utf-8")
text = text.replace(
    '<string name="app_name" translatable="false">sing-box</string>',
    '<string name="app_name" translatable="false">AmirVPN</string>',
)
text = text.replace(
    '<string name="title_dashboard">Dashboard</string>',
    '<string name="title_dashboard">Home</string>',
)
strings.write_text(text, encoding="utf-8")

# Force the AmirVPN dark theme.
color = root / "app/src/main/java/io/nekohasekai/sfa/compose/theme/Color.kt"
text = color.read_text(encoding="utf-8")
for old, new in {
    "Color(0xFFD81B60)": "Color(0xFF2F8BFF)",
    "Color(0xFFA00037)": "Color(0xFF175DB3)",
    "Color(0xFFFF5C8D)": "Color(0xFF69B5FF)",
    "Color(0xFF3498DB)": "Color(0xFF4DA3FF)",
    "Color(0xFF00A6B2)": "Color(0xFF2F8BFF)",
    "Color(0xFF2196F3)": "Color(0xFF2F8BFF)",
}.items():
    text = text.replace(old, new)
color.write_text(text, encoding="utf-8")

theme = root / "app/src/main/java/io/nekohasekai/sfa/compose/theme/Theme.kt"
text = theme.read_text(encoding="utf-8")
text = text.replace("darkTheme: Boolean = isSystemInDarkTheme(),", "darkTheme: Boolean = true,")
text = text.replace("dynamicColor: Boolean = true,", "dynamicColor: Boolean = false,")
theme.write_text(text, encoding="utf-8")

# Install bundled server profiles during app startup.
app = root / "app/src/main/java/io/nekohasekai/sfa/Application.kt"
text = app.read_text(encoding="utf-8")
text = text.replace("            UpdateProfileWork.reconfigureUpdater()\n", "")
if "AmirBootstrap.ensure()" not in text:
    text = text.replace(
        "            initialize(baseDir, workingDir, tempDir)",
        "            initialize(baseDir, workingDir, tempDir)\n            AmirBootstrap.ensure()",
        1,
    )
app.write_text(text, encoding="utf-8")


# Disable upstream first-launch / automatic application update checking.
main_activity = root / "app/src/main/java/io/nekohasekai/sfa/compose/MainActivity.kt"
text = main_activity.read_text(encoding="utf-8")
if "import io.nekohasekai.sfa.AmirBootstrap" not in text:
    text = text.replace(
        "import io.nekohasekai.sfa.Application",
        "import io.nekohasekai.sfa.AmirBootstrap\nimport io.nekohasekai.sfa.Application",
        1,
    )
text = text.replace(
    """        connection.reconnect()
        RemoteControlManager.restore()
""",
    """        connection.reconnect()
        RemoteControlManager.restore()

        lifecycleScope.launch(Dispatchers.IO) {
            AmirBootstrap.ensure()
        }
""",
    1,
)
text = text.replace(
    """        UpdateState.loadFromCache()
        if (Settings.checkUpdateEnabled) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val updateInfo = Vendor.checkUpdateAsync()
                    UpdateState.setUpdate(updateInfo)
                } catch (_: Exception) {
                    UpdateState.setUpdate(null)
                }
            }
        }

""",
    "",
    1,
)
prompt_start = text.find("        // Handle update check prompt dialog (shown only once on first launch)")
prompt_end = text.find("        // Handle update available dialog", prompt_start)
if prompt_start >= 0 and prompt_end > prompt_start:
    text = text[:prompt_start] + text[prompt_end:]
else:
    raise SystemExit("Unable to locate upstream update prompt block")
main_activity.write_text(text, encoding="utf-8")

# Copy the bundled server list and local bootstrap into the client source.
asset_src = Path("client-patch/amirs-servers.b64")
asset_dst = root / "app/src/main/assets/amirs-servers.b64"
asset_dst.parent.mkdir(parents=True, exist_ok=True)
shutil.copyfile(asset_src, asset_dst)

bootstrap_src = Path("client-patch/AmirBootstrap.kt")
bootstrap_dst = root / "app/src/main/java/io/nekohasekai/sfa/AmirBootstrap.kt"
shutil.copyfile(bootstrap_src, bootstrap_dst)

# Home: make bundled server installation the first load and refresh path.
dashboard_vm = root / "app/src/main/java/io/nekohasekai/sfa/compose/screen/dashboard/DashboardViewModel.kt"
text = dashboard_vm.read_text(encoding="utf-8")

if "import io.nekohasekai.sfa.AmirBootstrap" not in text:
    text = text.replace(
        "import io.nekohasekai.sfa.bg.BoxService\n",
        "import android.util.Log\nimport io.nekohasekai.sfa.AmirBootstrap\nimport io.nekohasekai.sfa.bg.BoxService\n",
        1,
    )

if "private var bootstrapAttempted = false" not in text:
    text = text.replace(
        "    private val _serviceStatus = MutableStateFlow(Status.Stopped)\n",
        "    private val _serviceStatus = MutableStateFlow(Status.Stopped)\n    private var bootstrapAttempted = false\n",
        1,
    )

old_load = """        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profiles = ProfileManager.list()
                val selectedId = Settings.selectedProfile
"""
new_load = """        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!bootstrapAttempted) {
                    bootstrapAttempted = true
                    AmirBootstrap.ensure()
                }
                val profiles = ProfileManager.list()
                val selectedId = Settings.selectedProfile
"""
text = text.replace(old_load, new_load, 1)

if "Selecting a server from Home should connect" not in text:
    text = text.replace(
        """                Settings.selectedProfile = profileId

                // Check if service is running
""",
        """                Settings.selectedProfile = profileId

                // A server tap on Home follows the normal Android VPN permission flow.
                if (_serviceStatus.value == Status.Stopped) {
                    Log.i("AmirVPN", "Profile selected; requesting service start id=$profileId")
                    sendGlobalEvent(UiEvent.RequestStartService)
                }

                // Check if service is running
""",
        1,
    )

old_refresh = """    fun updateProfile(profile: Profile) {
"""
new_refresh = """    fun refreshRemoteProfiles() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val changed = AmirBootstrap.ensure()
                bootstrapAttempted = true
                if (changed && _serviceStatus.value == Status.Started) {
                    runCatching {
                        Libbox.newStandaloneCommandClient().serviceReload()
                    }.onFailure {
                        sendGlobalEvent(UiEvent.RequestReconnectService)
                    }
                }
                loadProfiles()
            } catch (e: Exception) {
                sendError(e)
            }
        }
    }

    fun updateProfile(profile: Profile) {
"""
if "fun refreshRemoteProfiles()" not in text:
    text = text.replace(old_refresh, new_refresh, 1)

default_order = """            CardGroup.UploadTraffic,
            CardGroup.DownloadTraffic,
            CardGroup.Debug,
            CardGroup.Connections,
            CardGroup.SystemProxy,
            CardGroup.ClashMode,
            CardGroup.Profiles,"""
profiles_first = """            CardGroup.Profiles,
            CardGroup.UploadTraffic,
            CardGroup.DownloadTraffic,
            CardGroup.Debug,
            CardGroup.Connections,
            CardGroup.SystemProxy,
            CardGroup.ClashMode,"""
text = text.replace(default_order, profiles_first, 2)
dashboard_vm.write_text(text, encoding="utf-8")

dashboard_screen = root / "app/src/main/java/io/nekohasekai/sfa/compose/screen/dashboard/DashboardScreen.kt"
text = dashboard_screen.read_text(encoding="utf-8")
if "import androidx.compose.runtime.LaunchedEffect" not in text:
    text = text.replace(
        "import androidx.compose.runtime.Composable\n",
        "import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.LaunchedEffect\n",
        1,
    )
if "viewModel.refreshRemoteProfiles()" not in text:
    text = text.replace(
        "    val uiState by viewModel.uiState.collectAsState()\n",
        """    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshRemoteProfiles()
    }
""",
        1,
    )
dashboard_screen.write_text(text, encoding="utf-8")

# Make the visible profile label itself activate the same selection callback as the row.
profile_sheet = root / "app/src/main/java/io/nekohasekai/sfa/compose/screen/dashboard/ProfilePickerSheet.kt"
text = profile_sheet.read_text(encoding="utf-8")
if "import androidx.compose.foundation.clickable" not in text:
    text = text.replace(
        "import androidx.compose.foundation.layout.Arrangement\n",
        "import androidx.compose.foundation.clickable\nimport androidx.compose.foundation.layout.Arrangement\n",
        1,
    )
label_old = """                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.bodyMedium,
"""
label_new = """                Text(
                    text = profile.name,
                    modifier = Modifier.clickable(onClick = onSelect),
                    style = MaterialTheme.typography.bodyMedium,
"""
if "modifier = Modifier.clickable(onClick = onSelect)" not in text:
    if label_old not in text:
        raise SystemExit("Unable to locate profile label in ProfilePickerSheet.kt")
    text = text.replace(label_old, label_new, 1)
profile_sheet.write_text(text, encoding="utf-8")

print("Standalone AmirVPN patch ready")
