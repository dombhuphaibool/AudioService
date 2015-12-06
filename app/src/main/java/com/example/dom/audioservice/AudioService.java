package com.example.dom.audioservice;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;

import java.io.IOException;

public class AudioService extends Service implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, AudioLocalController {

    private static final String ACTION_PLAY = "com.example.action.PLAY";
    private static final String ACTION_RESUME = "com.example.action.RESUME";
    private static final String ACTION_PAUSE = "com.example.action.PAUSE";
    private static final String ACTION_REWIND_FULL = "com.example.action.REWIND_FULL";
    private static final String ACTION_REWIND_15_SEC = "com.example.action.REWIND_15_SEC";
    private static final String ACTION_GET_STATUS = "com.example.action.GET_STATUS";

    public static final String EXTRA_AUDIO_ID = "com.example.extra.AUDIO_ID";

    private static final long UPDATE_INTERVAL_MSEC = 1000 / 40; // Try updating at 40 Hz

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();

    private int mLastPositionMsec;

    private Handler mMainHandler;
    private Runnable mPositionUpdater;

    private LocalBroadcastManager mBroadcastManager;
    private MediaPlayer mAudioPlayer;

    private boolean mIsLoaded;

    private AudioNotificationManager mNotificationManager;
    private boolean mSendNotification;

    public enum Action {
        PLAY(ACTION_PLAY),
        RESUME(ACTION_RESUME),
        PAUSE(ACTION_PAUSE),
        REWIND_FULL(ACTION_REWIND_FULL),
        REWIND_15_SEC(ACTION_REWIND_15_SEC),
        GET_STATUS(ACTION_GET_STATUS);

        private final String mActionName;

        Action(String actionName) {
            mActionName = actionName;
        }

        String getActionName() {
            return mActionName;
        }
    }
    public static Intent getActionIntent(Context context, Action action) {
        Intent actionIntent = new Intent(context, AudioService.class);
        actionIntent.setAction(action.getActionName());
        return actionIntent;
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        AudioLocalController getLocalController() {
            // Return this instance of AudioService so clients can call public methods
            return (AudioLocalController) AudioService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mIsLoaded = false;
        mLastPositionMsec = 0;

        mMainHandler = new Handler();
        mBroadcastManager = LocalBroadcastManager.getInstance(this);

        mAudioPlayer = new MediaPlayer();
        mAudioPlayer.setOnPreparedListener(this);
        mAudioPlayer.setOnCompletionListener(this);
        mAudioPlayer.setOnErrorListener(this);

        mNotificationManager = new AudioNotificationManager(this);
        mSendNotification = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mAudioPlayer != null) {
            mAudioPlayer.release();
            mAudioPlayer = null;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mAudioPlayer == null || intent == null || TextUtils.isEmpty(intent.getAction())) {
            // @TODO: Log fatal error here...
            return START_NOT_STICKY;
        }

        switch (intent.getAction()) {
            case ACTION_PLAY:
                int audioId = intent.getIntExtra(EXTRA_AUDIO_ID, 0);
                playAudio(audioId);
                break;

            case ACTION_RESUME:
                resumeAudio();
                break;

            case ACTION_PAUSE:
                pauseAudio();
                break;

            case ACTION_REWIND_FULL:
                rewindAudioFull();
                break;

            case ACTION_REWIND_15_SEC:
                rewindAudio15Sec();
                break;

            case ACTION_GET_STATUS:
                requestStatus();
                break;

            default:
                // @TODO: Log unsupported action
                break;
        }

        return START_STICKY;
    }

    private void doPause() {
        if (mAudioPlayer.isPlaying()) {
            mAudioPlayer.pause();
            stopProgressUpdates();
        }
        if (mSendNotification) {
            mNotificationManager.updatePlayState(false);
            mNotificationManager.sendNotification();
        }
    }

    private void doResume() {
        if (!mAudioPlayer.isPlaying()) {
            mAudioPlayer.start();
            startProgressUpdates();
        }
        if (mSendNotification) {
            mNotificationManager.updatePlayState(true);
            mNotificationManager.sendNotification();
        }
    }

