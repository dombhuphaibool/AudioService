package com.bandonleon.audioservice;

/**
 * Created by dom on 12/6/15.
 */
public interface AudioLocalController extends AudioController {
    boolean isAudioPlaying();
    void startForegroundService(String notificationContent);
    void stopForegroundService(boolean dismissNotification);
}
