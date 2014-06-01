package com.codeschmoof.android.timelapse.app;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import com.codeschmoof.android.timelapse.R;
import com.codeschmoof.android.timelapse.service.TimelapseService;


public class TimelapseActivity extends ActionBarActivity {
    private enum Mode {
        START_TIMELAPSE,
        STOP_TIMELAPSE;

        public Mode getInverse() {
            return this == START_TIMELAPSE ? STOP_TIMELAPSE : START_TIMELAPSE;
        }
    }

    private Mode currentMode = Mode.START_TIMELAPSE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_timelapse);
    }

    @Override
    protected void onResume() {
        super.onResume();

        currentMode = Mode.START_TIMELAPSE;

        updateTimelapseText();
        updateTimelapseButton();

        getTimelapseSeekBar().setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                TimelapseService.startActionUpdateTimelapse(TimelapseActivity.this, seekBar.getProgress());
                updateTimelapseText();
            }
        });

        getTimelapseButton().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                performTimelapseAction();

                currentMode = currentMode.getInverse();
                updateTimelapseButton();
            }
        });
    }

    @Override
    protected void onDestroy() {
        TimelapseService.startActionDisconnect(this);

        super.onDestroy();
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
        final int progress = getTimelapseSeekBar().getProgress();
        getTimelapseTextView().setText(progress + " s");
    }

    private void updateTimelapseButton() {
        switch (currentMode) {
            case START_TIMELAPSE:
                getTimelapseButton().setText(R.string.button_start_timelapse);
                break;

            case STOP_TIMELAPSE:
                getTimelapseButton().setText(R.string.button_stop_timelapse);
                break;
        }
    }

    private void performTimelapseAction() {
        switch (currentMode) {
            case START_TIMELAPSE:
                final int value = getTimelapseSeekBar().getProgress();
                TimelapseService.startActionStartTimelapse(this, value);
                break;

            case STOP_TIMELAPSE:
                TimelapseService.startActionStopTimelapse(this);
                break;
        }
    }

    private Button getTimelapseButton() {
        return (Button) findViewById(R.id.button_start_stop_timelapse);
    }

    private TextView getTimelapseTextView() {
        return (TextView) findViewById(R.id.textview_timelapseSeconds);
    }

    private SeekBar getTimelapseSeekBar() {
        return (SeekBar) findViewById(R.id.seekBar_timelapse);
    }
}
