# Media3, AndroidX and Material ship their own consumer rules; the project uses no reflection or serialization.
# FragmentManager re-creates fragments reflectively after process death, so keep their no-arg constructors.
-keep public class * extends androidx.fragment.app.Fragment { public <init>(); }
# Views created by class name (ui/nav/Banners): keep the class name and its (Context) constructor, else R8 removes
# them and the banner silently never shows. ProguardKeepRulesTest checks every name Banners loads is listed here.
-keep class com.mhrsdev.musicplayer.ui.update.UpdateBanner { public <init>(android.content.Context); }
