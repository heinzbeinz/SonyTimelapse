package com.codeschmoof.android.timelapse.service;

import android.app.IntentService;
import android.app.Service;
import android.content.Intent;
import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.codeschmoof.android.timelapse.api.ServerDevice;
import com.codeschmoof.android.timelapse.api.SimpleRemoteApi;
import com.codeschmoof.android.timelapse.api.SimpleSsdpClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p/>
 */
public class TimelapseService extends Service {
    private static final String TAG = TimelapseService.class.getSimpleName();

    public static final String ACTION_SEARCH = "com.codeschmoof.android.timelapse.action.SEARCH";

    public static final String REPLY_ACTION_SEARCH = "com.codeschmoof.android.timelapse.action.REPLY_SEARCH";
    public static final String REPLY_ACTION_SEARCH_DATA_DEVICES = "com.codeschmoof.android.timelapse.data.REPLY_SEARCH.DEVICES";

    public static final String ACTION_CONNECT = "com.codeschmoof.android.timelapse.action.CONNECT";
    public static final String ACTION_CONNECT_DATA_DEVICE_ID = "com.codeschmoof.android.timelapse.data.CONNECT.DEVICE_ID";

    public static final String ACTION_DISCONNECT = "com.codeschmoof.android.timelapse.action.DISCONNECT";

    public static final String ACTION_START_TIMELAPSE = "com.codeschmoof.android.timelapse.action.START_TIMELAPSE";
    public static final String ACTION_START_TIMELAPSE_DATA_PERIOD = "com.codeschmoof.android.timelapse.data.START_TIMELAPSE.PERIOD";

    public static final String ACTION_UPDATE_TIMELAPSE = "com.codeschmoof.android.timelapse.action.UPDATE_TIMELAPSE";
    public static final String ACTION_UPDATE_TIMELAPSE_DATA_PERIOD = "com.codeschmoof.android.timelapse.data.UPDATE_TIMELAPSE.PERIOD";

    public static final String ACTION_STOP_TIMELAPSE = "com.codeschmoof.android.timelapse.action.STOP_TIMELAPSE";

    private final LocalBinder binder = new LocalBinder();
    private final SimpleSsdpClient ssdpClient = new SimpleSsdpClient();
    private final List<ServerDevice> devices = new ArrayList<ServerDevice>();
    private ExecutorService executor = null;
    private volatile SimpleRemoteApi currentApi = null;
    private Timer timer = null;


    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionSearch(Context context) {
        final Intent intent = new Intent(context, TimelapseService.class);
        intent.setAction(ACTION_SEARCH);
        context.startService(intent);
    }

    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionConnect(Context context, int deviceId) {
        final Intent intent = new Intent(context, TimelapseService.class);
        intent.setAction(ACTION_CONNECT);
        intent.putExtra(ACTION_CONNECT_DATA_DEVICE_ID, deviceId);
        context.startService(intent);
    }

    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionDisconnect(Context context) {
        final Intent intent = new Intent(context, TimelapseService.class);
        intent.setAction(ACTION_DISCONNECT);
        context.startService(intent);
    }

    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionStartTimelapse(Context context, int period) {
        final Intent intent = new Intent(context, TimelapseService.class);
        intent.setAction(ACTION_START_TIMELAPSE);
        intent.putExtra(ACTION_START_TIMELAPSE_DATA_PERIOD, period);
        context.startService(intent);
    }

    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionUpdateTimelapse(Context context, int period) {
        final Intent intent = new Intent(context, TimelapseService.class);
        intent.setAction(ACTION_UPDATE_TIMELAPSE);
        intent.putExtra(ACTION_UPDATE_TIMELAPSE_DATA_PERIOD, period);
        context.startService(intent);
    }

    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionStopTimelapse(Context context) {
        final Intent intent = new Intent(context, TimelapseService.class);
        intent.setAction(ACTION_STOP_TIMELAPSE);
        context.startService(intent);
    }

    public TimelapseService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();

