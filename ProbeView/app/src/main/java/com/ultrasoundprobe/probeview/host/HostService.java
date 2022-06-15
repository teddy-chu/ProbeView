package com.ultrasoundprobe.probeview.host;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.handshake.ServerHandshake;

public class HostService extends Service {
    private static final String TAG = "HostService";

    private static final int HOST_SOCKET_PORT = 3000;

    private ServiceBinder serviceBinder;
    private List<ServiceCallback> serviceCallbacks;
    private final Object serviceCallbackMutex = new Object();

    private Handler handler;
    private Runnable runnable;
    private int timerInterval = 1000;
    private boolean isTimerEnabled = false;

    private ConnectivityManager connectivityManager;
    private WifiManager wifiManager;

    private HostInfo hostInfo = null;
    private ImageDownload imageDownload;
    private Thread imageTransferThread;
    private boolean isImageTransferThreadRunning = false;

    private Socket hostSocket;
    private BufferedReader hostSocketBufferIn;
    private PrintWriter hostSocketBufferOut;

    private WebSocketClient webSocketClient;

    public interface ServiceCallback {
        void onScanResult(HostInfo hostInfo, int rssi);
        void onImageReceived(HostInfo hostInfo, Bitmap bitmap);
        void onHostConnected(HostInfo hostInfo);
        void onHostDisconnected(HostInfo hostInfo);
        void onTimerExpired(HostInfo hostInfo);
    }

    public class ServiceBinder extends Binder {
        public HostService getService() {
            return HostService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate()");

        serviceBinder = new HostService.ServiceBinder();
        serviceCallbacks = new ArrayList<>();

        // Setup timer
        handler = new Handler();
        runnable = new Runnable() {
            @Override
            public void run() {
                long startTime = System.currentTimeMillis();

                synchronized (serviceCallbackMutex) {
                    for (ServiceCallback serviceCallback : serviceCallbacks) {
                        if (serviceCallback == null)
                            continue;

                        serviceCallback.onTimerExpired(hostInfo);
                    }
                }

                long elapsedTime = System.currentTimeMillis() - startTime;

                if (isTimerEnabled)
                    handler.postDelayed(this, Math.max(timerInterval - elapsedTime, 0));
            }
        };

        connectivityManager = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        wifiManager = (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");

        disconnectHost();

        enableTimer(false);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind()");
        return serviceBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind()");
        return super.onUnbind(intent);
    }

    public boolean registerCallback(ServiceCallback callback) {
        if (serviceCallbacks.contains(callback))
            return true;
        else {
            boolean ret;

            synchronized (serviceCallbackMutex) {
                ret = serviceCallbacks.add(callback);
            }

            if (!ret) {
                Log.e(TAG, "Failed to register callback (" + callback + ")");
                return false;
            }
        }

        // Log.d(TAG, "Callback has been registered (" + callback + ")");

        return true;
    }

    public boolean unregisterCallback(ServiceCallback callback) {
        if (!serviceCallbacks.contains(callback)) {
            Log.e(TAG, "Callback had not been registered before (" + callback + ")");
            return false;
        }
        else {
            boolean ret;

            synchronized (serviceCallbackMutex) {
                ret = serviceCallbacks.remove(callback);
            }

            if (!ret) {
                Log.e(TAG, "Failed to unregister callback (" + callback + ")");
                return false;
            }
        }

        // Log.d(TAG, "Callback has been unregistered (" + callback + ")");

        return true;
    }

    public void setTimerInterval(int milliseconds) {
        if (!isTimerEnabled) {
            if (milliseconds < 0)
                return;

            timerInterval = milliseconds;
        }
    }

    public void enableTimer(boolean enable) {
        if (enable && !isTimerEnabled) {
            isTimerEnabled = handler.postDelayed(runnable, 0);
        } else if (!enable && isTimerEnabled) {
            handler.removeCallbacks(runnable);
            isTimerEnabled = false;
        }
    }

    public HostInfo getHostInfo() {
        if (!isHostConnected())
            return null;

        return hostInfo;
    }

    public boolean isHostConnected() {
        return imageTransferThread != null;
    }

    public boolean connectHost(String url) {
        if (isHostConnected())
            return true;
        else if (!isInternetConnected()) {
            Log.e(TAG, "No Internet connection");
            return false;
        }
        else if (wifiManager == null)
            return false;

        WifiInfo wifiInfo = wifiManager.getConnectionInfo();

        if (wifiInfo == null) {
            Log.e(TAG, "No WiFi information");
            return false;
        }

        int ipAddress = wifiInfo.getIpAddress();

        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ipAddress = Integer.reverseBytes(ipAddress);
        }

        byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();
        String ipAddressString;

        try {
            ipAddressString = InetAddress.getByAddress(ipByteArray).getHostAddress();
        } catch (UnknownHostException e) {
            ipAddressString = null;
        }

        // TODO: Support more sources other than WiFi for info settings
        hostInfo = new HostInfo(wifiInfo.getSSID(), ipAddressString);

        // Start a thread for scan image download from a server
        imageTransferThread = new Thread() {
            @Override
            public void run() {
                Log.d(TAG, "Image transfer thread started");

                isImageTransferThreadRunning = true;

                synchronized (serviceCallbackMutex) {
                    for (ServiceCallback serviceCallback : serviceCallbacks) {
                        if (serviceCallback == null)
                            continue;

                        serviceCallback.onHostConnected(hostInfo);
                    }
                }

                while (isInternetConnected() && isImageTransferThreadRunning) {
                    if (!startImageDownload(url)) {
                        // Log.e(TAG, "Failed to start image download");
                    }

                    try {
                        sleep(0);
                    } catch (InterruptedException e) {
                        Log.e(TAG, e.getMessage());
                    }
                }

                if (isImageTransferThreadRunning) {
                    Log.e(TAG, "Internet connection broken");
                    disconnectHost(false);
                }

                synchronized (serviceCallbackMutex) {
                    for (ServiceCallback serviceCallback : serviceCallbacks) {
                        if (serviceCallback == null)
                            continue;

                        serviceCallback.onHostDisconnected(hostInfo);
                    }
                }

                Log.d(TAG, "Image transfer thread stopped");
            }
        };
        imageTransferThread.start();

        return true;
    }

