package com.mhrsdev.musicplayer.playback;

import android.content.Context;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import com.mhrsdev.musicplayer.domain.model.SoundState;
import com.mhrsdev.musicplayer.domain.model.SoundState.Band;
import com.mhrsdev.musicplayer.domain.repository.AudioEffectsController;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Equalizer, bass boost, virtualizer and loudness enhancer on the audio session of the process-wide ExoPlayer.
 * Main thread only.
 *
 * <p>Battery / RAM: the native effect objects exist only while the sound screen is observed or while a playback
 * session is alive (not idle / ended) AND at least one effect changes the sound ({@link EffectsPolicy}); the service
 * stopping the player (idle) releases them. Attaching at "buffering" rather than "playing" avoids an unprocessed
 * blip on resume. Every platform call is guarded: a device without an effect (or a vendor failure) flags just that
 * effect unsupported in the {@link SoundState}. Nothing polls; everything is driven by player events and setters.
 */
@OptIn(markerClass = UnstableApi.class)
public final class AudioEffectsEngine implements AudioEffectsController {
    private final ExoPlayer player;
    private final SoundPrefs prefs;
    private final StateData state = new StateData();

    @Nullable private Equalizer eq;
    @Nullable private BassBoost bassBoost;
    @Nullable private Virtualizer virtualizer;
    @Nullable private LoudnessEnhancer loudness;
    private int sessionId = C.AUDIO_SESSION_ID_UNSET;

    private boolean eqSupported = true;
    private boolean bassSupported = true;
    private boolean virtSupported = true;
    private boolean loudSupported = true;

    // Device facts, probed on the first attach and kept after the effects are released.
    private int[] centersHz = new int[0];
    private int minMb;
    private int maxMb;
    private List<String> presetNames = Collections.emptyList();

    private boolean eqEnabled;
    private int preset;
    private int[] levels = new int[0];
    private int bass;
    private int virt;
    private int loudMb;

    public AudioEffectsEngine(Context context) {
        Context app = context.getApplicationContext();
        player = PlayerHolder.get(app);
        prefs = new SoundPrefs(app);
        eqEnabled = prefs.eqEnabled();
        preset = prefs.preset();
        bass = prefs.bass();
        virt = prefs.virtualizer();
        loudMb = prefs.loudnessMb();
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int playbackState) {
                refresh();
            }

