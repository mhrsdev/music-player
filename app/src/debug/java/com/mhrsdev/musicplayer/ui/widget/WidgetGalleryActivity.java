package com.mhrsdev.musicplayer.ui.widget;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.mhrsdev.musicplayer.R;
import com.mhrsdev.musicplayer.domain.model.Track;
import com.mhrsdev.musicplayer.fake.SampleData;
import com.mhrsdev.musicplayer.ui.theme.ThemeController;
import com.mhrsdev.musicplayer.ui.theme.Tokens;

/** Debug-only gallery of every T2 widget with fake data. Launch "Widget gallery" from the debug build. */
public class WidgetGalleryActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SeekBarView[] seeks = new SeekBarView[3];
    private MiniPlayerView mini;
    private long pos = 41_000;
    private boolean playing = true;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (playing) pos = (pos + 250) % 190_000;
            for (SeekBarView s : seeks) s.setPosition(pos);
            mini.setProgress(pos, 190_000);
            handler.postDelayed(this, 250);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        float d = getResources().getDisplayMetrics().density;
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int m = (int) (16 * d);
        col.setPadding(m, (int) (40 * d), m, (int) (40 * d));

        Track t0 = SampleData.TRACKS.get(0);
        float[] wf = new float[120];
        for (int i = 0; i < wf.length; i++) {
            wf[i] = (float) (0.35 + 0.3 * Math.sin(i * 0.21) * Math.sin(i * 0.053) + 0.25 * Math.abs(Math.sin(i * 0.9)));
        }

        header(col, "Play / pause (paused, playing, buffering, disabled)");
        LinearLayout pp = row(col);
        MorphPlayPauseView paused = new MorphPlayPauseView(this);
        MorphPlayPauseView play = new MorphPlayPauseView(this);
        play.setPlaying(true, false);
        MorphPlayPauseView buffering = new MorphPlayPauseView(this);
        buffering.setBuffering(true);
        MorphPlayPauseView disabled = new MorphPlayPauseView(this);
        disabled.setEnabled(false);
        for (MorphPlayPauseView v : new MorphPlayPauseView[] {paused, play, buffering, disabled}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (72 * d), (int) (72 * d));
            lp.setMarginEnd((int) (12 * d));
            pp.addView(v, lp);
        }
        paused.setOnClickListener(x -> paused.setPlaying(!paused.isPlaying(), true));

        header(col, "Seek bars: wavy, waveform, bars (the 2nd play button toggles playback)");
        seeks[0] = new WavySeekView(this);
        seeks[1] = new WaveformSeekView(this);
        seeks[2] = new BarsSeekView(this);
        for (SeekBarView s : seeks) {
            s.setDuration(190_000);
            s.setWaveform(wf);
            s.setPlaying(true);
            s.setOnSeekListener(new SeekBarView.OnSeekListener() {
                @Override public void onSeekStart(long p) {}
                @Override public void onSeek(long p) {}
                @Override public void onSeekEnd(long p) { pos = p; }
            });
            col.addView((View) s, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        seeks[0].setWaveform(null);

        header(col, "Floating nav");
        FloatingNavView nav = new FloatingNavView(this);
        nav.addDefaultItems();
        col.addView(nav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        header(col, "Mini-player");
        mini = new MiniPlayerView(this);
        mini.bind(t0);
        mini.setPlaying(true);
        mini.setListener(new MiniPlayerView.Listener() {
            @Override public void onOpen() {}
            @Override public void onPlayPause() { play.performClick(); }
            @Override public void onNext() {}
        });
        col.addView(mini);
        play.setOnClickListener(x -> {
            playing = !playing;
            play.setPlaying(playing, true);
            for (SeekBarView s : seeks) s.setPlaying(playing);
            mini.setPlaying(playing);
        });
        ThemeController.get(this).setFromSeedColor(PlaceholderArt.seedColor(t0.placeholderSeed()), t0.id());
        ThemeController.get(this).accent().observe(this, mini::setAccent);

        header(col, "Chips");
        HorizontalScrollView hs = new HorizontalScrollView(this);
        LinearLayout chips = new LinearLayout(this);
        hs.addView(chips);
        String[] labels = {"Songs", "Albums", "Artists", "Playlists", "Folders"};
        for (int i = 0; i < labels.length; i++) {
            ChipView c = new ChipView(this);
            c.setText(labels[i]);
            c.setChecked(i == 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd((int) (8 * d));
            chips.addView(c, lp);
        }
        col.addView(hs);

        SectionHeaderView sh = new SectionHeaderView(this);
        sh.setTitle("Quick picks");
        sh.setSubtitle("Mixed from what you play at night");
        sh.setAction(R.drawable.ic_mp_shuffle, "Shuffle", v -> { });
        col.addView(sh);
        int n = Math.min(6, SampleData.TRACKS.size());
        for (int i = 0; i < n; i++) {
            TrackRowView r = new TrackRowView(this);
            r.bind(SampleData.TRACKS.get(i));
            r.setGroupPosition(n == 1 ? TrackRowView.GROUP_SINGLE
                    : i == 0 ? TrackRowView.GROUP_FIRST : i == n - 1 ? TrackRowView.GROUP_LAST : TrackRowView.GROUP_MIDDLE);
            r.setNowPlaying(i == 1);
            r.setMissing(i == n - 1);
            r.setOnMoreClickListener(v -> { });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = (int) (3 * d);
            col.addView(r, lp);
        }

        header(col, "Artwork fallbacks");
        LinearLayout arts = row(col);
        ArtworkView a1 = new ArtworkView(this);
        a1.setArtwork(null, 7);
        ArtworkView a2 = new ArtworkView(this);
        a2.setCircle(true);
        a2.setMonogram("Orbit", 3);
        ArtworkView a3 = new ArtworkView(this);
        a3.setCircle(true);
        a3.setMonogram("ایران", 11);
        for (ArtworkView a : new ArtworkView[] {a1, a2, a3}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (88 * d), (int) (88 * d));
            lp.setMarginEnd((int) (12 * d));
            arts.addView(a, lp);
        }

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Tokens.color(this, R.attr.mpBackground));
        sv.addView(col);
        setContentView(sv);
    }

    private LinearLayout row(LinearLayout parent) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(r);
        return r;
    }

    private void header(LinearLayout parent, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextAppearance(R.style.TextAppearance_MusicPlayer_Label);
        t.setPadding(0, (int) Tokens.dp(this, 24f), 0, (int) Tokens.dp(this, 8f));
        parent.addView(t);
    }

    @Override protected void onStart() {
        super.onStart();
        handler.post(tick);
    }

    @Override protected void onStop() {
        handler.removeCallbacks(tick);
        super.onStop();
    }
}
