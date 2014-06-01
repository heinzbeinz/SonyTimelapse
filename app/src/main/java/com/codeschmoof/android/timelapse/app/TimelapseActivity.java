package com.codeschmoof.android.timelapse.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import com.codeschmoof.android.timelapse.R;
import com.codeschmoof.android.timelapse.service.ProgressListener;
import com.codeschmoof.android.timelapse.service.TimelapseService;
import com.codeschmoof.android.timelapse.util.TimeSpan;

import java.util.concurrent.TimeUnit;


public class TimelapseActivity extends ActionBarActivity {
    private enum Mode {
        START_TIMELAPSE,
        STOP_TIMELAPSE;

        public Mode getInverse() {
            return this == START_TIMELAPSE ? STOP_TIMELAPSE : START_TIMELAPSE;
        }
    }

    private LocalServiceConnection connection = new LocalServiceConnection();
    private volatile TimelapseService service = null;
    private Mode currentMode = Mode.START_TIMELAPSE;

    @Override
    protected void onStart() {
        super.onStart();

        // Bind to TimelapseService
        final Intent intent = new Intent(this, TimelapseService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (service != null) {
            unbindService(connection);
            service = null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_timelapse);
    }

    @Override
    protected void onResume() {
        super.onResume();

        //updateTimelapseUiState();
        getTimelapseSeekBar().setEnabled(false);
        getRepeatsSeekBar().setEnabled(false);
        getTimelapseButton().setEnabled(false);

        getTimelapseSeekBar().setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                updateTimelapseText();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                updateTimelapseText();
            }
        });

        getRepeatsSeekBar().setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                updateTimelapseText();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                updateTimelapseText();
            }
        });

        getTimelapseButton().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getTimelapseButton().setEnabled(false);
                performTimelapseAction();
            }
        });

        if (service != null) {
            updateAndBindServiceListener();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (service != null) {
            service.removeListener(progressListener);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.timelapse, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateTimelapseText() {
        try {
            final int period = getTimelapseSeekBar().getProgress() + 1;
            final int repeats = getRepeatsSeekBar().getProgress() + 1;
            final TimeSpan timeLeft = TimeSpan.of(period * repeats, TimeUnit.SECONDS);
            final String text = String.format("%d s * %d = %s left", period, repeats, timeLeft.toString());
            getTimelapseTextView().setText(text);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void updateTimelapseUiState() {
        switch (currentMode) {
            case START_TIMELAPSE:
                getTimelapseButton().setEnabled(true);
                getTimelapseButton().setText(R.string.button_start_timelapse);
                getTimelapseSeekBar().setEnabled(true);
                getRepeatsSeekBar().setEnabled(true);
                break;

            case STOP_TIMELAPSE:
                getTimelapseButton().setEnabled(true);
                getTimelapseButton().setText(R.string.button_stop_timelapse);
                getTimelapseSeekBar().setEnabled(false);
                getRepeatsSeekBar().setEnabled(false);
                break;
        }

        updateTimelapseText();
    }

    private void performTimelapseAction() {
        final Mode m = currentMode;
        final TimelapseService s = service;
        final int period = getTimelapseSeekBar().getProgress() + 1;
        final int repeats = getRepeatsSeekBar().getProgress() + 1;

        final Thread start = new Thread() {
            @Override
            public void run() {
                switch (m) {
                    case START_TIMELAPSE:
                        s.startCapture(period, repeats);
                        break;

                    case STOP_TIMELAPSE:
                        s.cancelCapture();
                        break;
                }
            }
        };
        start.start();
    }

    private void updateAndBindServiceListener() {
        currentMode = service.getMode() != TimelapseService.Mode.CAPTURING ? Mode.START_TIMELAPSE : Mode.STOP_TIMELAPSE;
        getTimelapseSeekBar().setProgress(service.getPeriod() - 1);
        getRepeatsSeekBar().setProgress(service.getMaxRepeats() - 1);
        updateTimelapseUiState();

        service.addListener(progressListener);
    }

    private void unbindServiceListener() {
        service.removeListener(progressListener);
    }

    private Button getTimelapseButton() {
        return (Button) findViewById(R.id.button_start_stop_timelapse);
    }

    private TextView getTimelapseTextView() {
        return (TextView) findViewById(R.id.label_timeleft);
    }

    private SeekBar getTimelapseSeekBar() {
        return (SeekBar) findViewById(R.id.seekBar_timelapse);
    }

    private SeekBar getRepeatsSeekBar() {
        return (SeekBar) findViewById(R.id.seekBar_repeats);
    }

    private class LocalServiceConnection implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            final TimelapseService.LocalBinder binder = (TimelapseService.LocalBinder) iBinder;

            TimelapseActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    service = binder.getService();
                    updateAndBindServiceListener();
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            TimelapseActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    unbindServiceListener();
                    service = null;
                }
            });
        }
    }

    private final ProgressListener progressListener = new ProgressListener() {

        @Override
        public void captureStarted(int period, int repeats) {
            TimelapseActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    currentMode = Mode.STOP_TIMELAPSE;
                    updateTimelapseUiState();
                }
            });
        }

        @Override
        public void captureCanceled() {
            TimelapseActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    currentMode = Mode.START_TIMELAPSE;
                    updateTimelapseUiState();
                }
            });
        }

        @Override
        public void captureFinished() {
            TimelapseActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    currentMode = Mode.START_TIMELAPSE;
                    updateTimelapseUiState();
                }
            });
        }

        @Override
        public void pictureTaken(int current, int max) {
        }
    };
}
