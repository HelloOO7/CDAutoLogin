package cz.mamstylcendy.cdautologin;

public enum CDCaptiveType {
    NONE,
    UNKNOWN,
    CDWIFI_BASIC,
    CDWIFI_PASSENGERA;

    public boolean isCdWifi() {
        return this == CDWIFI_PASSENGERA || this == CDWIFI_BASIC;
    }
}
