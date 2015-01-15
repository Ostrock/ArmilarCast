package de.danoeh.antennapodsp.activity;

import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.WindowCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;

import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import de.danoeh.antennapod.core.feed.EventDistributor;
import de.danoeh.antennapod.core.menuhandler.MenuItemUtils;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.service.download.DownloadService;
import de.danoeh.antennapod.core.storage.DBTasks;
import de.danoeh.antennapod.core.storage.DownloadRequester;
import de.danoeh.antennapod.core.util.StorageUtils;
import de.danoeh.antennapodsp.BuildConfig;
import de.danoeh.antennapodsp.R;
import de.danoeh.antennapodsp.SPAUtil;
import de.danoeh.antennapodsp.fragment.EpisodesFragment;
import de.danoeh.antennapodsp.fragment.ExternalPlayerFragment;

/**
 * The activity that is shown when the user launches the app.
 */
public class MainActivity extends ActionBarActivity {
    private static final String TAG = "MainActivity";

    public static final String ARG_FEED_ID = "feedID";

    private static final String SAVED_STATE_ACTION_BAR_HIDDEN = "actionbar_hidden";

    private static final int EVENTS = EventDistributor.DOWNLOAD_HANDLED
            | EventDistributor.DOWNLOAD_QUEUED;

    private SlidingUpPanelLayout slidingUpPanelLayout;
    private ExternalPlayerFragment externalPlayerFragment;

