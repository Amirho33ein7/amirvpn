from pathlib import Path

root = Path("client")

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

color = root / "app/src/main/java/io/nekohasekai/sfa/compose/theme/Color.kt"
text = color.read_text(encoding="utf-8")
repl = {
    "Color(0xFFD81B60)": "Color(0xFF2F8BFF)",
    "Color(0xFFA00037)": "Color(0xFF175DB3)",
    "Color(0xFFFF5C8D)": "Color(0xFF69B5FF)",
    "Color(0xFF3498DB)": "Color(0xFF4DA3FF)",
    "Color(0xFF00A6B2)": "Color(0xFF2F8BFF)",
    "Color(0xFF2196F3)": "Color(0xFF2F8BFF)",
}
for a, b in repl.items():
    text = text.replace(a, b)
color.write_text(text, encoding="utf-8")

theme = root / "app/src/main/java/io/nekohasekai/sfa/compose/theme/Theme.kt"
text = theme.read_text(encoding="utf-8")
text = text.replace("darkTheme: Boolean = isSystemInDarkTheme(),", "darkTheme: Boolean = true,")
text = text.replace("dynamicColor: Boolean = true,", "dynamicColor: Boolean = false,")
theme.write_text(text, encoding="utf-8")

app = root / "app/src/main/java/io/nekohasekai/sfa/Application.kt"
text = app.read_text(encoding="utf-8")
if "AmirBootstrap.ensure()" not in text:
    text = text.replace(
        "        GlobalScope.launch(Dispatchers.IO) {",
        "        GlobalScope.launch(Dispatchers.IO) {",
        1,
    )
    text = text.replace(
        "            initialize(baseDir, workingDir, tempDir)\n            UpdateProfileWork.reconfigureUpdater()",
        "            initialize(baseDir, workingDir, tempDir)\n            AmirBootstrap.ensure()\n            UpdateProfileWork.reconfigureUpdater()",
        1,
    )
app.write_text(text, encoding="utf-8")

build = root / "app/build.gradle.kts"
text = build.read_text(encoding="utf-8")
text = text.replace("compileSdk = 37", "compileSdk = 37")
text = text.replace("compileSdkMinor = 1", "compileSdkMinor = 1")
text = text.replace("    compileSdkMinor = 1\n", "    compileSdkMinor = 1\n")
text = text.replace("targetSdk = 37", "targetSdk = 35")
build.write_text(text, encoding="utf-8")

sync_url_path = Path("sync-url.txt")
sync_url = sync_url_path.read_text(encoding="utf-8").strip() if sync_url_path.exists() else ""
if not sync_url:
    raise SystemExit("sync-url.txt is empty")

dst = root / "app/src/main/java/io/nekohasekai/sfa/AmirBootstrap.kt"
bootstrap_text = Path("client-patch/AmirBootstrap.kt").read_text(encoding="utf-8")
import re
bootstrap_text, count = re.subn(
    r'const val REMOTE_URL = ".*?"',
    f'const val REMOTE_URL = "{sync_url}"',
    bootstrap_text,
    count=1,
)
if count != 1:
    raise SystemExit("AmirBootstrap sync URL constant not found")
dst.write_text(bootstrap_text, encoding="utf-8")


# Dashboard sync: bootstrap/refresh the shared remote profile on Home.
dashboard_vm = root / "app/src/main/java/io/nekohasekai/sfa/compose/screen/dashboard/DashboardViewModel.kt"
text = dashboard_vm.read_text(encoding="utf-8")

if "import io.nekohasekai.sfa.AmirBootstrap" not in text:
    text = text.replace(
        "import io.nekohasekai.sfa.bg.BoxService\n",
        "import io.nekohasekai.sfa.AmirBootstrap\nimport io.nekohasekai.sfa.bg.BoxService\n",
        1,
    )

if "private var bootstrapAttempted = false" not in text:
    text = text.replace(
        "    private val _serviceStatus = MutableStateFlow(Status.Stopped)\n",
        "    private val _serviceStatus = MutableStateFlow(Status.Stopped)\n    private var bootstrapAttempted = false\n",
        1,
    )

text = text.replace(
    """        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profiles = ProfileManager.list()
                val selectedId = Settings.selectedProfile
""",
    """        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!bootstrapAttempted) {
                    bootstrapAttempted = true
                    AmirBootstrap.ensure()
                }
                val profiles = ProfileManager.list()
                val selectedId = Settings.selectedProfile
""",
    1,
)

if "Selecting a server from Home should connect" not in text:
    text = text.replace(
        """                Settings.selectedProfile = profileId

                // Check if service is running
""",
        """                Settings.selectedProfile = profileId

                // Selecting a server from Home should connect when the tunnel is stopped.
                if (_serviceStatus.value == Status.Stopped) {
                    sendGlobalEvent(UiEvent.RequestStartService)
                }

                // Check if service is running
""",
        1,
    )

if "fun refreshRemoteProfiles()" not in text:
    text = text.replace(
        "    fun updateProfile(profile: Profile) {\n",
        """    fun refreshRemoteProfiles() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AmirBootstrap.ensure()
                bootstrapAttempted = true
                loadProfiles()
            } catch (e: Exception) {
                sendError(e)
            }
        }
    }

    fun updateProfile(profile: Profile) {
""",
        1,
    )

# Put the Profiles/servers card first on Home.
profileFirst = """            CardGroup.Profiles,
            CardGroup.UploadTraffic,
            CardGroup.DownloadTraffic,
            CardGroup.Debug,
            CardGroup.Connections,
            CardGroup.SystemProxy,
            CardGroup.ClashMode,"""
defaultOrder = """            CardGroup.UploadTraffic,
            CardGroup.DownloadTraffic,
            CardGroup.Debug,
            CardGroup.Connections,
            CardGroup.SystemProxy,
            CardGroup.ClashMode,
            CardGroup.Profiles,"""
text = text.replace(defaultOrder, profileFirst, 2)

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

if "Icons.Default.Refresh" not in text:
    text = text.replace(
        "import androidx.compose.material.icons.filled.MoreVert\n",
        "import androidx.compose.material.icons.filled.MoreVert\nimport androidx.compose.material.icons.filled.Refresh\n",
        1,
    )
    text = text.replace(
        """            actions = {
                Box {
""",
        """            actions = {
                IconButton(onClick = { viewModel.refreshRemoteProfiles() }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "تازه‌سازی سرورها",
                    )
                }
                Box {
""",
        1,
    )

dashboard_screen.write_text(text, encoding="utf-8")

print("Dashboard/Home patch ready")
