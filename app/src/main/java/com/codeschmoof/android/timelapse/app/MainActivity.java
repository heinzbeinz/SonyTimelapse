package com.codeschmoof.android.timelapse.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.text.Html;
import android.text.method.CharacterPickerDialog;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.codeschmoof.android.timelapse.R;
import com.codeschmoof.android.timelapse.api.ServerDevice;
import com.codeschmoof.android.timelapse.service.DeviceInfo;
import com.codeschmoof.android.timelapse.service.TimelapseService;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends ActionBarActivity {
    private DeviceListAdapter deviceListAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        deviceListAdapter = new DeviceListAdapter(this);

        final IntentFilter filter = new IntentFilter(TimelapseService.REPLY_ACTION_SEARCH);
        LocalBroadcastManager.getInstance(this).registerReceiver(new ServiceReceiver(), filter);
    }

    @Override
    protected void onResume() {
        super.onResume();

        deviceListAdapter.clearDevices();
        getDevicesList().setAdapter(deviceListAdapter);

        getSearchButton().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TimelapseService.startActionSearch(MainActivity.this);
                getSearchButton().setEnabled(false);
            }
        });

        getDevicesList().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                final ListView listView = (ListView) adapterView;
                final DeviceListAdapter adapter = (DeviceListAdapter) adapterView.getAdapter();
                final DeviceInfo item = adapter.getItem(i);
                connectToDevice(item);
            }
        });

        // Show Wi-Fi SSID.
        updateSSID();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
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

    private void connectToDevice(DeviceInfo deviceInfo) {
        TimelapseService.startActionConnect(this, deviceInfo.getId());

        final Intent openActivity = new Intent(this, TimelapseActivity.class);
        startActivity(openActivity);
    }

    private void updateSSID() {
        final TextView textWifiSsid = getWifiSsidTextView();
        final WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        if (wifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            String htmlLabel = String.format("SSID: <b>%s</b>",
                    wifiInfo.getSSID());
            textWifiSsid.setText(Html.fromHtml(htmlLabel));
            getSearchButton().setEnabled(true);
        } else {
            textWifiSsid.setText(R.string.msg_wifi_disconnect);
            getSearchButton().setEnabled(false);
        }
    }

    private void updateDevices(String[] deviceJSONStrings) {
        final ListView devicesList = getDevicesList();
        final DeviceListAdapter adapter = (DeviceListAdapter) devicesList.getAdapter();
        adapter.clearDevices();

        for (String json: deviceJSONStrings) {
            try {
                final DeviceInfo info = DeviceInfo.fromJSON(json);
                adapter.addDevice(info);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private TextView getWifiSsidTextView() {
        return (TextView) findViewById(R.id.text_wifi_ssid);
    }

    private Button getSearchButton() {
        return (Button) findViewById(R.id.button_search);
    }

    private ListView getDevicesList() {
        return (ListView) findViewById(R.id.list_device);
    }

    private class ServiceReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(TimelapseService.REPLY_ACTION_SEARCH)) {
                final String[] deviceJSONStrings = intent.getStringArrayExtra(TimelapseService.REPLY_ACTION_SEARCH_DATA_DEVICES);

                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateDevices(deviceJSONStrings);
                        getSearchButton().setEnabled(true);
                    }
                });
            }
        }
    }

    // Adapter class for DeviceList
    private static class DeviceListAdapter extends BaseAdapter {

        private final List<DeviceInfo> mDeviceList;
        private final LayoutInflater mInflater;

        public DeviceListAdapter(Context context) {
            mDeviceList = new ArrayList<DeviceInfo>();
            mInflater = LayoutInflater.from(context);
        }

        public void addDevice(DeviceInfo device) {
            mDeviceList.add(device);
            notifyDataSetChanged();
        }

        public void clearDevices() {
            mDeviceList.clear();
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mDeviceList.size();
        }

        @Override
        public DeviceInfo getItem(int position) {
            return mDeviceList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return getItem(position).getId();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            TextView textView = (TextView) convertView;
            if (textView == null) {
                textView = (TextView) mInflater.inflate(
                        R.layout.device_list_item, null);
            }

            final DeviceInfo item = getItem(position);
            final String friendlyName = item.getFriendlyName();
            final String ip = item.getIp();

            // Label
            String htmlLabel = String.format("%s ", friendlyName)
                    + String.format(
                    "<br><small>IP:  <font color=\"blue\">%s</font></small>",
                    ip);
            textView.setText(Html.fromHtml(htmlLabel));

            return textView;
        }
    }
}