            @Override public void onAudioSessionIdChanged(int audioSessionId) {
                refresh(); // a new session needs new effect objects (sessionId differs from the attached one)
            }
        });
        refresh();
        state.setValue(buildState());
    }

    @Override public LiveData<SoundState> getState() {
        return state;
    }

    // --- setters ------------------------------------------------------------------------------

    @Override public void setEqualizerEnabled(boolean enabled) {
        eqEnabled = enabled;
        prefs.eqEnabled(enabled);
        changed();
    }

    @Override public void setBandLevel(int band, int levelMb) {
        if (band < 0 || band >= levels.length) return;
        levels[band] = EffectsPolicy.clamp(levelMb, minMb, maxMb);
        preset = SoundState.PRESET_CUSTOM;
        prefs.preset(preset);
        prefs.customLevels(EffectsPolicy.formatLevels(levels));
        changed();
    }

    @Override public void selectPreset(int index) {
        if (index >= presetNames.size()) return;
        preset = index < 0 ? SoundState.PRESET_CUSTOM : index;
        prefs.preset(preset);
        if (preset < 0) levels = EffectsPolicy.parseLevels(prefs.customLevels(), levels.length, minMb, maxMb);
        changed(); // for a device preset the levels are read back from the equalizer while applying
    }

    @Override public void setBassBoost(int strength) {
        bass = EffectsPolicy.clamp(strength, 0, SoundState.MAX_STRENGTH);
        prefs.bass(bass);
        changed();
    }

    @Override public void setVirtualizer(int strength) {
        virt = EffectsPolicy.clamp(strength, 0, SoundState.MAX_STRENGTH);
        prefs.virtualizer(virt);
        changed();
    }

    @Override public void setLoudnessGain(int gainMb) {
        loudMb = EffectsPolicy.clamp(gainMb, 0, SoundState.MAX_LOUDNESS_MB);
        prefs.loudnessMb(loudMb);
        changed();
    }

    @Override public void reset() {
        eqEnabled = false;
        preset = SoundState.PRESET_CUSTOM;
        levels = new int[levels.length];
        bass = 0;
        virt = 0;
        loudMb = 0;
        prefs.eqEnabled(false);
        prefs.preset(preset);
        prefs.customLevels("");
        prefs.bass(0);
        prefs.virtualizer(0);
        prefs.loudnessMb(0);
        changed();
    }

    // --- effect lifecycle ---------------------------------------------------------------------

    private void changed() {
        refresh();
        applyAll();
        state.setValue(buildState());
    }

    /** Attaches or releases the native effects according to {@link EffectsPolicy#shouldHold}. */
    private void refresh() {
        int pState = player.getPlaybackState();
        boolean alive = pState == Player.STATE_READY || pState == Player.STATE_BUFFERING;
        boolean active = EffectsPolicy.anyActive(eqEnabled, levels, bass, virt, loudMb);
        if (!EffectsPolicy.shouldHold(state.hasActiveObservers(), alive, active)) {
            release();
            return;
        }
        int id = player.getAudioSessionId();
        if (id == C.AUDIO_SESSION_ID_UNSET || (id == sessionId && hasEffects())) return;
        release();
        sessionId = id;
        if (eqSupported) eq = create(() -> new Equalizer(0, id), () -> eqSupported = false);
        if (bassSupported) bassBoost = create(() -> new BassBoost(0, id), () -> bassSupported = false);
        if (virtSupported) virtualizer = create(() -> new Virtualizer(0, id), () -> virtSupported = false);
        if (loudSupported) loudness = create(() -> new LoudnessEnhancer(id), () -> loudSupported = false);
        probeEqualizer();
        applyAll();
        state.setValue(buildState());
    }

    private interface Factory<T> { T create(); }

    @Nullable private static <T extends AudioEffect> T create(Factory<T> factory, Runnable onUnsupported) {
        try {
            return factory.create();
        } catch (RuntimeException e) { // missing effect library, vendor failure, too many effects: just unsupported
            onUnsupported.run();
            return null;
        }
    }

    private boolean hasEffects() {
        return eq != null || bassBoost != null || virtualizer != null || loudness != null;
    }

    private void release() {
        sessionId = C.AUDIO_SESSION_ID_UNSET;
        if (eq != null) eq.release();
        if (bassBoost != null) bassBoost.release();
        if (virtualizer != null) virtualizer.release();
        if (loudness != null) loudness.release();
        eq = null;
        bassBoost = null;
        virtualizer = null;
        loudness = null;
    }

    private void probeEqualizer() {
        if (eq == null || centersHz.length > 0) return;
        try {
            int n = eq.getNumberOfBands();
            short[] range = eq.getBandLevelRange();
            int[] centers = new int[n];
            for (int i = 0; i < n; i++) centers[i] = eq.getCenterFreq((short) i) / 1000; // milliHertz -> Hz
            List<String> names = new ArrayList<>();
            for (int i = 0; i < eq.getNumberOfPresets(); i++) names.add(eq.getPresetName((short) i));
            centersHz = centers;
            minMb = range[0];
            maxMb = range[1];
            presetNames = names;
            levels = EffectsPolicy.parseLevels(prefs.customLevels(), n, minMb, maxMb);
            if (preset >= names.size()) preset = SoundState.PRESET_CUSTOM;
        } catch (RuntimeException e) {
            eqSupported = false;
            eq.release();
            eq = null;
        }
    }

    private void applyAll() {
        if (eq != null) {
            try {
                if (preset >= 0) {
                    eq.usePreset((short) preset);
                    for (int i = 0; i < levels.length; i++) levels[i] = eq.getBandLevel((short) i);
                } else {
                    for (int i = 0; i < levels.length; i++) eq.setBandLevel((short) i, (short) levels[i]);
                }
                eq.setEnabled(eqEnabled);
            } catch (RuntimeException e) {
                eqSupported = false;
                eq.release();
                eq = null;
            }
        }
        if (bassBoost != null) {
            try {
                if (!bassBoost.getStrengthSupported()) throw new UnsupportedOperationException();
                bassBoost.setStrength((short) bass);
                bassBoost.setEnabled(bass > 0);
            } catch (RuntimeException e) {
                bassSupported = false;
                bassBoost.release();
                bassBoost = null;
            }
        }
        if (virtualizer != null) {
            try {
                if (!virtualizer.getStrengthSupported()) throw new UnsupportedOperationException();
                virtualizer.setStrength((short) virt);
                virtualizer.setEnabled(virt > 0);
            } catch (RuntimeException e) {
                virtSupported = false;
                virtualizer.release();
                virtualizer = null;
            }
        }
        if (loudness != null) {
            try {
                loudness.setTargetGain(loudMb);
                loudness.setEnabled(loudMb > 0);
            } catch (RuntimeException e) {
                loudSupported = false;
                loudness.release();
                loudness = null;
            }
        }
    }

    private SoundState buildState() {
        List<Band> bands = new ArrayList<>(levels.length);
        for (int i = 0; i < levels.length && i < centersHz.length; i++) bands.add(new Band(centersHz[i], levels[i]));
        return new SoundState(eqSupported, eqEnabled, bands, minMb, maxMb, presetNames, preset,
                bassSupported, bass, virtSupported, virt, loudSupported, loudMb);
    }

    /** Observing the state (the sound screen) is what makes the effects probe the device and stay attached. */
    private final class StateData extends MutableLiveData<SoundState> {
        @Override protected void onActive() {
            refresh();
            setValue(buildState());
        }

        @Override protected void onInactive() {
            refresh();
        }
    }
}
