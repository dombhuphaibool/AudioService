package com.example.dom.audioservice;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;

import com.example.dom.audioservice.AudioService.Action;

public class MainActivity extends AppCompatActivity implements AudioReceiver.AudioListener {
    private static int AUDIO_TRACK_RESOURCE_ID = R.raw.nocturne_op9_no1;
    private static String AUDIO_TRACK_TITLE = "Chopin Op.9 no.1";

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
        IntentFilter filter = AudioReceiver.getAudioReceiverFilter();
        LocalBroadcastManager.getInstance(this).registerReceiver(mAudioReceiver, filter);
        if (mAudioController != null) {
            mAudioController.stopForegroundService();
            mAudioController.requestStatus();
        }
        // startService(AudioService.getActionIntent(this, Action.GET_STATUS));
    }

    @Override
    protected void onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mAudioReceiver);
        if (mAudioController != null && mAudioController.isAudioPlaying()) {
            startService(AudioService.getActionIntent(this, Action.GET_STATUS));
            mAudioController.startForegroundService(AUDIO_TRACK_TITLE);
        }
        super.onPause();
    }

    private void updateUI() {
        String label = getString(mIsPlaying ? R.string.pause : R.string.play);
        mActionBtn.setText(label);
    }

    /***************************************************************************************
     *                              AudioReceiver.AudioListener
     ***************************************************************************************/
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

    private AudioLocalController mAudioController;
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mAudioController = ((AudioService.LocalBinder)service).getLocalController();
            mAudioController.stopForegroundService();
            mAudioController.requestStatus();
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mAudioController = null;
        }
    };
}
