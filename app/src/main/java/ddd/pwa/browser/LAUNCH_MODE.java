package ddd.pwa.browser;

public enum LAUNCH_MODE {
    GET_URL_DETAIL(0),
    SHOW_URL_PAGE(1);

    private final int intValue;

    LAUNCH_MODE(int intValue) {
        this.intValue = intValue;
    }

    public int getIntValue() {
        return intValue;
    }
}
