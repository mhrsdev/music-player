package com.mhrsdev.musicplayer.domain.model;

import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A titled row on the Home screen. Immutable. */
public record HomeSection(
        String id,
        String title,
        @Nullable String subtitle,
        HomeSectionStyle style,
        List<HomeItem> items) {

    public HomeSection {
        items = Collections.unmodifiableList(new ArrayList<>(items));
    }
}