    private boolean isUpdatingFeeds;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        setTheme(UserPreferences.getTheme());
        supportRequestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        supportRequestWindowFeature(WindowCompat.FEATURE_ACTION_BAR_OVERLAY);
        super.onCreate(savedInstanceState);
        StorageUtils.checkStorageAvailability(this);
        getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setCustomView(R.layout.abs_layout);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.GINGERBREAD_MR1) {
            getSupportActionBar().setBackgroundDrawable(getResources().getDrawable(R.color.actionbar_gray));
        }

        setContentView(R.layout.main);
        slidingUpPanelLayout = (SlidingUpPanelLayout) findViewById(R.id.sliding_layout);
        slidingUpPanelLayout.setPanelSlideListener(panelSlideListener);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        int playerInitialState = ExternalPlayerFragment.ARG_INIT_ANCHORED;
        if (savedInstanceState != null && savedInstanceState.getBoolean(SAVED_STATE_ACTION_BAR_HIDDEN)) {
            getSupportActionBar().hide();
            slidingUpPanelLayout.expandPanel();
            playerInitialState = ExternalPlayerFragment.ARG_INIT_EPXANDED;
        }

        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fT = fragmentManager.beginTransaction();
        EpisodesFragment epf = (EpisodesFragment) fragmentManager.findFragmentById(R.id.main_view);
        if (epf == null) {
            long feedID = getIntent().getLongExtra(ARG_FEED_ID, 1L);
            epf = EpisodesFragment.newInstance(feedID);
        }
        fT.replace(R.id.main_view, epf);
        externalPlayerFragment = ExternalPlayerFragment.newInstance(playerInitialState);
        fT.replace(R.id.player_view, externalPlayerFragment);
        fT.commit();

        slidingUpPanelLayout.post(new Runnable() {
            @Override
            public void run() {
                slidingUpPanelLayout.hidePanel();
            }
        });

        SPAUtil.askForPodcatcherInstallation(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SAVED_STATE_ACTION_BAR_HIDDEN, !getSupportActionBar().isShowing());
    }

    @Override
    protected void onPause() {
        super.onPause();
        EventDistributor.getInstance().unregister(contentUpdate);
    }

    @Override
    protected void onResume() {
        super.onResume();
        StorageUtils.checkStorageAvailability(this);
        EventDistributor.getInstance().register(contentUpdate);

    }

    private EventDistributor.EventListener contentUpdate = new EventDistributor.EventListener() {

        @Override
        public void update(EventDistributor eventDistributor, Integer arg) {
            if ((EVENTS & arg) != 0) {
                if (BuildConfig.DEBUG)
                    Log.d(TAG, "Received contentUpdate Intent.");
                if (isUpdatingFeeds != updateChecker.isRefreshing()) {
                    supportInvalidateOptionsMenu();
                }
            }
        }
    };

    private static final MenuItemUtils.UpdateRefreshMenuItemChecker updateChecker = new MenuItemUtils.UpdateRefreshMenuItemChecker() {
        @Override
        public boolean isRefreshing() {
            return DownloadService.isRunning && DownloadRequester.getInstance().isDownloadingFeeds();
        }
    };

    private SlidingUpPanelLayout.PanelSlideListener panelSlideListener = new SlidingUpPanelLayout.PanelSlideListener() {

        float lastOffset = 0.0f;

        @Override
        public void onPanelSlide(View panel, float slideOffset) {
            Log.i("Panel", "offset:" + slideOffset + ", " + slidingUpPanelLayout.isPanelAnchored());
            final float THRESHOLD = 0.2f;

            if (slideOffset > lastOffset) {
                // panel is moved up
                if (slideOffset > THRESHOLD && getSupportActionBar().isShowing()) {
                    getSupportActionBar().hide();
                }
            } else if (0 < slideOffset && slideOffset < lastOffset) {
                // panel is moved down
                if (slideOffset < THRESHOLD && !getSupportActionBar().isShowing()) {
                    getSupportActionBar().show();
                }
            }
            if (slideOffset >= 0) {
                lastOffset = slideOffset;
            }
        }

        @Override
        public void onPanelCollapsed(View panel) {
            externalPlayerFragment.setFragmentState(ExternalPlayerFragment.FragmentState.ANCHORED);
            slidingUpPanelLayout.setDragView(externalPlayerFragment.getExpandView());
            getSupportActionBar().show();
        }

        @Override
        public void onPanelExpanded(View panel) {
            externalPlayerFragment.setFragmentState(ExternalPlayerFragment.FragmentState.EXPANDED);
            slidingUpPanelLayout.setDragView(externalPlayerFragment.getCollapseView());
            getSupportActionBar().hide();
        }

        @Override
        public void onPanelAnchored(View panel) {

        }

        @Override
        public void onPanelHidden(View view) {

        }
    };

    @Override
    public void onBackPressed() {
        if (slidingUpPanelLayout.isPanelExpanded()) {
            slidingUpPanelLayout.collapsePanel();
        } else {
            super.onBackPressed();
        }
    }

    public void onPlayerFragmentCreated(ExternalPlayerFragment fragment, ExternalPlayerFragment.FragmentState fragmentState) {
        if (fragmentState == ExternalPlayerFragment.FragmentState.EXPANDED) {
            slidingUpPanelLayout.setDragView(fragment.getCollapseView());
        } else {
            slidingUpPanelLayout.setDragView(fragment.getExpandView());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);

        isUpdatingFeeds = MenuItemUtils.updateRefreshMenuItem(menu, R.id.all_feed_refresh, updateChecker);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.all_feed_refresh:
                DBTasks.refreshAllFeeds(this, null);
                return true;
            case R.id.about_item:
                startActivity(new Intent(this, AboutActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void resetPlayer() {
        slidingUpPanelLayout.post(new Runnable() {
            @Override
            public void run() {
                slidingUpPanelLayout.collapsePanel();
                slidingUpPanelLayout.hidePanel();

            }
        });
    }

    public void openPlayer(final ExternalPlayerFragment.FragmentState state) {
        slidingUpPanelLayout.showPanel();
        slidingUpPanelLayout.post(new Runnable() {
            @Override
            public void run() {
                if (state == ExternalPlayerFragment.FragmentState.ANCHORED) {
                    slidingUpPanelLayout.collapsePanel();
                } else if (state == ExternalPlayerFragment.FragmentState.EXPANDED) {
                    slidingUpPanelLayout.expandPanel();
                }
            }
        });
    }
}
