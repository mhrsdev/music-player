package com.mhrsdev.musicplayer.ui.home;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.mhrsdev.musicplayer.domain.model.HomeSection;
import com.mhrsdev.musicplayer.domain.repository.HomeFeedSource;
import java.util.List;

/** Example ViewModel: no Android UI classes, exposes LiveData only, talks to a domain interface. */
public final class HomeViewModel extends ViewModel {
    private final LiveData<List<HomeSection>> sections;

    HomeViewModel(HomeFeedSource feed) {
        sections = feed.getSections();
    }

    public LiveData<List<HomeSection>> getSections() {
        return sections;
    }

    /** ViewModels with constructor arguments need a factory; every ViewModel in the app follows this shape. */
    public static ViewModelProvider.Factory factory(HomeFeedSource feed) {
        return new ViewModelProvider.Factory() {
            @NonNull @Override
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                return modelClass.cast(new HomeViewModel(feed));
            }
        };
    }
}
