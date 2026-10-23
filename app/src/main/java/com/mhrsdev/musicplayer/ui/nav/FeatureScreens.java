package com.mhrsdev.musicplayer.ui.nav;

import android.content.Context;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.mhrsdev.musicplayer.R;
import java.util.ArrayList;
import java.util.List;

/**
 * Extension registry: feature screens built by other packages, linked from Settings by class name so the shell
 * compiles without them. An entry is shown only when its class is present and is a {@link Fragment} with a public
 * no-arg constructor (same pattern {@code MainActivity} uses for the player).
 */
public class FeatureScreens {

    public record Entry(String className, String tag, @StringRes int title, @StringRes int subtitle,
            @DrawableRes int icon) { }

    public static final List<Entry> ALL = List.of(
            new Entry("com.mhrsdev.musicplayer.ui.sound.SoundFragment", "sound",
                    R.string.settings_feature_sound, R.string.settings_feature_sound_sub, R.drawable.settings_ic_equalizer),
            new Entry("com.mhrsdev.musicplayer.ui.headset.HeadsetFragment", "headset",
                    R.string.settings_feature_headset, R.string.settings_feature_headset_sub, R.drawable.settings_ic_headset),
            new Entry("com.mhrsdev.musicplayer.ui.backup.BackupFragment", "backup",
                    R.string.settings_feature_backup, R.string.settings_feature_backup_sub, R.drawable.settings_ic_backup),
            new Entry("com.mhrsdev.musicplayer.ui.update.UpdateFragment", "update",
                    R.string.settings_feature_update, R.string.settings_feature_update_sub, R.drawable.settings_ic_update));

    private FeatureScreens() { }

    /** The entries whose screens are in this build. */
    public static List<Entry> available(Context context) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : ALL) if (exists(context, e.className())) out.add(e);
        return out;
    }

    static boolean exists(Context context, String className) {
        try {
            Class<?> c = Class.forName(className, false, context.getClassLoader());
            return Fragment.class.isAssignableFrom(c) && c.getConstructor() != null;
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError e) {
            return false;
        }
    }

    /** Creates the screen through the fragment factory; null if it cannot be built. */
    @Nullable
    public static Fragment instantiate(FragmentManager fm, Context context, String className) {
        try {
            return fm.getFragmentFactory().instantiate(context.getClassLoader(), className);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Pushes {@code entry}'s screen on the back stack. False if it is missing or no navigator hosts it. */
    public static boolean open(Fragment from, Entry entry) {
        ScreenNavigator nav = ScreenNavigator.from(from.requireContext());
        Fragment f = instantiate(from.getParentFragmentManager(), from.requireContext(), entry.className());
        if (nav == null || f == null) return false;
        nav.openFragment(f, entry.tag());
        return true;
    }
}
