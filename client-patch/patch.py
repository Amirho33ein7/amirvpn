from pathlib import Path

root = Path("client")

strings = root / "app/src/main/res/values/strings.xml"
text = strings.read_text(encoding="utf-8")
text = text.replace(
    '<string name="app_name" translatable="false">sing-box</string>',
    '<string name="app_name" translatable="false">AmirVPN</string>',
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

dst = root / "app/src/main/java/io/nekohasekai/sfa/AmirBootstrap.kt"
dst.write_text(Path("client-patch/AmirBootstrap.kt").read_text(encoding="utf-8"), encoding="utf-8")


# Dashboard sync: allow users to force-refresh the shared remote profile from Home.
dashboard_vm = root / "app/src/main/java/io/nekohasekai/sfa/compose/screen/dashboard/DashboardViewModel.kt"
text = dashboard_vm.read_text(encoding="utf-8")
if "fun refreshRemoteProfiles()" not in text:
    text = text.replace(
        "import io.nekohasekai.sfa.database.Profile\n",
        "import io.nekohasekai.sfa.AmirBootstrap\nimport io.nekohasekai.sfa.database.Profile\n",
        1,
    )
    text = text.replace(
        "    fun updateProfile(profile: Profile) {\n",
        """    /**
     * Refresh all remote profiles from the shared AmirVPN configuration endpoint.
     * If the profile has not been created yet, bootstrap it first.
     */
    fun refreshRemoteProfiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val remotes = ProfileManager.list()
                .filter { it.typed.type == TypedProfile.Type.Remote }

            if (remotes.isEmpty()) {
                AmirBootstrap.ensure()
                loadProfiles()
                return@launch
            }

            remotes.forEach { profile ->
                updateProfile(profile)
            }
        }
    }

    fun updateProfile(profile: Profile) {
""",
        1,
    )
dashboard_vm.write_text(text, encoding="utf-8")

dashboard_screen = root / "app/src/main/java/io/nekohasekai/sfa/compose/screen/dashboard/DashboardScreen.kt"
text = dashboard_screen.read_text(encoding="utf-8")
if "contentDescription = "تازه‌سازی سرورها"" not in text:
    text = text.replace(
        "import androidx.compose.material.icons.filled.MoreVert\n",
        "import androidx.compose.material.icons.filled.MoreVert\nimport androidx.compose.material.icons.filled.Refresh\n",
        1,
    )
    text = text.replace(
        "            actions = {\n                Box {\n",
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

print("Dashboard refresh patch ready")
