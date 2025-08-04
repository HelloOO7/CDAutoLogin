package cz.mamstylcendy.cdautologin;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.RouteInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class ConnectivityManagerCompat {

    public static Network getBoundOrActiveNetwork(ConnectivityManager cm) {
        Network network = cm.getBoundNetworkForProcess();
        if (network != null) {
            return network;
        }
        return cm.getActiveNetwork();
    }

    public static InetAddress getDefaultGateway(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return getDefaultGatewayAPI23(context);
        } else {
            return getDefaultGatewayBelow23(context);
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private static InetAddress getDefaultGatewayAPI23(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return null;
        }
        LinkProperties linkProperties = cm.getLinkProperties(getBoundOrActiveNetwork(cm));
        if (linkProperties == null) {
            return null;
        }
        List<RouteInfo> routes = linkProperties.getRoutes();
        for (RouteInfo route : routes) {
            if (route.isDefaultRoute()) {
                return route.getGateway();
            }
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private static InetAddress getDefaultGatewayBelow23(Context context) {
        DhcpInfo dhcpInfo = getDHCPInfo(context);

        if (dhcpInfo == null) {
            return null;
        }

        return convertIPAddressInt(dhcpInfo.gateway);
    }

    public static List<InetAddress> getDNSServers(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return getDNSServersAPI23(context);
        } else {
            return getDNSServersBelow23(context);
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private static List<InetAddress> getDNSServersAPI23(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return null;
        }
        LinkProperties linkProperties = cm.getLinkProperties(getBoundOrActiveNetwork(cm));
        if (linkProperties == null) {
            return null;
        }
        return linkProperties.getDnsServers();
    }

    @SuppressWarnings("deprecation")
    private static List<InetAddress> getDNSServersBelow23(Context context) {
        DhcpInfo dhcpInfo = getDHCPInfo(context);

        if (dhcpInfo == null) {
            return null;
        }

        List<InetAddress> dnsServers = new ArrayList<>();
        if (dhcpInfo.dns1 != 0) {
            dnsServers.add(convertIPAddressInt(dhcpInfo.dns1));
        }
        if (dhcpInfo.dns2 != 0) {
            dnsServers.add(convertIPAddressInt(dhcpInfo.dns2));
        }
        return dnsServers;
    }

    @SuppressWarnings("deprecation")
    private static DhcpInfo getDHCPInfo(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            return null;
        }
        return wifiManager.getDhcpInfo();
    }

    private static InetAddress convertIPAddressInt(int address) {
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            bytes[i] = (byte) ((address >> (i * 8)) & 0xFF);
        }
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static boolean isConnectedToWifi(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return isConnectedToWifiAPI23(context);
        } else {
            return isConnectedToWifiBelow23(context);
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private static boolean isConnectedToWifiAPI23(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        Network network = getBoundOrActiveNetwork(cm);
        if (network == null) {
            return false;
        }
        NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
        if (capabilities == null) {
            return false;
        }
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    @SuppressWarnings("deprecation")
    private static boolean isConnectedToWifiBelow23(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        NetworkInfo networkInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return networkInfo != null && networkInfo.isConnected();
    }
}
