package hk.idv.elton.cuwaveautologin;

import android.annotation.TargetApi;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.Toast;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class LoginRoute extends IntentService {

    private static final String TAG = "LoginRoute";
    private static final int LOGIN_ERROR_ID = 1;
    private static final int LOGIN_ONGOING_ID = 2;

    public static final String EXTRA_LOGOUT = "logout";
    public static final String EXTRA_USER_TRIGGERED = "user_triggered";

    private Handler mHandler;
    private SharedPreferences mPrefs;
    private NotificationManager mNotifMan;
    private Notification mNotification;
    private Network mNetwork;

    public LoginRoute() {
        super(TAG);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Utils.loadLocale(this);

        mHandler = new Handler();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mNotifMan = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        mNotification = new Notification(R.drawable.ic_stat_notify_key, null, System.currentTimeMillis());
        updateOngoingNotification(getString(R.string.notify_request_wifi_ongoing_text), false); // Foreground service requires a valid notification
        startForeground(LOGIN_ONGOING_ID, mNotification); // Stopped automatically when onHandleIntent returns
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        mNotifMan.cancel(LOGIN_ERROR_ID); // clear any old notification

        boolean isLogout = intent.getBooleanExtra(EXTRA_LOGOUT, false);
        boolean isUserTriggered = intent.getBooleanExtra(EXTRA_USER_TRIGGERED, false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // For Lollipop+, we need to request the Wi-Fi network since
            // connections will go over mobile data by default if a captive
            // portal is detected
            Log.v(TAG, "Requesting Wi-Fi network");

            String[] SSID = NetworkStateChanged.SSID;

            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo wifiInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

            if (!wifiInfo.isConnected()) {
                Log.d(TAG, "Trying to connect Wi-Fi");
                for (int i = 0; i < SSID.length; i++) {
                    if (connectToNetwork(SSID[i])) {
                        break;
                    }
                }
            }

            if (!requestNetwork()) {
                Log.e(TAG, "Unable to request Wi-Fi network");
                createRetryNotification(isLogout, null, getString(R.string.notify_request_wifi_error_text));
                return;
            }
        }

        doLogin(isLogout, isUserTriggered);
    }

    private boolean connectToNetwork(String ssid) {

        Log.v(TAG, "Entered connectToNetwork");

        WifiConfiguration conf = new WifiConfiguration();
        conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiManager.startScan();
        List<ScanResult> scanned = wifiManager.getScanResults();

        wifiManager.addNetwork(conf);
        List<WifiConfiguration> list = wifiManager.getConfiguredNetworks();

        for (WifiConfiguration i : list) {
            String[] SSID = NetworkStateChanged.SSID;
            if (Arrays.asList(SSID).contains(i)) {
                Log.d(TAG, "Detected specified SSID = " + i.SSID);
            }

            if (scanned.contains(i) && i.SSID != null && i.SSID.equals("\"" + ssid + "\"")) {
                Log.d(TAG, "Connecting to " + ssid);
                wifiManager.disconnect();
                wifiManager.enableNetwork(i.networkId, true);
                boolean success = wifiManager.reconnect();
                if (success) {
                    Log.d(TAG, "Connected to " + ssid);
                    return true;
                } else {
                    wifiManager.reassociate();
                }
            }
        }
        return false;
    }

    private boolean requestNetwork() {

        Log.v(TAG, "Entered requestNetwork");

        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            for (Network net : cm.getAllNetworks()) {
                if (cm.getNetworkInfo(net).getType() == ConnectivityManager.TYPE_WIFI) {
                    Log.d(TAG, "Set network to " + net);
                    mNetwork = net;
                    ConnectivityManager.setProcessDefaultNetwork(net);

                    String[] SSID = NetworkStateChanged.SSID;

                    NetworkInfo wifiInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

                    if (!wifiInfo.isConnected()) {
                        Log.d(TAG, "Trying to connect Wi-Fi");

                        for (int i = 0; i < SSID.length; i++) {
                            if (connectToNetwork(SSID[i])) {
                                break;
                            }
                        }
                    } else {
                        Log.v(TAG, "Usable Wi-Fi is connected");
                    }
                    return true;
                }
            }
        } else {
            for (NetworkInfo net : cm.getAllNetworkInfo()) {
                if (net.getType() == ConnectivityManager.TYPE_WIFI) {
                    Log.d(TAG, "Set network to " + net);
                    cm.setNetworkPreference(ConnectivityManager.TYPE_WIFI);
                    return true;
                }
            }
        }
        /*
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            for (Network net : cm.getAllNetworks()) {
                if (cm.getNetworkInfo(net).getType() == ConnectivityManager.TYPE_WIFI) {
                    Log.d(TAG, "Set network to " + net);
                    mNetwork = net;
                    ConnectivityManager.setProcessDefaultNetwork(net);
                    return true;
                }
            }
        } else {
            if (cm != null) {
                NetworkInfo[] info = cm.getAllNetworkInfo();
                if (info != null) {
                    for (NetworkInfo net : info) {
                        if (net.getType() == ConnectivityManager.TYPE_WIFI) {
                            Log.d(TAG, "Set network to " + net);
                            cm.setNetworkPreference(ConnectivityManager.TYPE_WIFI);
                            return true;
                        }
                    }
                }
            }
        }
        */
        return false;
    }

    /**
     * Report successful to Lollipop's captive portal detector
     *
     * See CaptivePortalLoginActivity in frameworks/base
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void reportStateChange() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        // We're reporting "good" network. This function forces Android to
        // re-evaluate the network (and realize it's no longer a captive portal).
        cm.reportBadNetwork(mNetwork);
    }

    private void doLogin(boolean isLogout, boolean isUserTriggered) {
        try {
            if (isLogout) {
                Log.v(TAG, "Logging out");
                updateOngoingNotification(getString(R.string.notify_logout_ongoing_text), true);

                LoginClient loginClient = getLogoutClient();
                loginClient.logout();

                createToastNotification(R.string.logout_successful, Toast.LENGTH_SHORT);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    // Report logout successful so Android stops using this network
                    // (or at least that should happen, but 5.0.0_r2 doesn't seem to
                    // automatically switch to cellular)
                    reportStateChange();
                }

                Log.v(TAG, "Logout successful");
            } else {
                updateOngoingNotification(getString(R.string.notify_login_ongoing_text_determine_requirement), true);

                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());

                LoginClient loginClient = getLoginClient();
                String connectionName = null;

                if (loginClient != null) {
                    Log.v(TAG, "Login required");

                    if (!isUserTriggered && !loginClient.allowAuto()) {
                        // Require user to click login for insecure hotspots
                        createRetryNotification(isLogout,
                                String.format(getString(R.string.notify_request_wifi_connected_title), wm.getConnectionInfo().getSSID()),
                                getString(R.string.notify_request_wifi_connected_text));
                        return;
                    }

                    String username = null;
                    String password = null;

                    if (ip.startsWith("192.168.2")) {
                        // ERGWAVE
                        Log.v(TAG, "ERGWAVE");
                        username = mPrefs.getString(Preferences.KEY_USERNAME, null);
                        password = mPrefs.getString(Preferences.KEY_PASSWORD, null);
                    } else if (ip.startsWith("10.")) {
                        // CUHK WiFi
                        Log.v(TAG, "CUHK WiFi");
                        username = mPrefs.getString(Preferences.KEY_COMPUTINGID, null);
                        password = mPrefs.getString(Preferences.KEY_CWEM_PASSWORD, null);
                    }

                    String fqdn = mPrefs.getString(Preferences.KEY_FQDN, null);
                    String cmd = "authenticate";
                    String login = "Log In";

                    updateOngoingNotification(getString(R.string.notify_login_ongoing_text_logging_in), true);
                    loginClient.login(username, password, fqdn, cmd, login);

                    if (mPrefs.getBoolean(Preferences.KEY_TOAST_NOTIFY_SUCCESS, true)) {
                        connectionName = wm.getConnectionInfo().getSSID();
                        createToastNotificationWithString("Connected to " + connectionName + ", " + getString(R.string.login_successful), Toast.LENGTH_SHORT);
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        // Report login successful so Android starts using this network
                        reportStateChange();
                    }

                    Log.v(TAG, "Login successful");
                } else {
                    if (mPrefs.getBoolean(Preferences.KEY_TOAST_NOTIFY_NOT_REQUIRED, true)) {
                        connectionName = wm.getConnectionInfo().getSSID();
                        createToastNotificationWithString("Connected to " + connectionName + ", " + getString(R.string.no_login_required), Toast.LENGTH_SHORT);
                    }

                    Log.v(TAG, "No login required");
                }
            }
        } catch (LoginException e) {
            Log.e(TAG, "Login failed: LoginException", e);

            createRetryNotification(isLogout, null, e.getMessage());
        } catch (IOException e) {
            Log.e(TAG, "Login failed: IOException", e);

            tryConnection(isLogout);
        } catch (NullPointerException e) {
            // a bug in HttpClient library
            // thrown when there is a connection failure when handling a redirect
            Log.e(TAG, "Login failed: NullPointerException", e);

            tryConnection(isLogout);
        }
    }

    // After a login "failure", we can check if our connection is working or not
    private void tryConnection(boolean isLogout) {
        Log.v(TAG, "Trying connection after failure");
        try {
            boolean loggedIn = getLoginClient() == null;
            if ((!loggedIn && !isLogout) || (loggedIn && isLogout)) {
                createRetryNotification(isLogout);
            } else {
                Log.v(TAG, "Connection working");
            }
        } catch (Exception e) {
            createRetryNotification(isLogout);
        }
    }

    private void updateOngoingNotification(String message, boolean notify) {
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(), 0);
        //mNotification.setLatestEventInfo(this, getString(R.string.notify_login_ongoing_title), message, contentIntent);

        mNotification = new NotificationCompat.Builder(this)
                .setContentTitle(getString(R.string.notify_login_ongoing_title))
                .setContentText(message)
                .setContentIntent(contentIntent)
                .setSmallIcon(R.drawable.ic_stat_notify_key)
                .build();
    }

    private void createRetryNotification(boolean isLogout, String title, String text) {
        Intent notificationIntent = new Intent(this, LoginRoute.class);
        notificationIntent.putExtra(EXTRA_LOGOUT, isLogout);
        notificationIntent.putExtra(EXTRA_USER_TRIGGERED, true);
        PendingIntent contentIntent = PendingIntent.getService(this, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        if (title == null) {
            title = getString(isLogout ? R.string.notify_logout_error_title : R.string.notify_login_error_title);
        }

        createErrorNotification(contentIntent, title, text, isLogout);
    }

    private void createRetryNotification(boolean isLogout) {
        createRetryNotification(isLogout, null, getString(R.string.notify_login_error_text));
    }

    private void createErrorNotification(PendingIntent contentIntent, String title, String errorText, boolean isLogout) {
        Log.d(TAG, "createErrorNotification isLogout=" + isLogout);

        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (!wifi.isWifiEnabled()) {
            // Don't show errors if wifi is disabled
            return;
        }

        //Notification notification = new Notification(R.drawable.ic_stat_notify_key, title, System.currentTimeMillis());
        //notification.setLatestEventInfo(this, title, errorText, contentIntent);

        // Use new API
        Notification notification = new NotificationCompat.Builder(this)
                .setContentTitle(title)
                .setContentText(errorText)
                .setContentIntent(contentIntent)
                .setSmallIcon(R.drawable.ic_stat_notify_key)
                .build();

        //notification.flags = Notification.FLAG_AUTO_CANCEL;

        if (mPrefs.getBoolean(Preferences.KEY_ERROR_NOTIFY_SOUND, false)) {
            notification.defaults |= Notification.DEFAULT_SOUND;
        }
        if (mPrefs.getBoolean(Preferences.KEY_ERROR_NOTIFY_VIBRATE, false)) {
            notification.defaults |= Notification.DEFAULT_VIBRATE;
        }
        if (mPrefs.getBoolean(Preferences.KEY_ERROR_NOTIFY_LIGHTS, false)) {
            notification.defaults |= Notification.DEFAULT_LIGHTS;
        }

        mNotifMan.notify(LOGIN_ERROR_ID, notification);
    }

    private void createToastNotification(final int message, final int length) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(LoginRoute.this, message, length).show();
            }
        });
    }

    private void createToastNotificationWithString(final String message, final int length) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(LoginRoute.this, message, length).show();
            }
        });
    }

    private LoginClient getLoginClient() throws IOException, LoginException {
        HttpGet httpget = new HttpGet("http://client3.google.com/generate_204");
        HttpResponse response = Utils.createHttpClient(true, null).execute(httpget);

        Log.d(TAG, "Statuscode = " + response.getStatusLine().getStatusCode());

        if (response.getStatusLine().getStatusCode() == 204) {
            // We're online! No login required
            return null;
        }

        //String strRes = EntityUtils.toString(response.getEntity());
        //Log.d(TAG, strRes);

        return getLogoutClient();
    }

    private LoginClient getLogoutClient() throws IOException, LoginException {
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
        Log.d(TAG, "IP :" + ip);
        if (ip.startsWith("192.168.2")) {
            // Assume ERGWAVE
            Log.v(TAG, "ERGWAVE network");
            return new ERGWAVEClient(this);
        } else if (ip.startsWith("10.")) {
            // Wifi IP
            Log.v(TAG, "WiFi network");
            return new CUHKWifiClient(this);
        }

        return null;
    }
}
