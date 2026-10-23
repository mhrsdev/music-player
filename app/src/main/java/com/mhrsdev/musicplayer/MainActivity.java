package com.mhrsdev.musicplayer;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.mhrsdev.musicplayer.databinding.ActivityMainBinding;
import com.mhrsdev.musicplayer.di.AppContainer;
import com.mhrsdev.musicplayer.ui.home.HomeViewModel;

/** Single-activity shell. The UI task replaces the placeholder content with the nav host. */
public class MainActivity extends AppCompatActivity {

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        AppContainer container = ((MusicPlayerApp) getApplication()).getContainer();
        HomeViewModel viewModel = new ViewModelProvider(this, HomeViewModel.factory(container.homeFeed))
                .get(HomeViewModel.class);
        viewModel.getSections().observe(this, sections ->
                binding.placeholder.setText(getString(R.string.placeholder_sections, sections.size())));
    }
}
