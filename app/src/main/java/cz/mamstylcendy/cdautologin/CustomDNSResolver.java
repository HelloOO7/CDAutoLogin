package cz.mamstylcendy.cdautologin;

import androidx.annotation.NonNull;

import org.xbill.DNS.ARecord;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TextParseException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import okhttp3.Dns;

public class CustomDNSResolver implements Dns {

    private static final Pattern IP4_PATTERN = Pattern.compile("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$");
    private static final Pattern IP6_PATTERN = Pattern.compile("^[0-9a-fA-F:]+$");

    private final SimpleResolver mResolver;

    public CustomDNSResolver() {
        try {
            mResolver = new SimpleResolver();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    public void setNameserver(InetAddress address) {
        mResolver.setAddress(address);
    }

    private boolean isIPAddress(String hostname) {
        return IP4_PATTERN.matcher(hostname).matches() || IP6_PATTERN.matcher(hostname).matches();
    }

    @NonNull
    @Override
    public List<InetAddress> lookup(@NonNull String hostname) throws UnknownHostException {
        if (isIPAddress(hostname)) {
            return List.of(InetAddress.getAllByName(hostname));
        }

        Lookup lookup;
        try {
            lookup = new Lookup(hostname);
        } catch (TextParseException e) {
            throw new RuntimeException(e);
        }
        lookup.setResolver(mResolver);
        Record[] results = lookup.run();
        if (results == null) {
            throw new UnknownHostException("No results for " + hostname);
        }
        List<InetAddress> addresses = new ArrayList<>();
        for (Record rec : results) {
            if (rec instanceof ARecord) {
                ARecord a = (ARecord) rec;
                addresses.add(a.getAddress());
            }
        }
        if (addresses.isEmpty()) {
            throw new UnknownHostException("No A records for " + hostname);
        }
        return addresses;
    }
}
