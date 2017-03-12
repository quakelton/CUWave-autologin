package hk.idv.elton.cuwaveautologin;

import android.content.Context;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

// Client for MU-WiFi system running on Aruba Networks
public class ArubaClient implements LoginClient {

    private static final String TAG = "ArubaClient";

    // These are not regex
    private static final String LOGIN_SUCCESSFUL_PATTERN = "External Welcome Page";
    private static final String LOGOUT_SUCCESSFUL_PATTERN = "Logout Successful";

    private static final String FORM_USERNAME = "user";
    private static final String FORM_PASSWORD = "password";
    private static final String FORM_FQDN = "fqdn";
    private static final String FORM_CMD = "cmd";
    private static final String FORM_LOGIN = "Login";
    private static final String FORM_URL = "https://securelogin.arubanetworks.com/cgi-bin/login";
    private static final String LOGOUT_URL = "";

    private DefaultHttpClient mHttpClient;

    public ArubaClient(Context context) throws IOException {
        mHttpClient = Utils.createHttpClient(true, new byte[][] {
                Utils.getRawResource(context, R.raw.ergwave)
        });
    }

    public void login(String username, String password, String fqdn, String cmd, String login) throws IOException, LoginException {
        try {
            List<NameValuePair> formparams = new ArrayList<NameValuePair>();
            formparams.add(new BasicNameValuePair(FORM_USERNAME, username));
            formparams.add(new BasicNameValuePair(FORM_PASSWORD, password));
            formparams.add(new BasicNameValuePair(FORM_FQDN, fqdn));
            formparams.add(new BasicNameValuePair(FORM_CMD, cmd));
            formparams.add(new BasicNameValuePair(FORM_LOGIN, login));
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(formparams, "UTF-8");
            HttpPost httppost = new HttpPost(FORM_URL);
            httppost.setEntity(entity);
            HttpResponse response = mHttpClient.execute(httppost);
            String strRes = EntityUtils.toString(response.getEntity());

            Log.d(TAG, strRes);

            if (strRes.contains(LOGIN_SUCCESSFUL_PATTERN)) {
                // login successful
            } else {
                throw new LoginException("Unexpected reply from server.");
            }
        } catch (ClientProtocolException e) {
            // If there is an error, the server will send an illegal reply (containing space)
            if (e.getCause() == null || e.getCause().getCause() == null ||
                    !(e.getCause().getCause() instanceof URISyntaxException)) {
                throw new LoginException("Unknown error.");
            }
            URISyntaxException orig = (URISyntaxException) e.getCause().getCause();
            String url = orig.getInput();
            String message = url.substring(url.indexOf("errmsg=") + "errmsg=".length());
            throw new LoginException(message);
        }
    }

    public void logout() throws IOException, LoginException {
        //HttpGet httpget = new HttpGet(LOGOUT_URL);
        //HttpResponse response = mHttpClient.execute(httpget);
        //String strRes = EntityUtils.toString(response.getEntity());

        /*
        if (strRes.contains(LOGOUT_SUCCESSFUL_PATTERN)) {
            // logout successful
        } else {
            // TODO
            throw new LoginException("Unexpected reply from server.");
        }
        */
        throw new LoginException("Server does not support logout.");
    }

    @Override
    public boolean allowAuto() {
        return true;
    }

}