    private void startProgressUpdates() {
        mPositionUpdater = new Runnable() {
            @Override
            public void run() {

                int currPosMsec = mAudioPlayer != null ? mAudioPlayer.getCurrentPosition() : 0;
                if (currPosMsec != mLastPositionMsec) {
                    mBroadcastManager.sendBroadcast(AudioReceiver.getPositionUpdateIntent(currPosMsec));
                    mLastPositionMsec = currPosMsec;
                }

                if (mSendNotification) {
                    mNotificationManager.updateProgress(mAudioPlayer.getDuration(), currPosMsec);
                    mNotificationManager.sendNotification();
                }

                mMainHandler.postDelayed(this, UPDATE_INTERVAL_MSEC);
            }
        };

        mMainHandler.post(mPositionUpdater);
    }

    private void stopProgressUpdates() {
        mMainHandler.removeCallbacks(mPositionUpdater);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        if (mp != mAudioPlayer) {
            // @TODO: Log error!
            return;
        }

        mIsLoaded = true;
        int durationMsec = mAudioPlayer.getDuration();
        mBroadcastManager.sendBroadcast(AudioReceiver.getAudioStartedIntent(durationMsec));

        doResume();
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (mp != mAudioPlayer) {
            // @TODO: Log error!
            return;
        }

        mBroadcastManager.sendBroadcast(AudioReceiver.getActionIntent(AudioReceiver.Action.COMPLETED));

        if (mSendNotification) {
            mNotificationManager.updatePlayState(false);
            mNotificationManager.updateProgress(1, 0);
            mNotificationManager.sendNotification();
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        // @TODO: Log errors
        return false;   // We're not currently handling errors
    }

    /***************************************************************************************
     *                               LocalAudioController
     ***************************************************************************************/
    @Override
    public void playAudio(int audioResId) {
        if (audioResId != 0) {
            AssetFileDescriptor assetFD = getResources().openRawResourceFd(audioResId);
            try {
                mAudioPlayer.setDataSource(assetFD.getFileDescriptor(),
                        assetFD.getStartOffset(), assetFD.getLength());
                mAudioPlayer.prepareAsync();
            } catch (IOException ex) {
                // @TODO: Log exception here...
            }
        }
    }

    @Override
    public void resumeAudio() {
        doResume();
        int positionMsec = mAudioPlayer.getCurrentPosition();
        mBroadcastManager.sendBroadcast(AudioReceiver.getAudioResumeIntent(positionMsec));
    }

    @Override
    public void pauseAudio() {
        doPause();
        mBroadcastManager.sendBroadcast(AudioReceiver.getActionIntent(AudioReceiver.Action.PAUSED));
    }

    @Override
    public void rewindAudioFull() {
        doPause();
        mAudioPlayer.seekTo(0);
    }

    @Override
    public void rewindAudio15Sec() {
        boolean resumePlay = mAudioPlayer.isPlaying();
        doPause();
        int seekPosMsec = mAudioPlayer.getCurrentPosition() - 15000;
        seekPosMsec = Math.max(seekPosMsec, 0);
        mAudioPlayer.seekTo(seekPosMsec);
        if (resumePlay) {
            doResume();
        } else {
            // Post current position
            int positionMsec = mAudioPlayer != null ? mAudioPlayer.getCurrentPosition() : 0;
            mBroadcastManager.sendBroadcast(AudioReceiver.getPositionUpdateIntent(positionMsec));
        }
    }

    @Override
    public void requestStatus() {
        boolean isPlaying = mAudioPlayer.isPlaying();
        int durationMsec = mAudioPlayer.getDuration();
        durationMsec = Math.max(durationMsec, 1);
        int positionMsec = mAudioPlayer.getCurrentPosition();
        mBroadcastManager.sendBroadcast(AudioReceiver.getGetStatusIntent(mIsLoaded, isPlaying, durationMsec, positionMsec));
    }

    @Override
    public boolean isAudioPlaying() {
        return (mAudioPlayer != null && mAudioPlayer.isPlaying());
    }

    @Override
    public void startForegroundService(String notificationContent) {
        mSendNotification = true;
        mNotificationManager.updateContent(notificationContent);
        mNotificationManager.updatePlayState(mAudioPlayer.isPlaying());
        startForeground(mNotificationManager.getNotificationId(), mNotificationManager.getAudioNotification());
    }

    @Override
    public void stopForegroundService() {
        if (mSendNotification) {
            stopForeground(true);
            mSendNotification = false;
        }
    }
}
