package com.bandonleon.audioservice;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class AudioService extends Service implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, AudioLocalController {

    private static final String ACTION_PLAY = "com.bandonleon.audioservice.action.PLAY";
    private static final String ACTION_IDLE = "com.bandonleon.audioservice.action.IDLE";

    public static final String EXTRA_AUDIO_ID = "com.bandonleon.audioservice.extra.AUDIO_ID";

    private static final long UPDATE_INTERVAL_MSEC = 1000 / 40; // Try updating at 40 Hz

    private enum ServiceState {
        FOREGROUND_WITH_NOTIFICATION,   // Foreground services requires notification (just being explicit here)
        BACKGROUND_WITH_NOTIFICATION,
        BACKGROUND
    }

    // Binder given to clients
    private final LocalBinder mBinder = new LocalBinder();

    private ServiceState mState;
    private boolean mIsLoaded;
    private boolean mPlayOnLoad;
    private int mLastPositionMsec;

    private Handler mMainHandler;

    private LocalBroadcastManager mBroadcastManager;
    private AudioServiceReceiver mServiceReceiver;

    private MediaPlayer mAudioPlayer;
    private Runnable mPositionUpdater;
    private AudioNotificationManager mNotificationManager;

    public static Intent getPlayAudioIntent(Context context, int audioResId) {
        Intent intent = new Intent(context, AudioService.class);
        intent.setAction(ACTION_PLAY);
        intent.putExtra(EXTRA_AUDIO_ID, audioResId);
        return intent;
    }

    public static Intent getStartIdleIntent(Context context) {
        Intent intent = new Intent(context, AudioService.class);
        intent.setAction(ACTION_IDLE);
        return intent;
    }

    public static Intent getStopIntent(Context context) {
        return new Intent(context, AudioService.class);
    }

    private interface UnbindListener {
        void onUnbind(LocalBinder binder);
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        private Set<UnbindListener> listeners = new HashSet<>();

        public AudioLocalController getLocalController() {
            // Return this instance of AudioService so clients can call public methods
            return (AudioLocalController) AudioService.this;
        }

        public void addUnbindListener(UnbindListener listener) {
            listeners.add(listener);
        }

        public boolean removeUnbindListener(UnbindListener listener) {
            return listeners.remove(listener);
        }

        public void notifyUnbind() {
            for (UnbindListener listener : listeners) {
                listener.onUnbind(this);
            }
            listeners.clear();
        }
    }

    public interface ServiceListener {
        void audioServiceBound(AudioLocalController controller);
        void audioServiceUnbound();
    }

    public static class AudioServiceConnection implements ServiceConnection, UnbindListener {
        private ServiceListener mListener;

        public AudioServiceConnection(ServiceListener listener) {
            mListener = listener;
        }

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            LocalBinder localBinder = (LocalBinder)service;
            localBinder.addUnbindListener(this);
            mListener.audioServiceBound(localBinder.getLocalController());
            // mAudioController.stopForegroundService(true);
            // mAudioController.requestStatus();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mListener.audioServiceUnbound();
        }

        @Override
        public void onUnbind(AudioService.LocalBinder binder) {
            mListener.audioServiceUnbound();
        }
    };

    private boolean isForeground() {
        return mState == ServiceState.FOREGROUND_WITH_NOTIFICATION;
    }

    private boolean hasNotification() {
        return mState == ServiceState.BACKGROUND_WITH_NOTIFICATION ||
                mState == ServiceState.FOREGROUND_WITH_NOTIFICATION;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mState = ServiceState.BACKGROUND;
        mIsLoaded = false;
        mPlayOnLoad = false;
        mLastPositionMsec = 0;

        mMainHandler = new Handler();
        mBroadcastManager = LocalBroadcastManager.getInstance(this);
        mServiceReceiver = new AudioServiceReceiver(this);
        IntentFilter filter = AudioServiceReceiver.getAudioReceiverFilter();
        // RemoteViews in notification uses a PendingIntent so we cannot use
        // LocalBroadcastManager to register our receiver.
        registerReceiver(mServiceReceiver, filter);

        mAudioPlayer = new MediaPlayer();
        mAudioPlayer.setOnPreparedListener(this);
        mAudioPlayer.setOnCompletionListener(this);
        mAudioPlayer.setOnErrorListener(this);

        mPositionUpdater = new Runnable() {
            @Override
            public void run() {

                int currPosMsec = mAudioPlayer != null ? mAudioPlayer.getCurrentPosition() : 0;
                if (currPosMsec != mLastPositionMsec) {
                    mBroadcastManager.sendBroadcast(AudioClientReceiver.getPositionUpdateIntent(currPosMsec));
                    mLastPositionMsec = currPosMsec;
                }

                if (hasNotification()) {
                    mNotificationManager.updateProgress(mAudioPlayer.getDuration(), currPosMsec);
                    mNotificationManager.sendNotification();
                }

                mMainHandler.postDelayed(this, UPDATE_INTERVAL_MSEC);
            }
        };

        mNotificationManager = new AudioNotificationManager(this);
    }

    @Override
    public void onDestroy() {
        if (mAudioPlayer != null) {
            mAudioPlayer.release();
            mAudioPlayer = null;
        }

        unregisterReceiver(mServiceReceiver);

        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean validAction = false;

        if (mAudioPlayer == null || intent == null || TextUtils.isEmpty(intent.getAction())) {
            // @TODO: Log fatal error here...
        } else if (ACTION_PLAY.equals(intent.getAction())) {
            int audioId = intent.getIntExtra(EXTRA_AUDIO_ID, 0);
            playAudio(audioId);
            validAction = true;
        } else if (ACTION_IDLE.equals(intent.getAction())) {
            // Nothing to do, just start idling
            validAction = true;
        }

        if (!validAction) {
            final int stopId = startId;
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    stopSelf(stopId);
                }
            });
        }

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mBinder.notifyUnbind();
        return false;
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

    private void doStop() {
        mAudioPlayer.stop();
        stopProgressUpdates();
    }

    private void startProgressUpdates() {
        mMainHandler.post(mPositionUpdater);
    }

    private void stopProgressUpdates() {
        mMainHandler.removeCallbacks(mPositionUpdater);
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        if (mp != mAudioPlayer) {
            // @TODO: Log error!
            return;
        }

        mIsLoaded = true;
        int durationMsec = mAudioPlayer.getDuration();
        mBroadcastManager.sendBroadcast(AudioClientReceiver.getAudioLoadedIntent(durationMsec));

        if (mPlayOnLoad) {
            mBroadcastManager.sendBroadcast(AudioClientReceiver.getAudioStartedIntent(durationMsec));
            doResume();
        }
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (mp != mAudioPlayer) {
            // @TODO: Log error!
            return;
        }

        mBroadcastManager.sendBroadcast(AudioClientReceiver.getActionIntent(AudioClientReceiver.Action.COMPLETED));

        if (hasNotification()) {
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
    private void loadAudio(int audioResId, boolean playOnLoad) {
        if (audioResId != 0) {
            AssetFileDescriptor assetFD = getResources().openRawResourceFd(audioResId);
            try {
                mAudioPlayer.setDataSource(assetFD.getFileDescriptor(),
                        assetFD.getStartOffset(), assetFD.getLength());
                mPlayOnLoad = playOnLoad;
                mAudioPlayer.prepareAsync();
            } catch (IOException ex) {
                // @TODO: Log exception here...
            }
        }
    }

    @Override
    public void loadAudio(int audioResId) {
        loadAudio(audioResId, false);
    }

    @Override
    public void playAudio(int audioResId) {
        loadAudio(audioResId, true);
    }

    @Override
    public void resumeAudio() {
        doResume();
        int positionMsec = mAudioPlayer.getCurrentPosition();
        mBroadcastManager.sendBroadcast(AudioClientReceiver.getAudioResumeIntent(positionMsec));

        if (hasNotification()) {
            startForegroundService(null);
            mNotificationManager.updatePlayState(true);
            mNotificationManager.sendNotification();
        }
    }

    @Override
    public void pauseAudio() {
        doPause();
        mBroadcastManager.sendBroadcast(AudioClientReceiver.getActionIntent(AudioClientReceiver.Action.PAUSED));

        if (hasNotification()) {
            stopForegroundService(false);
            mNotificationManager.updatePlayState(false);
            mNotificationManager.sendNotification();
        }
    }

    @Override
    public void seekAudio(int msec) {
        mAudioPlayer.seekTo(msec);
        int positionMsec = mAudioPlayer.getCurrentPosition();
        mBroadcastManager.sendBroadcast(AudioClientReceiver.getPositionUpdateIntent(positionMsec));
    }

    @Override
    public void rewindAudioFull() {
        doPause();
        mAudioPlayer.seekTo(0);

        if (hasNotification()) {
            stopForegroundService(false);
            mNotificationManager.updatePlayState(false);
            mNotificationManager.sendNotification();
        }
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
            mBroadcastManager.sendBroadcast(AudioClientReceiver.getPositionUpdateIntent(positionMsec));
        }
    }

    @Override
    public void requestStatus() {
        boolean isPlaying = mAudioPlayer.isPlaying();
        int durationMsec = mAudioPlayer.getDuration();
        durationMsec = Math.max(durationMsec, 1);
        int positionMsec = mAudioPlayer.getCurrentPosition();
        mBroadcastManager.sendBroadcast(AudioClientReceiver.getGetStatusIntent(mIsLoaded, isPlaying, durationMsec, positionMsec));
    }

    @Override
    public boolean isAudioPlaying() {
        return (mAudioPlayer != null && mAudioPlayer.isPlaying());
    }

    @Override
    public void startForegroundService(String notificationContent) {
        if (notificationContent != null) {
            mNotificationManager.updateContent(notificationContent);
        }
        mNotificationManager.updatePlayState(mAudioPlayer.isPlaying());
        if (!isForeground()) {
            mState = ServiceState.FOREGROUND_WITH_NOTIFICATION;
            startForeground(mNotificationManager.getNotificationId(), mNotificationManager.getAudioNotification());
        }
    }

    @Override
    public void stopForegroundService(boolean dismissNotification) {
        if (isForeground()) {
            stopForeground(dismissNotification);
        }
        mState = dismissNotification ? ServiceState.BACKGROUND : ServiceState.BACKGROUND_WITH_NOTIFICATION;
    }
}
