package com.example.dom.audioservice;

/**
 * Created by dom on 12/6/15.
 */
public interface AudioLocalController {
    void playAudio(int audioResId);
    void resumeAudio();
    void pauseAudio();
    void rewindAudioFull();
    void rewindAudio15Sec();
    void requestStatus();

    boolean isAudioPlaying();
    void startForegroundService(String notificationContent);
    void stopForegroundService();
}
