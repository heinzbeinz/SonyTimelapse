package com.codeschmoof.android.timelapse.service;

import android.util.JsonReader;

import com.codeschmoof.android.timelapse.api.ServerDevice;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by Heinz on 31.05.2014.
 */
public class DeviceInfo {
    private final int id;
    private final String friendlyName;
    private final String url;
    private final String ip;

    public DeviceInfo(int id, String friendlyName, String url, String ip) {
        this.id = id;
        this.friendlyName = friendlyName;
        this.url = url;
        this.ip = ip;
    }

    public int getId() {
        return id;
    }

    public String getFriendlyName() {
        return friendlyName;
    }

    public String getIp() {
        return ip;
    }

    public String getUrl() {
        return url;
    }

    public JSONObject toJSON() throws JSONException {
        final JSONObject ret = new JSONObject();
        ret.put("id", id);
        ret.put("friendlyName", friendlyName);
        ret.put("url", url);
        ret.put("ip", ip);
        return ret;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DeviceInfo that = (DeviceInfo) o;

        if (id != that.id) return false;
        if (friendlyName != null ? !friendlyName.equals(that.friendlyName) : that.friendlyName != null)
            return false;
        if (ip != null ? !ip.equals(that.ip) : that.ip != null) return false;
        if (url != null ? !url.equals(that.url) : that.url != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = id;
        result = 31 * result + (friendlyName != null ? friendlyName.hashCode() : 0);
        result = 31 * result + (url != null ? url.hashCode() : 0);
        result = 31 * result + (ip != null ? ip.hashCode() : 0);
        return result;
    }

    public static DeviceInfo fromServerDevice(int id, ServerDevice device) {
        return new DeviceInfo(id, device.getFriendlyName(), device.getDDUrl(), device.getIpAddres());
    }

    public static DeviceInfo fromJSON(JSONObject object) throws JSONException {
        return new DeviceInfo(object.getInt("id"), object.getString("friendlyName"), object.getString("url"), object.getString("ip"));
    }

    public static DeviceInfo fromJSON(String jsonString) throws JSONException {
        final JSONObject object = new JSONObject(jsonString);
        return new DeviceInfo(object.getInt("id"), object.getString("friendlyName"), object.getString("url"), object.getString("ip"));
    }
}