package com.codeschmoof.android.timelapse.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.ActionBarActivity;
import android.text.Html;
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
import com.codeschmoof.android.timelapse.api.SimpleSsdpClient;
import com.codeschmoof.android.timelapse.service.TimelapseService;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends ActionBarActivity {
    private final SimpleSsdpClient ssdpClient = new SimpleSsdpClient();

    private LocalServiceConnection connection = new LocalServiceConnection();
    private volatile TimelapseService service = null;
    private DeviceListAdapter deviceListAdapter;

    @Override
    protected void onStart() {
        super.onStart();

        // Start TimelapseService (service needs to stay alive even after activity is gone)
        TimelapseService.startActionStart(this);

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
        setContentView(R.layout.activity_main);

        deviceListAdapter = new DeviceListAdapter(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        deviceListAdapter.clearDevices();
        getDevicesList().setAdapter(deviceListAdapter);

        getSearchButton().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startSearch();
            }
        });

        getDevicesList().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                final ListView listView = (ListView) adapterView;
                final DeviceListAdapter adapter = (DeviceListAdapter) adapterView.getAdapter();
                final ServerDevice item = adapter.getItem(i);
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

    private void startSearch() {
        getSearchButton().setEnabled(false);

        ssdpClient.search(new SimpleSsdpClient.SearchResultHandler() {
            @Override
            public void onDeviceFound(final ServerDevice device) {
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        final DeviceListAdapter adapter = (DeviceListAdapter) getDevicesList().getAdapter();
                        adapter.addDevice(device);
                    }
                });
            }

            @Override
            public void onFinished() {
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        getSearchButton().setEnabled(true);
                    }
                });
            }

            @Override
            public void onErrorFinished() {
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        getSearchButton().setEnabled(true);
                    }
                });
            }
        });
    }

    private void connectToDevice(final ServerDevice deviceInfo) {
        final Intent openActivity = new Intent(this, TimelapseActivity.class);

        final Thread start = new Thread() {
            @Override
            public void run() {
                service.setDevice(deviceInfo);

                startActivity(openActivity);
            }
        };
        start.start();
    }

    private void updateSSID() {
        final TextView textWifiSsid = getWifiSsidTextView();
        final WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        if (wifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            String htmlLabel = String.format("SSID: <b>%s</b>",
                    wifiInfo.getSSID());
            textWifiSsid.setText(Html.fromHtml(htmlLabel));
            getSearchButton().setEnabled(!ssdpClient.isSearching());
        } else {
            textWifiSsid.setText(R.string.msg_wifi_disconnect);
            getSearchButton().setEnabled(false);
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

    // Adapter class for DeviceList
    private static class DeviceListAdapter extends BaseAdapter {

        private final List<ServerDevice> mDeviceList;
        private final LayoutInflater mInflater;

        public DeviceListAdapter(Context context) {
            mDeviceList = new ArrayList<ServerDevice>();
            mInflater = LayoutInflater.from(context);
        }

        public void addDevice(ServerDevice device) {
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
        public ServerDevice getItem(int position) {
            return mDeviceList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position; // hacky
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            TextView textView = (TextView) convertView;
            if (textView == null) {
                textView = (TextView) mInflater.inflate(
                        R.layout.device_list_item, null);
            }

            final ServerDevice item = getItem(position);
            final String friendlyName = item.getFriendlyName();
            final String ip = item.getIpAddres();

            // Label
            String htmlLabel = String.format("%s ", friendlyName)
                    + String.format(
                    "<br><small>IP:  <font color=\"blue\">%s</font></small>",
                    ip);
            textView.setText(Html.fromHtml(htmlLabel));

            return textView;
        }
    }

    private class LocalServiceConnection implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            final TimelapseService.LocalBinder binder = (TimelapseService.LocalBinder) iBinder;
            service = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            service = null;
        }
    }
}
