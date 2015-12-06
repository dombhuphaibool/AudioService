package com.example.dom.audioservice;

import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;

import com.example.dom.audioservice.AudioService.Action;

public class MainActivity extends AppCompatActivity implements AudioReceiver.AudioListener {

    private boolean mIsLoaded = false;
    private boolean mIsPlaying = false;

    private AudioReceiver mAudioReceiver;

    private Button mActionBtn;
    private ProgressBar mProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAudioReceiver = new AudioReceiver();
        mAudioReceiver.addAudioListener(this);

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
                Action audioAction = mIsPlaying ? Action.PAUSE : (mIsLoaded ? Action.RESUME : Action.PLAY);
                Intent audioIntent = AudioService.getActionIntent(MainActivity.this, audioAction);
                if (audioAction == Action.PLAY) {
                    audioIntent.putExtra(AudioService.EXTRA_AUDIO_ID, R.raw.nocturne_op9_no1);
                }
                startService(audioIntent);
            }
        });

        rewind15Btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService(AudioService.getActionIntent(MainActivity.this, Action.REWIND_15_SEC));
            }
        });
    }

    @Override
    protected void onDestroy() {
        mAudioReceiver.removeAudioListener(this);
        super.onDestroy();
    }

    private void updateUI() {
        String label = getString(mIsPlaying ? R.string.pause : R.string.play);
        mActionBtn.setText(label);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = AudioReceiver.getAudioReceiverFilter();
        LocalBroadcastManager.getInstance(this).registerReceiver(mAudioReceiver, filter);
        startService(AudioService.getActionIntent(MainActivity.this, Action.GET_STATUS));
    }

    @Override
    protected void onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mAudioReceiver);
        super.onPause();
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
}
