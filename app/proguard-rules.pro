# Media3, AndroidX and Material ship their own consumer rules; the project uses no reflection or serialization.
# FragmentManager re-creates fragments reflectively after process death, so keep their no-arg constructors.
-keep public class * extends androidx.fragment.app.Fragment { public <init>(); }
