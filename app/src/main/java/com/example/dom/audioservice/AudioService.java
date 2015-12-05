package com.example.dom.audioservice;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;

import java.io.IOException;

public class AudioService extends Service implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

    private static final String ACTION_PLAY = "com.example.action.PLAY";
    private static final String ACTION_RESUME = "com.example.action.RESUME";
    private static final String ACTION_PAUSE = "com.example.action.PAUSE";
    private static final String ACTION_REWIND_FULL = "com.example.action.REWIND_FULL";
    private static final String ACTION_REWIND_15_SEC = "com.example.action.REWIND_15_SEC";
    private static final String ACTION_GET_STATUS = "com.example.action.GET_STATUS";

    public static final String EXTRA_AUDIO_ID = "com.example.extra.AUDIO_ID";

    private static final long UPDATE_INTERVAL_MSEC = 1000 / 40; // Try updating at 40 Hz

    private int mLastPositionMsec;

    private Handler mMainHandler;
    private Runnable mPositionUpdater;

    private LocalBroadcastManager mBroadcastManager;
    private MediaPlayer mAudioPlayer;

    private boolean mIsLoaded;

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

        int positionMsec = 0;
        switch (intent.getAction()) {
            case ACTION_PLAY:
                int audioId = intent.getIntExtra(EXTRA_AUDIO_ID, 0);
                if (audioId != 0) {
                    AssetFileDescriptor assetFD = getResources().openRawResourceFd(audioId);
                    try {
                        mAudioPlayer.setDataSource(assetFD.getFileDescriptor(),
                                assetFD.getStartOffset(), assetFD.getLength());
                        mAudioPlayer.prepareAsync();
                    } catch (IOException ex) {
                        // @TODO: Log exception here...
                    }
                }
                break;

            case ACTION_RESUME:
                doResume();
                positionMsec = mAudioPlayer.getCurrentPosition();
                mBroadcastManager.sendBroadcast(AudioReceiver.getAudioResumeIntent(positionMsec));
                break;

            case ACTION_PAUSE:
                doPause();
                mBroadcastManager.sendBroadcast(AudioReceiver.getActionIntent(AudioReceiver.Action.PAUSED));
                break;

            case ACTION_REWIND_FULL:
                doPause();
                mAudioPlayer.seekTo(0);
                break;

            case ACTION_REWIND_15_SEC:
                boolean resumePlay = mAudioPlayer.isPlaying();
                doPause();
                int seekPosMsec = mAudioPlayer.getCurrentPosition() - 15000;
                seekPosMsec = Math.max(seekPosMsec, 0);
                mAudioPlayer.seekTo(seekPosMsec);
                if (resumePlay) {
                    doResume();
                } else {
                    // Post current position
                    positionMsec = mAudioPlayer != null ? mAudioPlayer.getCurrentPosition() : 0;
                    mBroadcastManager.sendBroadcast(AudioReceiver.getPositionUpdateIntent(positionMsec));
                }
                break;

            case ACTION_GET_STATUS:
                boolean isPlaying = mAudioPlayer.isPlaying();
                int durationMsec = mAudioPlayer.getDuration();
                positionMsec = mAudioPlayer.getCurrentPosition();
                mBroadcastManager.sendBroadcast(AudioReceiver.getGetStatusIntent(mIsLoaded, isPlaying, durationMsec, positionMsec));
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
    }

    private void doResume() {
        if (!mAudioPlayer.isPlaying()) {
            mAudioPlayer.start();
            startProgressUpdates();
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
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
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

        startForegroundService();
    }

    private static final int ONGOING_NOTIFICATION_ID = 1234;
    private void startForegroundService() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        Notification notification = new Notification.Builder(this)
                .setContentTitle(getText(R.string.notification_title))
                .setContentText(getText(R.string.notification_message))
                .setSmallIcon(R.drawable.ic_notification)
                // .setLargeIcon(Icon.createWithResource(this, R.drawable.ic_notification_large))
                .setContentIntent(pendingIntent)
                .build();
        startForeground(ONGOING_NOTIFICATION_ID, notification);
    }

    private void stopForegroundService() {
        stopForeground(true);
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (mp != mAudioPlayer) {
            // @TODO: Log error!
            return;
        }

        mBroadcastManager.sendBroadcast(AudioReceiver.getActionIntent(AudioReceiver.Action.COMPLETED));
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        // @TODO: Log errors
        return false;   // We're not currently handling errors
    }
}
