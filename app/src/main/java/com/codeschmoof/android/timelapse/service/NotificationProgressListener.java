package com.codeschmoof.android.timelapse.service;

import android.app.NotificationManager;
import android.content.Context;
import android.support.v4.app.NotificationCompat;

import com.codeschmoof.android.timelapse.R;

/**
 * Created by Heinz on 01.06.2014.
 */
public class NotificationProgressListener implements ProgressListener {
    private final Context context;
    private final NotificationManager manager;
    private final NotificationCompat.Builder builder;
    private final int id = 1;

    public NotificationProgressListener(Context context) {
        this.context = context;
        this.manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        this.builder = new NotificationCompat.Builder(context);

    }

    @Override
    public void captureStarted(int period, int repeats) {
        builder.setContentTitle("Timelapse")
                .setContentText("Timelapse in progress")
                .setSmallIcon(R.drawable.ic_launcher);
    }

    @Override
    public void captureCanceled() {
        builder.setContentText("Timelapse canceled");
        builder.setProgress(0, 0, false);
        manager.notify(id, builder.build());
    }

    @Override
    public void captureFinished() {
        builder.setContentText("Timelapse finished");
        builder.setProgress(0, 0, false);
        manager.notify(id, builder.build());
    }

    @Override
    public void pictureTaken(int current, int max) {
        final int percent = Math.round(current / (float)max);
        builder.setContentText("Timelapse in progress: " + percent + "%");
        builder.setProgress(max, current, false);
        manager.notify(id, builder.build());
    }
}
