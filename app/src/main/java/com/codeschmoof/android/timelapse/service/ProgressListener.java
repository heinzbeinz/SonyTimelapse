package com.codeschmoof.android.timelapse.service;

/**
 * Created by Heinz on 01.06.2014.
 */
public interface ProgressListener {
    public void captureStarted(int period, int repeats);
    public void captureCanceled();
    public void captureFinished();

    public void pictureTaken(int current, int max);
}
