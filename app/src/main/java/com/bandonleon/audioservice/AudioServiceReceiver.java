package com.bandonleon.audioservice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

/**
 * Created by dom on 12/7/15.
 */
public class AudioServiceReceiver extends BroadcastReceiver {
    private static final String RESUME_AUDIO = "com.bandonleon.serverreceiver.action.RESUME_AUDIO";
    private static final String PAUSE_AUDIO = "com.bandonleon.serverreceiver.action.PAUSE_AUDIO";
    private static final String REWIND_AUDIO_FULL = "com.bandonleon.serverreceiver.action.REWIND_AUDIO_FULL";
    private static final String REWIND_AUDIO_15_SEC = "com.bandonleon.serverreceiver.action.REWIND_AUDIO_15_SEC";
    private static final String REQUEST_STATUS = "com.bandonleon.serverreceiver.action.REQUEST_STATUS";
    private static final String DISMISS_NOTIFICATION = "com.bandonleon.serverreceiver.action.DISMISS_NOTIFICATION";

    public static IntentFilter getAudioReceiverFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(RESUME_AUDIO);
        filter.addAction(PAUSE_AUDIO);
        filter.addAction(REWIND_AUDIO_FULL);
        filter.addAction(REWIND_AUDIO_15_SEC);
        filter.addAction(REQUEST_STATUS);
        filter.addAction(DISMISS_NOTIFICATION);
        return filter;
    }

    public static Intent getActionIntent(Action action) {
        return new Intent(action.getActionName());
    }

    public enum Action {
        RESUME(RESUME_AUDIO),
        PAUSE(PAUSE_AUDIO),
        REWIND_FULL(REWIND_AUDIO_FULL),
        REWIND_15_SEC(REWIND_AUDIO_15_SEC),
        REQ_STATUS(REQUEST_STATUS),
        DISMISS(DISMISS_NOTIFICATION);

        private final String mActionName;

        Action(String actionName) {
            mActionName = actionName;
        }

        String getActionName() {
            return mActionName;
        }
    }

    private AudioController mAudioController;

    public AudioServiceReceiver(AudioController audioController) {
        mAudioController = audioController;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        switch (intent.getAction()) {
            case RESUME_AUDIO:
                mAudioController.resumeAudio();
                break;

            case PAUSE_AUDIO:
                mAudioController.pauseAudio();
                break;

            case REWIND_AUDIO_FULL:
                mAudioController.rewindAudioFull();
                break;

            case REWIND_AUDIO_15_SEC:
                mAudioController.rewindAudio15Sec();
                break;

            case REQUEST_STATUS:
                mAudioController.requestStatus();
                break;

            case DISMISS_NOTIFICATION:
                // Nothing to do for now...
                break;

            default:
                // Nothing to do
                break;
        }
    }
}
