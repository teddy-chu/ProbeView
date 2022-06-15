package com.ultrasoundprobe.probeview.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.IOException;

public class Keystore {
    private static final String TAG = "Keystore";

    private static String filename;
    private static Keystore keystore;

    private final SharedPreferences sharedPreferences;

    public Keystore(Context context) {
        filename = context.getPackageName();

        Log.d(TAG, "Initialize " + filename);

        sharedPreferences = context.getApplicationContext().getSharedPreferences(filename,0);
    }

    public static Keystore getInstance(Context context) {
        if (keystore == null) {
            keystore = new Keystore(context);
        }
        return keystore;
    }

    public static void freeInstance() {
        keystore = null;
    }

    public void set(String key, String value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putString(key, value);
        editor.apply();

        Log.d(TAG, "Set " + key + ": " + value);
    }

    public String get(String key, String... defaultValue) {
        String value = sharedPreferences.getString(key,
                defaultValue.length == 0 ? null : defaultValue[0]);

        Log.d(TAG, "Get " + key + ": " + value);

        return value;
    }

    public void setInt(String key, int value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putInt(key, value);
        editor.apply();

        Log.d(TAG, "Set " + key + ": " + value);
    }

    public int getInt(String key, Integer... defaultValue) {
        int value = sharedPreferences.getInt(key,
                defaultValue.length == 0 ? 0 : defaultValue[0]);

        Log.d(TAG, "Get " + key + ": " + value);

        return value;
    }

    public void setObject(String key, Object value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();

        try {
            editor.putString(key, ObjectSerializer.serialize(value));
        } catch (IOException e) {
            e.printStackTrace();
        }
        editor.apply();

        Log.d(TAG, "Set " + key + ": " + value);
    }

    public Object getObject(String key, Object... defaultValue) {
        Object value = null;

        try {
            value = ObjectSerializer.deserialize(sharedPreferences.getString(key,
                    defaultValue.length == 0 ? null : ObjectSerializer.serialize(defaultValue[0])));
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        Log.d(TAG, "Get " + key + ": " + value);

        return value;
    }

    public void clear() {
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.clear();
        editor.apply();

        Log.d(TAG, "Clear " + filename);
    }

    public void remove() {
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.remove(filename);
        editor.apply();

        Log.d(TAG, "Remove " + filename);
    }
}
