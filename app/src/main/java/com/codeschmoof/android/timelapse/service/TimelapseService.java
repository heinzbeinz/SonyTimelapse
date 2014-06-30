package com.codeschmoof.android.timelapse.service;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import com.codeschmoof.android.timelapse.api.ServerDevice;
import com.codeschmoof.android.timelapse.api.SimpleRemoteApi;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p/>
 */
public class TimelapseService extends Service {
    public enum Mode {
        STARTED,
        INITIALIZED,
        CAPTURING
    }
    private static final String TAG = TimelapseService.class.getSimpleName();

    public static final String ACTION_START = "com.codeschmoof.android.timelapse.action.START";
    public static final String ACTION_CAPTURE = "com.codeschmoof.android.timelapse.action.CAPTURE";

    private final LocalBinder binder = new LocalBinder();
    private final CopyOnWriteArrayList<ProgressListener> listener = new CopyOnWriteArrayList<ProgressListener>();
    private AlarmManager alarmManager = null;
    private ExecutorService executor = null;
    private PendingIntent alarmIntent = null;
    private volatile Mode mode = Mode.STARTED;
    private ServerDevice currentDevice = null;
    private SimpleRemoteApi currentApi = null;
    private int period = 10;
    private int current = 0;
    private int maxRepeats = 60;

    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionStart(Context context) {
        final Intent intent = new Intent(context, TimelapseService.class);
        intent.setAction(ACTION_START);
        context.startService(intent);
    }

    public TimelapseService() {
    }

    public Mode getMode() {
        return mode;
    }

    public int getPeriod() {
        return period;
    }

    public int getMaxRepeats() {
        return maxRepeats;
    }

    public void addListener(ProgressListener listener) {
        this.listener.add(listener);
    }

    public void removeListener(ProgressListener listener) {
        this.listener.remove(listener);
    }

    public synchronized void setDevice(ServerDevice device) {
        if (device != null) {
            currentDevice = device;
            currentApi = new SimpleRemoteApi(currentDevice);
            try {
                currentApi.startRecMode();
                mode = Mode.INITIALIZED;
            } catch (Throwable e) {
                Log.e(TAG, e.getMessage(), e);
            }
        } else {
            if (currentApi != null) {
                try {
                    currentApi.stopRecMode();
                } catch (Throwable e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }

            mode = Mode.STARTED;
            currentDevice = null;
            currentApi = null;
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public synchronized void startCapture(final int period, int repeats) {
        if (mode != Mode.INITIALIZED) {
            return;
        }

        mode = Mode.CAPTURING;
        this.period = period;
        this.maxRepeats = repeats;
        this.current = 0;

        for (ProgressListener l: listener) {
            l.captureStarted(period, repeats);
        }

        alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, 0, alarmIntent);
    }

    public void cancelCapture() {
        if (mode != Mode.CAPTURING) {
            return;
        }

        stopCapture();

        for (ProgressListener l: listener) {
            l.captureCanceled();
        }
    }

    public synchronized void modifyCapture(int period, int repeats) {
        this.period = period;
        this.maxRepeats = repeats;
        this.current = Math.min(this.current, this.maxRepeats);
    }

    private synchronized void stopCapture() {
        current = maxRepeats = 0;
        alarmManager.cancel(alarmIntent);
        mode = Mode.INITIALIZED;
    }
    
    @Override
    public void onCreate() {
        super.onCreate();

        executor = Executors.newSingleThreadExecutor();
        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        final Intent intent = new Intent(this, getClass());
        intent.setAction(ACTION_CAPTURE);
        alarmIntent = PendingIntent.getService(this, 0, intent, 0);
        Log.d(TAG, "Service created");

        addListener(new NotificationProgressListener(this));
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    @Override
    public void onDestroy() {
        current = maxRepeats = 0;
        alarmManager.cancel(alarmIntent);
        alarmManager = null;
        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Log.e(TAG, e.getMessage(), e);
        }
        Log.d(TAG, "Service destroyed");

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                onHandleIntent(intent);
            }
        });

        return START_STICKY;
    }

    private void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            Log.d(TAG, "Got " + action);

            if (ACTION_START.equals(action)) {
                handleActionStart();
            } else if (ACTION_CAPTURE.equals(action)) {
                handleActionCapture();
            }
        }
    }

    private void handleActionStart() {
        // nothing to do...
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void handleActionCapture() {
        final long nextScheduling = SystemClock.elapsedRealtime() + period * 1000;
        takePicture();
        if (current < maxRepeats) {
            Log.d(TAG, "Current " + current + "/" + maxRepeats);
            alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, nextScheduling, alarmIntent);
        } else {
            stopCapture();

            for (ProgressListener l: listener) {
                l.captureFinished();
            }
        }
    }

    private void takePicture() {
        if (current >= maxRepeats) {
            return;
        }

        current++;

        try {
            currentApi.actTakePicture();
        } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
        }

        for (ProgressListener l: listener) {
            l.pictureTaken(current, maxRepeats);
        }
    }

    public class LocalBinder extends Binder {
        public TimelapseService getService() {
            return TimelapseService.this;
        }
    }
}
