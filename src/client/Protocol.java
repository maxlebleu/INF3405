package client;

public final class Protocol {

    private Protocol() {
    }

    public static final String AUTH = "AUTH";

    public static final String AUTH_OK = "AUTH_OK";

    public static final String AUTH_FAIL = "AUTH_FAIL";

    public static final String MESSAGE = "MSG";

    public static final String CHAT = "CHAT";

    public static final String QUIT = "QUIT";

    public static final String ERROR = "ERROR";
}
