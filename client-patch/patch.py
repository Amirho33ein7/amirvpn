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
needle = "Settings.dataStore.initialize()"
if "AmirBootstrap.ensure()" not in text:
    text = text.replace(needle, needle + "\n            AmirBootstrap.ensure()")
app.write_text(text, encoding="utf-8")

dst = root / "app/src/main/java/io/nekohasekai/sfa/AmirBootstrap.kt"
dst.write_text(Path("client-patch/AmirBootstrap.kt").read_text(encoding="utf-8"), encoding="utf-8")
