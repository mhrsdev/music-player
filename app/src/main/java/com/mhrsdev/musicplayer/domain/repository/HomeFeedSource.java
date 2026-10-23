package com.mhrsdev.musicplayer.domain.repository;

import androidx.lifecycle.LiveData;
import com.mhrsdev.musicplayer.domain.model.HomeSection;
import java.util.List;

/** Supplies the Home screen sections (New releases, Throwbacks, Quick picks ...). Empty sections are omitted. */
public interface HomeFeedSource {

    LiveData<List<HomeSection>> getSections();

    /** Pull-to-refresh / "Surprise me": rebuild with a new discovery seed (random picks change, section order does not). */
    default void refresh() { }
}
