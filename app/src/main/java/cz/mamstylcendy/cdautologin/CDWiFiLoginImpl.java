package cz.mamstylcendy.cdautologin;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Objects;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;

public class CDWiFiLoginImpl {

    private static final String TAG = CDWiFiLoginImpl.class.getSimpleName();

    private static final String CONNECTIVITY_TEST_URL = "http://connectivitycheck.gstatic.com/generate_204";

    private final Context mContext;
    /*
    What this is even for:
    On devices which have private DNS enabled (most notably for ad blocking), the DNS resolver
    will be unreachable until the captive portal lets us through. When Android does its own
    captive portal flow, it uses a "bypass" network which ignores the private DNS setting until
    a resolution has been reached. However, as this is a privileged system API with additional
    signature/privileged permissions required for it to actually take effect, we need to
    perform name resolution entirely on our own.
     */
    private final CustomDNSResolver mDNS;
    private final OkHttpClient mClient;
    private final OkHttpClient mClientNoRedirect;

    public CDWiFiLoginImpl(Context context) {
        mContext = context;
        mDNS = new CustomDNSResolver();
        HttpLoggingInterceptor logger = new HttpLoggingInterceptor();
        if (BuildConfig.DEBUG) {
            logger.setLevel(HttpLoggingInterceptor.Level.BODY);
        }
        mClient = new OkHttpClient.Builder()
                .dns(mDNS)
                .addNetworkInterceptor(logger)
                .build();
        mClientNoRedirect = mClient
                .newBuilder()
                .followRedirects(false)
                .build();
    }

    private void debugLog(String text) {
        Log.d(TAG, text);
    }

    private void updateDNS() {
        List<InetAddress> nameservers = ConnectivityManagerCompat.getDNSServers(mContext);
        if (nameservers != null && !nameservers.isEmpty()) {
            mDNS.setNameserver(nameservers.get(0));
        } else {
            mDNS.setNameserver(ConnectivityManagerCompat.getDefaultGateway(mContext));
        }
    }

    public CaptivePortalInfo detectCaptivePortal() throws IOException {
        if (!ConnectivityManagerCompat.isConnectedToWifi(mContext)) {
            return CaptivePortalInfo.notCaptive();
        }
        updateDNS();

        String captiveUrl;

        try (Response response = sendRequestNoRedirect(newBasicHttpRequest(CONNECTIVITY_TEST_URL))) {
            if (response.code() == 204) {
                return CaptivePortalInfo.notCaptive();
            }

            String location = response.header("Location");

            if (location == null || !isCdWifiUrl(location)) {
                debugLog("Not CDWiFi location: " + location);
                return new CaptivePortalInfo(CDCaptiveType.UNKNOWN, location);
            }

            captiveUrl = location;
        }

        try (Response response = sendRequest(newBasicHttpRequest(captiveUrl))) {
            if (checkIsCdWifiSimpleLoginPage(response)) {
                return new CaptivePortalInfo(CDCaptiveType.CDWIFI_BASIC, captiveUrl);
            } else {
                return new CaptivePortalInfo(CDCaptiveType.CDWIFI_PASSENGERA, captiveUrl);
            }
        }
    }

    private boolean isCdWifiUrl(String location) {
        return location.contains("cdwifi.cz");
    }

    private Request newBasicHttpRequest(String url) {
        return newBasicHttpRequest(HttpUrl.get(url));
    }

    private Request newBasicHttpRequest(HttpUrl url) {
        return new Request.Builder()
                .url(url)
                .build();
    }

    private Response sendRequest(OkHttpClient client, Request request) throws IOException {
        return client.newCall(request).execute();
    }

    private Response sendRequest(Request request) throws IOException {
        return sendRequest(mClient, request);
    }

    private Response sendRequestNoRedirect(Request request) throws IOException {
        return sendRequest(mClientNoRedirect, request);
    }

    private Pair<String, String> getLegacyLoginFormInfo(ResponseBody body) throws IOException {
        if (body == null) {
            throw new IOException("Empty response body");
        }
        String gatewayWebpage = body.string();
        Document doc = Jsoup.parse(gatewayWebpage);
        Element secretEl = doc.selectFirst("input[name=\"secret\"]");
        if (secretEl != null) {
            Element form = secretEl.closest("form");
            if (form != null) {
                return new Pair<>(form.attr("action"), secretEl.val());
            }
        }
        return null;
    }

    private boolean checkIsCdWifiSimpleLoginPage(Response response) throws IOException {
        try (ResponseBody body = response.body()) {
            return getLegacyLoginFormInfo(body) != null;
        }
    }

    private Request newLegacyLoginPostRequest(CaptivePortalInfo info, String action, String secret) {
        HttpUrl url = Objects.requireNonNull(HttpUrl.get(info.homeUrl).resolve(action));

        return new Request.Builder()
                .url(url)
                .post(new FormBody.Builder()
                        .add("secret", secret)
                        .add("eula", "on")
                        .build())
                .build();
    }

    private boolean cdWifiLoginLegacy(CaptivePortalInfo info) throws IOException {
        updateDNS();
        Pair<String, String> formInfo;

        try (Response gatewayResponse = sendRequest(newBasicHttpRequest(info.homeUrl))) {
            if (!gatewayResponse.isSuccessful()) {
                debugLog("Failed to fetch gateway page!");
                return false;
            }

            try (ResponseBody body = gatewayResponse.body()) {
                formInfo = getLegacyLoginFormInfo(body);
            }

            if (formInfo == null) {
                debugLog("Could not get secret from CDW gateway!");
                return false;
            }

            debugLog("CDWifi legacy form action=" + formInfo.first + ", secret=" + formInfo.second);
        }

        try (Response loginResponse = sendRequest(newLegacyLoginPostRequest(info, formInfo.first, formInfo.second))) {
            if (!loginResponse.isSuccessful()) {
                debugLog("Failed POST request to " + formInfo.first + "!");
                return false;
            }

            if (checkIsCdWifiSimpleLoginPage(loginResponse)) {
                debugLog("Login failed!");
                return false;
            }
        }

        return true;
    }

    private Request newPassengeraLoginGetRequest(String successLocation, String failureLocation) {
        return newBasicHttpRequest(
                HttpUrl.get("http://cdwifi.cz/portal/api/vehicle/gateway/user/authenticate?ahoj=ceskedrahy")
                        .newBuilder()
                        .addQueryParameter("category", "internet")
                        .addQueryParameter("url", successLocation)
                        .addQueryParameter("onerror", failureLocation)
                        .build()
        );
    }

    private boolean cdWifiLoginPassengera(CaptivePortalInfo info) throws IOException {
        updateDNS();

        final String LOCATION_SUCCESS = "http://julka.je/nejkrasnejsi-na-svete";
        final String LOCATION_FAILURE = "http://mff.cuni.cz/";

        try (Response loginResp = sendRequestNoRedirect(newPassengeraLoginGetRequest(LOCATION_SUCCESS, LOCATION_FAILURE))) {
            if (loginResp.code() != 307) {
                debugLog("Unexpected response code: " + loginResp.code());
                return false;
            }

            String location = loginResp.header("Location");
            if (!LOCATION_SUCCESS.equals(location)) {
                debugLog("Bad location: " + location);
                return false;
            }
        }

        return true;
    }

    public boolean cdWifiLogin(CaptivePortalInfo captiveInfo) throws IOException {
        switch (captiveInfo.type) {
            case CDWIFI_PASSENGERA:
                return cdWifiLoginPassengera(captiveInfo);
            case CDWIFI_BASIC:
                return cdWifiLoginLegacy(captiveInfo);
            default:
                return false;
        }
    }
}
