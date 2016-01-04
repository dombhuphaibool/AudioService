package com.bandonleon.audioservice;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;

public class MainActivity extends AppCompatActivity implements AudioClientReceiver.AudioListener,
        AudioService.ServiceListener {

    private static int AUDIO_TRACK_RESOURCE_ID = R.raw.nocturne_op9_no1;
    private static String AUDIO_TRACK_TITLE = "Chopin Op.9 no.1";

    private boolean mIsLoaded = false;
    private boolean mIsPlaying = false;

    private AudioClientReceiver mAudioReceiver;
    private AudioLocalController mAudioController;
    private ServiceConnection mConnection;

    private Button mActionBtn;
    private ProgressBar mProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAudioReceiver = new AudioClientReceiver();
        mAudioReceiver.addAudioListener(this);

        mAudioController = null;
        mConnection = new AudioService.AudioServiceConnection(this);

        // Initialize states, but these will be updated in onResume()
        mIsLoaded = false;
        mIsPlaying = false;

        mActionBtn = (Button) findViewById(R.id.play_pause_btn);
        Button rewind15Btn = (Button) findViewById(R.id.rewind_15_btn);
        mProgressBar = (ProgressBar) findViewById(R.id.progress_bar);
        mProgressBar.setProgress(0);

        mActionBtn.setText(getString(R.string.play));

        mActionBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /*
                Action audioAction = mIsPlaying ? Action.PAUSE : (mIsLoaded ? Action.RESUME : Action.PLAY);
                Intent audioIntent = AudioService.getActionIntent(MainActivity.this, audioAction);
                if (audioAction == Action.PLAY) {
                    audioIntent.putExtra(AudioService.EXTRA_AUDIO_ID, R.raw.nocturne_op9_no1);
                }
                startService(audioIntent);
                */
                if (mIsPlaying) {
                    mAudioController.pauseAudio();
                } else if (mIsLoaded) {
                    mAudioController.resumeAudio();
                } else {
                    mAudioController.playAudio(AUDIO_TRACK_RESOURCE_ID);
                }
            }
        });

        rewind15Btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // startService(AudioService.getActionIntent(MainActivity.this, Action.REWIND_15_SEC));
                mAudioController.rewindAudio15Sec();
            }
        });
    }

    @Override
    protected void onDestroy() {
        mAudioReceiver.removeAudioListener(this);
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Establish a connection with the service.  We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        bindService(new Intent(this, AudioService.class), mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        // Detach our existing connection.
        unbindService(mConnection);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = AudioClientReceiver.getAudioReceiverFilter();
        LocalBroadcastManager.getInstance(this).registerReceiver(mAudioReceiver, filter);
    }

    @Override
    protected void onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mAudioReceiver);
        if (mAudioController != null && mAudioController.isAudioPlaying()) {
            // We need to start the service here because after we unbind, it will go away
            // if no one else has started the service
            startService(AudioService.getStartIdleIntent(this));
            mAudioController.startForegroundService(AUDIO_TRACK_TITLE);
        } else {
            // No need to stop the service here, ideally we would stop it when it logically makes
            // sense. Perhaps when the app exits.
            // stopService(AudioService.getStopIntent(this));
        }
        super.onPause();
    }

    private void updateUI() {
        String label = getString(mIsPlaying ? R.string.pause : R.string.play);
        mActionBtn.setText(label);
    }

    /***************************************************************************************
     *                              AudioClientReceiver.AudioListener
     ***************************************************************************************/
    @Override
    public void onAudioLoaded(int durationMsec) {
        mIsLoaded = true;
        mIsPlaying = false;
        mProgressBar.setMax(durationMsec);
        updateUI();
    }

    @Override
    public void onAudioStarted(int durationMsec) {
        mIsLoaded = true;
        mIsPlaying = true;
        mProgressBar.setMax(durationMsec);
        updateUI();
    }

    @Override
    public void onAudioCompleted() {
        mIsPlaying = false;
        mProgressBar.setProgress(0);
        updateUI();
    }

    @Override
    public void onAudioResumed(int positionMsec) {
        mIsPlaying = true;
        updateUI();
    }

    @Override
    public void onAudioPaused() {
        mIsPlaying = false;
        updateUI();
    }

    @Override
    public void onPositionUpdate(int positionMsec) {
        mProgressBar.setProgress(positionMsec);
    }

    @Override
    public void onStatusUpdate(boolean isLoaded, boolean isPlaying, int durationMsec, int positionMsec) {
        mIsLoaded = isLoaded;
        mIsPlaying = isPlaying;
        mProgressBar.setMax(durationMsec);
        mProgressBar.setProgress(positionMsec);
        updateUI();
    }

    /***************************************************************************************
     *                              AudioService.ServiceListener
     ***************************************************************************************/
    @Override
    public void audioServiceBound(AudioLocalController controller) {
        mAudioController = controller;
        if (mAudioController != null) {
            mAudioController.stopForegroundService(true);
            mAudioController.requestStatus();
        }
    }

    @Override
    public void audioServiceUnbound() {
        mAudioController = null;
    }
}