        executor = Executors.newSingleThreadExecutor();
        Log.d(TAG, "Service created");
    }

    @Override
    public void onDestroy() {
        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
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

            if (ACTION_SEARCH.equals(action)) {
                handleActionSearch();
            } else if (ACTION_CONNECT.equals(action)) {
                handleActionConnect(intent.getIntExtra(ACTION_CONNECT_DATA_DEVICE_ID, -1));
            } else if (ACTION_DISCONNECT.equals(action)) {
                handleActionDisconnect();
            } else if (ACTION_START_TIMELAPSE.equals(action)) {
                handleActionStartTimelapse(intent.getIntExtra(ACTION_START_TIMELAPSE_DATA_PERIOD, -1));
            } else if (ACTION_UPDATE_TIMELAPSE.equals(action)) {
                handleActionUpdateTimelapse(intent.getIntExtra(ACTION_UPDATE_TIMELAPSE_DATA_PERIOD, -1));
            } else if (ACTION_STOP_TIMELAPSE.equals(action)) {
                handleActionStopTimelapse();
            }
        }
    }

    /**
     * Handle action Foo in the provided background thread with the provided
     * parameters.
     */
    private void handleActionSearch() {
        final CountDownLatch latch = new CountDownLatch(1);

        devices.clear();

        ssdpClient.search(new SimpleSsdpClient.SearchResultHandler() {
            @Override
            public void onDeviceFound(ServerDevice device) {
                Log.d(TAG, "found device " + device.getFriendlyName());

                devices.add(device);
            }

            @Override
            public void onFinished() {
                latch.countDown();
            }

            @Override
            public void onErrorFinished() {
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        final List<JSONObject> devicesJSON = getDevicesJSON();
        final String[] data = toArray(devicesJSON);

        final Intent reply = new Intent();
        reply.setAction(REPLY_ACTION_SEARCH);
        reply.putExtra(REPLY_ACTION_SEARCH_DATA_DEVICES, data);
        LocalBroadcastManager.getInstance(this).sendBroadcast(reply);
    }

    private void handleActionConnect(int deviceId) {
        if (deviceId < 0 || deviceId >= devices.size()) {
            return;
        }

        currentApi = new SimpleRemoteApi(devices.get(deviceId));
        try {
            currentApi.startRecMode();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            JSONObject res = currentApi.setFocusMode("MF");
            Log.d(TAG, "shoot mode: " + res.toString());
            final JSONObject apis = currentApi.getAvailableApiList();
            Log.d(TAG, "APIs: " + apis.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleActionDisconnect() {
        if (currentApi != null) {
            try {
                currentApi.stopRecMode();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        currentApi = null;
        reset();
    }

    private void handleActionStartTimelapse(long period) {
        if (currentApi == null) {
            return;
        }

        reset();
        timer = new Timer();

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                takePicture();
            }
        }, 0, period * 1000);
    }

    private void handleActionUpdateTimelapse(long period) {
        if (currentApi == null || timer == null) {
            return;
        }

        reset();
        timer = new Timer();

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                takePicture();
            }
        }, 0, period * 1000);
    }

    private void handleActionStopTimelapse() {
        reset();
    }

    private void takePicture() {
        final SimpleRemoteApi api = currentApi;

        if (api == null) {
            return;
        }

        try {
            Log.d(TAG, "taking picture...");
            final JSONObject ret = api.actTakePicture();
            Log.d(TAG, "returned " + ret.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void reset() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private List<JSONObject> getDevicesJSON() {
        final List<JSONObject> ret = new ArrayList<JSONObject>(devices.size());

        for (int i = 0; i < devices.size(); ++i) {
            try {
                final DeviceInfo info = DeviceInfo.fromServerDevice(i, devices.get(i));
                ret.add(info.toJSON());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return ret;
    }

    private static String[] toArray(List<JSONObject> objects) {
        final String[] ret = new String[objects.size()];

        for (int i = 0; i < objects.size(); ++i) {
            ret[i] = objects.get(i).toString();
        }

        return ret;
    }

    public class LocalBinder extends Binder {
        public TimelapseService getService() {
            return TimelapseService.this;
        }
    }
}
