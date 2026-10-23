package com.mhrsdev.musicplayer.domain.model;

import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A row on the Home screen. Immutable. The UI shows a localized title looked up by {@link #id} (see
 * {@code ui/home/SectionText}); {@link #title} / {@link #subtitle} are only an untranslated fallback for unknown ids.
 *
 * @param arg id-specific parameter for the localized title (e.g. a year for "On this day"); may be null
 * @param stats only for {@link HomeSectionStyle#STATS}
 */
public record HomeSection(
        String id,
        String title,
        @Nullable String subtitle,
        HomeSectionStyle style,
        List<HomeItem> items,
        @Nullable String arg,
        @Nullable HomeStats stats) {

    public HomeSection {
        items = Collections.unmodifiableList(new ArrayList<>(items));
    }

    public HomeSection(String id, String title, @Nullable String subtitle, HomeSectionStyle style, List<HomeItem> items) {
        this(id, title, subtitle, style, items, null, null);
    }

    /** True when the section has something to show (ACTIONS / STATS carry no items). */
    public boolean hasContent() {
        return !items.isEmpty() || style == HomeSectionStyle.ACTIONS || stats != null;
    }
}
