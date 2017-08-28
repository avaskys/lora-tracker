package com.vaskys.tracker;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.concurrent.LinkedBlockingQueue;

public class LocationWatcherService extends Service {

    private DatagramSocket sock = null;
    private LinkedBlockingQueue<byte[]> sendQueue = new LinkedBlockingQueue<>();
    private FusedLocationProviderClient locationClient = null;
    private LocCallback locCb = new LocCallback();

    public static final String ACTION_START = "start";
    public static final String ACTION_STOP = "stop";

    public static final String POS_BROADCAST_ACTION = "pos_broadcast";

    @Override
    public void onCreate() {
        locationClient = LocationServices.getFusedLocationProviderClient(this);

        try {
            sock = new DatagramSocket();

            new Thread(new NetworkSendThread()).start();
            new Thread(new NetworkRecvThread()).start();
        } catch (IOException ioe) {
            logErr("Unable to create UDP socket: " + ioe.getMessage());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final int ONGOING_NOTIFICATION_ID = 1357;

        if (ACTION_START.equals(intent.getAction())) {

            Intent notificationIntent = new Intent(this, LocationWatcherService.class);
            notificationIntent.setAction(ACTION_STOP);
            PendingIntent pendingIntent = PendingIntent.getService(this, 0, notificationIntent, 0);

            Notification notification =
                    new Notification.Builder(this)
                            .setContentTitle("Burning Man Tracker")
                            .setContentText("Tracking location...")
                            .setContentIntent(pendingIntent)
                            .setSmallIcon(R.drawable.pointer_bubble_normal)
                            .setTicker("Ticker???")
                            .build();

            startForeground(ONGOING_NOTIFICATION_ID, notification);

            // Get all known locations
            sendQueue.add("{\"type\":\"getall\"}".getBytes());

            LocationRequest lr = new LocationRequest();
            lr.setInterval(60000);
            lr.setFastestInterval(30000);
            lr.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            locationClient.requestLocationUpdates(lr, locCb, null);
        }
        else if (ACTION_STOP.equals(intent.getAction())) {
            stopForeground(true);
            stopSelf();
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        locationClient.removeLocationUpdates(locCb);
        sendQueue.add(null);
        if (sock != null)
            sock.close();
        sock = null;
    }

    private void logErr(final String err) {
        Handler handler = new Handler(Looper.getMainLooper());

        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(LocationWatcherService.this, err, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private class NetworkSendThread implements Runnable {
        @Override
        public void run() {
            try {
                byte[] toSend;
                while ((toSend = sendQueue.take()) != null) {
                    try {
                        String ip = PreferenceManager.getDefaultSharedPreferences(LocationWatcherService.this).getString("ip", "192.168.0.254");
                        InetAddress addr = InetAddress.getByName(ip);

                        DatagramPacket dp = new DatagramPacket(toSend, toSend.length, addr, 5309);
                        sock.send(dp);
                    }
                    catch (IOException ioe) {
                        logErr("IO Error in NetworkSendThread: " + ioe.getMessage());
                        //stopSelf();
                    }
                }
            }
            catch (InterruptedException ie) {
               logErr("InterruptedException??");
            }
        }
    }

    private class NetworkRecvThread implements Runnable {
        @Override
        public void run() {
            try {
                while (true) {
                    byte[] buf = new byte[1024];
                    DatagramPacket dp = new DatagramPacket(buf, buf.length);
                    sock.receive(dp);

                    try {
                        JSONObject jo = new JSONObject(new String(dp.getData()));

                        Intent update = new Intent(POS_BROADCAST_ACTION);
                        update.putExtra("callsign", jo.getString("callsign"));
                        update.putExtra("lat", jo.getInt("lat"));
                        update.putExtra("long", jo.getInt("long"));
                        if (jo.has("age")) {
                            update.putExtra("age", jo.getInt("age"));
                        }
                        else {
                            update.putExtra("age", 0);
                        }

                        LocalBroadcastManager.getInstance(LocationWatcherService.this).sendBroadcast(update);
                    }
                    catch (JSONException je) {
                        logErr("Tracker: Unexpected network data received");
                    }
                }
            }
            catch (SocketException se) {
                // This is probably okay, we're just exiting
            }
            catch (Exception e) {
                logErr("Error in NetworkRecvThread: " + e.getMessage());
            }
        }
    }

    private class LocCallback extends LocationCallback {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            int lat = (int) (locationResult.getLastLocation().getLatitude() * 1000000);
            int lon = (int) (locationResult.getLastLocation().getLongitude() * 1000000);

            try {
                JSONObject jo = new JSONObject();
                jo.put("type", "posupdate");
                jo.put("callsign", PreferenceManager.getDefaultSharedPreferences(LocationWatcherService.this).getString("callsign", "NONE"));
                jo.put("lat", lat);
                jo.put("long", lon);
                jo.put("isaccurate", true); /* FIXME */

                sendQueue.add(jo.toString().getBytes());
            }
            catch (JSONException je) {
                logErr("Error marshalling json: " + je.getMessage());
            }
        }
    }
}
