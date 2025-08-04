package cz.mamstylcendy.cdautologin;

public class CaptivePortalInfo {
    public final CDCaptiveType type;
    public final String homeUrl;

    public CaptivePortalInfo(CDCaptiveType type, String homeUrl) {
        this.type = type;
        this.homeUrl = homeUrl;
    }

    public static CaptivePortalInfo notCaptive() {
        return new CaptivePortalInfo(CDCaptiveType.NONE, null);
    }

    public static CaptivePortalInfo failure() {
        return null;
    }
}
