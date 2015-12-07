package com.bandonleon.audioservice;

/**
 * Created by dom on 12/7/15.
 */
public interface AudioController {
    void playAudio(int audioResId);
    void resumeAudio();
    void pauseAudio();
    void rewindAudioFull();
    void rewindAudio15Sec();
    void requestStatus();
}
