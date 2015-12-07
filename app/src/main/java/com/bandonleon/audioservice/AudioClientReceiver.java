package com.bandonleon.audioservice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import java.util.HashSet;
import java.util.Set;

public class AudioClientReceiver extends BroadcastReceiver {

    private static final String AUDIO_STARTED = "com.bandonleon.clientreceiver.action.AUDIO_STARTED";
    private static final String AUDIO_COMPLETED = "com.bandonleon.clientreceiver.action.AUDIO_COMPLETED";
    private static final String AUDIO_PAUSED = "com.bandonleon.clientreceiver.action.AUDIO_PAUSED";
    private static final String AUDIO_RESUMED = "com.bandonleon.clientreceiver.action.AUDIO_RESUMED";
    private static final String AUDIO_GET_STATUS = "com.bandonleon.clientreceiver.action.GET_STATUS";
    private static final String AUDIO_POSITION_UPDATE = "com.bandonleon.clientreceiver.action.PROGRESS_UPDATE";

    private static final String EXTRA_DURATION = "com.bandonleon.clientreceiver.extra.DURATION";
    private static final String EXTRA_POSITION = "com.bandonleon.clientreceiver.extra.POSITION";
    private static final String EXTRA_IS_LOADED = "com.bandonleon.clientreceiver.extra.IS_LOADED";
    private static final String EXTRA_IS_PLAYING = "com.bandonleon.clientreceiver.extra.IS_PLAYING";

    public static IntentFilter getAudioReceiverFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(AUDIO_STARTED);
        filter.addAction(AUDIO_COMPLETED);
        filter.addAction(AUDIO_PAUSED);
        filter.addAction(AUDIO_RESUMED);
        filter.addAction(AUDIO_GET_STATUS);
        filter.addAction(AUDIO_POSITION_UPDATE);
        return filter;
    }

    public static Intent getPositionUpdateIntent(int positionMsec) {
        Intent intent = new Intent(AUDIO_POSITION_UPDATE);
        intent.putExtra(EXTRA_POSITION, positionMsec);
        return intent;
    }

    public static Intent getAudioStartedIntent(int durationMsec) {
        Intent intent = new Intent(AUDIO_STARTED);
        intent.putExtra(EXTRA_DURATION, durationMsec);
        return intent;
    }

    public static Intent getAudioResumeIntent(int positionMsec) {
        Intent intent = new Intent(AUDIO_RESUMED);
        intent.putExtra(EXTRA_POSITION, positionMsec);
        return intent;
    }

    public static Intent getGetStatusIntent(boolean isLoaded, boolean isPlaying, int durationMsec, int positionMsec) {
        Intent getStatusIntent = new Intent(AUDIO_GET_STATUS);
        getStatusIntent.putExtra(EXTRA_IS_LOADED, isLoaded);
        getStatusIntent.putExtra(EXTRA_IS_PLAYING, isPlaying);
        getStatusIntent.putExtra(EXTRA_DURATION, durationMsec);
        getStatusIntent.putExtra(EXTRA_POSITION, positionMsec);
        return getStatusIntent;
    }

    public static Intent getActionIntent(Action action) {
        return new Intent(action.getActionName());
    }

    public enum Action {
        COMPLETED(AUDIO_COMPLETED),
        PAUSED(AUDIO_PAUSED);

        private final String mActionName;

        Action(String actionName) {
            mActionName = actionName;
        }

        String getActionName() {
            return mActionName;
        }
    }

    public interface AudioListener {
        void onAudioStarted(int durationMsec);
        void onAudioCompleted();
        void onAudioPaused();
        void onAudioResumed(int positionMsec);
        void onPositionUpdate(int positionMsec);
        void onStatusUpdate(boolean isLoaded, boolean isPlaying, int durationMsec, int positionMsec);
    }

    private Set<AudioListener> mListeners = new HashSet<>();

    @Override
    public void onReceive(Context context, Intent intent) {
        int durationMsec = 0;
        int positionMsec = 0;
        switch (intent.getAction()) {
            case AUDIO_STARTED:
                durationMsec = intent.getIntExtra(EXTRA_DURATION, 0);
                for (AudioListener listener : mListeners) {
                    listener.onAudioStarted(durationMsec);
                }
                break;

            case AUDIO_COMPLETED:
                for (AudioListener listener : mListeners) {
                    listener.onAudioCompleted();
                }
                break;

            case AUDIO_PAUSED:
                for (AudioListener listener : mListeners) {
                    listener.onAudioPaused();
                }
                break;

            case AUDIO_RESUMED:
                positionMsec = intent.getIntExtra(EXTRA_POSITION, 0);
                for (AudioListener listener : mListeners) {
                    listener.onAudioResumed(positionMsec);
                }
                break;

            case AUDIO_POSITION_UPDATE:
                positionMsec = intent.getIntExtra(EXTRA_POSITION, 0);
                for (AudioListener listener : mListeners) {
                    listener.onPositionUpdate(positionMsec);
                }
                break;

            case AUDIO_GET_STATUS:
                boolean isLoaded = intent.getBooleanExtra(EXTRA_IS_LOADED, false);
                boolean isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false);
                durationMsec = intent.getIntExtra(EXTRA_DURATION, 0);
                positionMsec = intent.getIntExtra(EXTRA_POSITION, 0);
                for (AudioListener listener : mListeners) {
                    listener.onStatusUpdate(isLoaded, isPlaying, durationMsec, positionMsec);
                }
                break;

            default:
                break;
        }
    }

    public void addAudioListener(AudioListener listener) {
        mListeners.add(listener);
    }

    public void removeAudioListener(AudioListener listener) {
        mListeners.remove(listener);
    }
}
