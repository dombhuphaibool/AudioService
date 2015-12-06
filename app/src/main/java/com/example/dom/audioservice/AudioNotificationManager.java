package com.example.dom.audioservice;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import com.example.dom.audioservice.AudioService.Action;

/**
 * Created by dom on 12/6/15.
 */
public class AudioNotificationManager {
    private static final int SERVICE_NOTIFICATION_ID = 1234;

    private NotificationManager mNotificationManager;
    private Notification.Builder mNotificationBuilder;
    private RemoteViews mNotificationView;

    public AudioNotificationManager(Context context) {
        mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        mNotificationView = new RemoteViews(context.getPackageName(), R.layout.audio_notification);
        mNotificationView.setTextViewText(R.id.title, context.getText(R.string.notification_title));
        PendingIntent resumeIntent = PendingIntent.getService(context, 0, AudioService.getActionIntent(context, Action.RESUME), 0);
        mNotificationView.setOnClickPendingIntent(R.id.play_btn, resumeIntent);
        PendingIntent pauseIntent = PendingIntent.getService(context, 0, AudioService.getActionIntent(context, AudioService.Action.PAUSE), 0);
        mNotificationView.setOnClickPendingIntent(R.id.pause_btn, pauseIntent);

        Intent notificationIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);
        mNotificationBuilder = new Notification.Builder(context)
                .setContent(mNotificationView)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent);
        // .setDeleteIntent()
    }

    private void updateNotificationView(boolean isPlaying) {
        mNotificationView.setViewVisibility(R.id.play_btn, isPlaying ? View.GONE : View.VISIBLE);
        mNotificationView.setViewVisibility(R.id.pause_btn, isPlaying ? View.VISIBLE : View.GONE);
    }

    public void updateContent(String content) {
        mNotificationView.setTextViewText(R.id.content, content);
    }

    public void updatePlayState(boolean isPlaying) {
        updateNotificationView(isPlaying);
    }

    public void updateProgress(int max, int progress) {
        mNotificationView.setProgressBar(R.id.progress, max, progress, false);
    }

    public void sendNotification() {
        mNotificationBuilder.setContent(mNotificationView);
        mNotificationManager.notify(SERVICE_NOTIFICATION_ID, mNotificationBuilder.build());
    }

    public int getNotificationId() {
        return SERVICE_NOTIFICATION_ID;
    }

    public Notification getAudioNotification() {
        mNotificationBuilder.setContent(mNotificationView);
        return mNotificationBuilder.build();
    }
}