    public boolean disconnectHost() {
        return disconnectHost(true);
    }

    private boolean disconnectHost(boolean sync) {
        boolean ret = true;

        if (!isHostConnected())
            return false;

        if (!closeHostSocket_()) {
            Log.e(TAG, "Failed to close socket");
            ret = false;
        }

        if (!closeHostWebSocket_()) {
            Log.e(TAG, "Failed to close Web socket");
            ret = false;
        }

        // Set flag to terminate the running thread
        isImageTransferThreadRunning = false;

        if (!stopImageDownload()) {
            Log.e(TAG, "Failed to stop image transfer");
            ret = false;
        }

        if (sync) {
            try {
                // Wait for thread to finish
                imageTransferThread.join();
            } catch (InterruptedException e) {
                Log.e(TAG, e.getMessage());
                ret = false;
            }
        }

        imageTransferThread = null;

        return ret;
    }

    public boolean abortHost() {
        return disconnectHost();
    }

    public boolean openHostSocket(String address, int timeout) {
        boolean ret = true;

        if (isHostSocketOpened())
            return false;

        try {
            hostSocket = new Socket();
            // Connect socket with timeout
            hostSocket.connect(new InetSocketAddress(address, HOST_SOCKET_PORT), timeout);

            hostSocketBufferIn = new BufferedReader(new InputStreamReader(
                    hostSocket.getInputStream()));
            hostSocketBufferOut = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
                    hostSocket.getOutputStream())), true);
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());

            closeHostSocket_();
            ret = false;
        }

        return ret && isHostSocketOpened();
    }

    public boolean closeHostSocket() {
        return isHostSocketOpened() && closeHostSocket_();
    }

    private boolean closeHostSocket_() {
        boolean ret;

        try {
            if (hostSocket != null && !hostSocket.isClosed())
                hostSocket.close();
            if (hostSocketBufferIn != null)
                hostSocketBufferIn.close();
            if (hostSocketBufferOut != null)
                hostSocketBufferOut.close();

            ret = hostSocket == null || hostSocket.isClosed();
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());

            ret = false;
        } finally {
            hostSocket = null;
            hostSocketBufferIn = null;
            hostSocketBufferOut = null;
        }

        return ret;
    }

    public boolean writeHostSocket(String data) {
        if (!isHostSocketOpened())
            return false;

        hostSocketBufferOut.println(data);

        return !hostSocketBufferOut.checkError();
    }

    public String readHostSocket(int timeout) {
        if (!isHostSocketOpened())
            return null;

        String message = null;

        try {
            // Set timeout for blocked reading
            if (hostSocket.getSoTimeout() != timeout)
                hostSocket.setSoTimeout(timeout);

            message = hostSocketBufferIn.readLine();
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }

        return message;
    }

    public boolean isHostSocketOpened() {
        return hostSocket != null && !hostSocket.isClosed() &&
                hostSocketBufferOut != null && hostSocketBufferIn != null;
    }

    public boolean openHostWebSocket(String address, int timeout) {
        boolean ret = true;

        if (isHostWebSocketOpened())
            return false;

        try {
            Map<String, String> header =
                    new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);

            webSocketClient = new WebSocketClient(
                    new URI("ws://" + address),
                    new Draft_17(),     // Select the standard implementation of WebSocket
                    header,
                    timeout) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    Log.d(TAG, "onOpen: " + handshakedata);
                }

                @Override
                public void onMessage(String message) {
                    Log.d(TAG, "onMessage: " + message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.d(TAG, "onClose: " + reason);
                }

                @Override
                public void onError(Exception e) {
                    Log.d(TAG, "onError: " + e.getMessage());
                }
            };

            webSocketClient.connectBlocking();
        } catch (URISyntaxException | InterruptedException e) {
            Log.e(TAG, e.getMessage());

            closeHostWebSocket_();
            ret = false;
        }

        return ret && isHostWebSocketOpened();
    }

    public boolean closeHostWebSocket() {
        return isHostWebSocketOpened() && closeHostWebSocket_();
    }

    private boolean closeHostWebSocket_() {
        boolean ret;

        try {
            if (webSocketClient != null && !webSocketClient.isClosed())
                // webSocketClient.closeBlocking();
                webSocketClient.close();

            ret = webSocketClient == null || webSocketClient.isClosed();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());

            ret = false;
        } finally {
            webSocketClient = null;
        }

        return ret;
    }

    public boolean writeHostWebSocket(String data) {
        if (!isHostWebSocketOpened())
            return false;

        webSocketClient.send(data);

        return true;
    }

    public String readHostWebSocket(int timeout) {
        // TODO: Implement blocked reading
        return null;
    }

    public boolean isHostWebSocketOpened() {
        return webSocketClient != null && webSocketClient.isOpen();
    }

    private boolean isInternetConnected() {
        if (connectivityManager == null)
            return false;

        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();

        return networkInfo != null && networkInfo.isConnected();
    }

    private boolean isImageDownloading() {
        return imageDownload != null &&
                imageDownload.getStatus() == AsyncTask.Status.RUNNING;
    }

    private boolean startImageDownload(String url) {
        if (isImageDownloading())
            return false;

        imageDownload = null;
        imageDownload = new ImageDownload(hostInfo, serviceCallbacks,
                serviceCallbackMutex);

        return imageDownload.execute(url) == imageDownload;
    }

    private boolean stopImageDownload() {
        if (!isImageDownloading())
            return false;

        return imageDownload.cancel(true);
    }

    private static class ImageDownload extends AsyncTask<String, Void, Bitmap[]> {
        private final HostInfo hostInfo;
        private final List<ServiceCallback> serviceCallbacks;
        private final Object serviceCallbackMutex;

        public ImageDownload(HostInfo hostInfo, List<ServiceCallback> serviceCallbacks,
                             Object serviceCallbackMutex) {
            this.hostInfo = hostInfo;

            if (serviceCallbacks == null)
                this.serviceCallbacks = new ArrayList<>();
            else
                this.serviceCallbacks = serviceCallbacks;

            this.serviceCallbackMutex = serviceCallbackMutex;
        }

        @Override
        protected Bitmap[] doInBackground(String... urls) {
            Bitmap[] bitmaps = new Bitmap[urls.length];

            for (int i = 0; i < urls.length; i++) {
                try {
                    InputStream in = new URL(urls[i]).openStream();
                    bitmaps[i] = BitmapFactory.decodeStream(in);
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage());
                }
            }

            return bitmaps;
        }

        @Override
        protected void onPostExecute(Bitmap[] bitmaps) {
            if (bitmaps == null)
                return;

            for (Bitmap bitmap : bitmaps) {
                if (bitmap == null)
                    continue;

                synchronized (serviceCallbackMutex) {
                    for (ServiceCallback serviceCallback : serviceCallbacks) {
                        if (serviceCallback == null)
                            continue;

                        serviceCallback.onImageReceived(hostInfo, bitmap);
                    }
                }
            }
        }
    }
}
