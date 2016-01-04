package com.bandonleon.audioservice;

/**
 * Created by dom on 12/7/15.
 */
public interface AudioController {
    void loadAudio(int audioResId);
    void playAudio(int audioResId);
    void resumeAudio();
    void pauseAudio();
    void seekAudio(int msec);
    void rewindAudioFull();
    void rewindAudio15Sec();
    void requestStatus();
}
