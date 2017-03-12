package hk.idv.elton.cuwaveautologin;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.widget.BaseAdapter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class Preferences extends PreferenceActivity implements OnSharedPreferenceChangeListener, ActivityCompat.OnRequestPermissionsResultCallback {
    public static final String KEY_LOGIN_NOW = "login_now";
    public static final String KEY_LOGOUT_NOW = "logout_now";
    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_COMPUTINGID = "computingid";
    public static final String KEY_CWEM_PASSWORD = "cwempassword";
    public static final String KEY_USERNAME = "username";
    public static final String KEY_PASSWORD = "password";
    public static final String KEY_FQDN = "fqdn";
    public static final String KEY_ERROR_NOTIFY = "error_notify";
    public static final String KEY_ERROR_NOTIFY_SOUND = "error_notify_sound";
    public static final String KEY_ERROR_NOTIFY_VIBRATE = "error_notify_vibrate";
    public static final String KEY_ERROR_NOTIFY_LIGHTS = "error_notify_lights";
    public static final String KEY_TOAST_NOTIFY = "toast_notify";
    public static final String KEY_TOAST_NOTIFY_SUCCESS = "toast_notify_success";
    public static final String KEY_TOAST_NOTIFY_NOT_REQUIRED = "toast_notify_not_required";
    public static final String KEY_LANGUAGE = "language";
    public static final String KEY_VERSION = "version";
    public static final String KEY_WEBSITE = "website";
    public static final String KEY_AUTHOR = "author";

    public static final String FQDN_DEFAULT = "";
    public static final String LANGUAGE_DEFAULT = "default";
    public static final String MARKET_PREFIX = "market://details?id=";
    public static final String EMAIL_TYPE = "message/rfc822";
    public static final String EMAIL_AUTHOR = "cuwave-autologin@elton.tk";
    public static final String EMAIL_SUBJECT = "[CUWave Autologin] Debug Log";
    public static final String GITHUB_URL = "https://github.com/quakelton/CUWave-autologin";
    public static final String WEBSITE_URL = "https://elton.tk/cuwave";

    public static final int MY_PERMISSION_ACCESS_COARSE_LOCATION = 11;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[] {
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                }, MY_PERMISSION_ACCESS_COARSE_LOCATION);
            }
        }

        Utils.loadLocale(this);
        addPreferencesFromResource(R.xml.preferences);

        updateComputingIdSummary();
        updateUsernameSummary();
        updateFQDNSummary();
        updateErrorNotificationSummary();
        updateToastNotificationSummary();
        updateLanguageSummary();

        // Set version number
        String versionSummary = String.format(getString(R.string.pref_version_summary), Utils.getVersionName(this));
        findPreference(KEY_VERSION).setSummary(versionSummary);

        // Login now callback
        findPreference(KEY_LOGIN_NOW).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Utils.checkWifiAndDoLogin(Preferences.this, false);
                return true;
            }
        });

        // Logout now callback
        findPreference(KEY_LOGOUT_NOW).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Utils.checkWifiAndDoLogin(Preferences.this, true);
                return true;
            }
        });

        // Version (visit android market) callback
        findPreference(KEY_VERSION).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                //Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(MARKET_PREFIX + getPackageName()));
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL));
                startActivity(i);
                return true;
            }
        });

        // Visit website callback
        findPreference(KEY_WEBSITE).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(WEBSITE_URL));
                startActivity(i);
                return true;
            }
        });

        // Contact author callback
        findPreference(KEY_AUTHOR).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType(EMAIL_TYPE);
                i.putExtra(Intent.EXTRA_EMAIL, new String[] {EMAIL_AUTHOR});
                i.putExtra(Intent.EXTRA_SUBJECT, EMAIL_SUBJECT);
                i.putExtra(Intent.EXTRA_TEXT, "\n\n----\nVersion: " + Utils.getVersionName(Preferences.this)
                        + "\nLogs:\n" + Utils.getLogCat());

                startActivity(Intent.createChooser(i, ""));
                return true;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Callback to update dynamic summaries when they get changed
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
        if (key.equals(KEY_ENABLED)) {
            updateEnabled();
        }
        else if (key.equals(KEY_COMPUTINGID)) {
            updateComputingIdSummary();
        }
        else if (key.equals(KEY_USERNAME)) {
            updateUsernameSummary();
        }
        else if (key.equals(KEY_FQDN)) {
            updateFQDNSummary();
        }
        else if (key.equals(KEY_ERROR_NOTIFY_SOUND)
                || key.equals(KEY_ERROR_NOTIFY_VIBRATE)
                || key.equals(KEY_ERROR_NOTIFY_LIGHTS)) {
            updateErrorNotificationSummary();
            ((BaseAdapter) getPreferenceScreen().getRootAdapter()).notifyDataSetChanged(); // force update parent screen
        }
        else if (key.equals(KEY_TOAST_NOTIFY_SUCCESS)
                || key.equals(KEY_TOAST_NOTIFY_NOT_REQUIRED)) {
            updateToastNotificationSummary();
            ((BaseAdapter) getPreferenceScreen().getRootAdapter()).notifyDataSetChanged(); // force update parent screen
        }
        else if (key.equals(KEY_LANGUAGE)) {
            updateLanguageSummary();

            // WARNING: restarting the activity, don't do anything after this
            Intent intent = getIntent();
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            finish();
            overridePendingTransition(0, 0);
            startActivity(intent);
            overridePendingTransition(0, 0);
        }
    }

    // Enable / disable the BroadcastReceiver
    private void updateEnabled() {
        boolean enabled = getPreferenceManager().getSharedPreferences().getBoolean(KEY_ENABLED, false);
        Utils.setEnableBroadcastReceiver(this, enabled);
    }

    private void updateComputingIdSummary() {
        // Set computingId as summary if set
        String computingId = getPreferenceManager().getSharedPreferences().getString(KEY_COMPUTINGID, "");
        if (computingId.length() != 0) {
            findPreference(KEY_COMPUTINGID).setSummary(computingId);
        } else {
            findPreference(KEY_COMPUTINGID).setSummary(R.string.pref_cwem_username_summary);
        }
    }

    private void updateUsernameSummary() {
        // Set username as summary if set
        String username = getPreferenceManager().getSharedPreferences().getString(KEY_USERNAME, "");
        if (username.length() != 0) {
            findPreference(KEY_USERNAME).setSummary(username);
        } else {
            findPreference(KEY_USERNAME).setSummary(R.string.pref_username_summary);
        }
    }

    private void updateFQDNSummary() {
        ListPreference listPref = (ListPreference) findPreference(KEY_FQDN);
        listPref.setSummary(listPref.getEntry());
    }

    private void updateErrorNotificationSummary() {
        ArrayList<String> methods = new ArrayList<String>();

        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();

        if (prefs.getBoolean(Preferences.KEY_ERROR_NOTIFY_SOUND, false)) {
            methods.add(getString(R.string.pref_error_notify_sound));
        }
        if (prefs.getBoolean(Preferences.KEY_ERROR_NOTIFY_VIBRATE, false)) {
            methods.add(getString(R.string.pref_error_notify_vibrate));
        }
        if (prefs.getBoolean(Preferences.KEY_ERROR_NOTIFY_LIGHTS, false)) {
            methods.add(getString(R.string.pref_error_notify_lights));
        }

        if (methods.size() == 0) {
            findPreference(KEY_ERROR_NOTIFY).setSummary(R.string.pref_error_notify_none);
        }
        else {
            String summaryStr = join(methods, getString(R.string.pref_error_notify_deliminator));
            findPreference(KEY_ERROR_NOTIFY).setSummary(summaryStr);
        }
    }

    private void updateToastNotificationSummary() {
        ArrayList<String> methods = new ArrayList<String>();

        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();

        if (prefs.getBoolean(KEY_TOAST_NOTIFY_SUCCESS, true)) {
            methods.add(getString(R.string.pref_toast_notify_success));
        }
        if (prefs.getBoolean(KEY_TOAST_NOTIFY_NOT_REQUIRED, true)) {
            methods.add(getString(R.string.pref_toast_notify_not_required));
        }

        if (methods.size() == 0) {
            findPreference(KEY_TOAST_NOTIFY).setSummary(R.string.pref_error_notify_none);
        }
        else {
            String summaryStr = join(methods, getString(R.string.pref_error_notify_deliminator));
            findPreference(KEY_TOAST_NOTIFY).setSummary(summaryStr);
        }
    }

    private void updateLanguageSummary() {
        ListPreference listPref = (ListPreference) findPreference(KEY_LANGUAGE);
        listPref.setSummary(listPref.getEntry());
    }

    private static String join(Collection<String> col, String deliminator) {
        StringBuilder sb = new StringBuilder();
        Iterator<String> iter = col.iterator();

        sb.append(iter.next());
        while (iter.hasNext()) {
            sb.append(deliminator);
            sb.append(iter.next());
        }
        return sb.toString();
    }
}