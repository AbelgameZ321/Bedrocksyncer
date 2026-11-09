package dev.bedrocksync.auth;

/**
 * Thread-safe, in-memory sign-in state shown on the account screen.
 * Holds no tokens: only display strings (gamertag, XUID, status text).
 */
public final class BedrockSession {

    public enum State { SIGNED_OUT, PENDING, SIGNED_IN, ERROR }

    private State state = State.SIGNED_OUT;
    private String message = "Not signed in.";
    private String userCode = "";
    private String verificationUri = "";
    private String gamertag = "";
    private String xuid = "";

    public synchronized State state() { return state; }
    public synchronized String message() { return message; }
    public synchronized String userCode() { return userCode; }
    public synchronized String verificationUri() { return verificationUri; }
    public synchronized String gamertag() { return gamertag; }
    public synchronized String xuid() { return xuid; }

    public synchronized void setPending() {
        state = State.PENDING;
        message = "Requesting a sign-in code...";
        userCode = "";
        verificationUri = "";
    }

    public synchronized void setDeviceCode(String code, String uri) {
        userCode = code;
        verificationUri = uri;
        message = "Open the link on any device and enter the code.";
    }

    public synchronized void setSignedIn(String gamertag, String xuid) {
        this.state = State.SIGNED_IN;
        this.gamertag = gamertag;
        this.xuid = xuid;
        this.userCode = "";
        this.verificationUri = "";
        this.message = "Signed in with Microsoft / Xbox.";
    }

    public synchronized void fail(String reason) {
        state = State.ERROR;
        message = reason;
        userCode = "";
        verificationUri = "";
    }

    public synchronized void signOut() {
        state = State.SIGNED_OUT;
        message = "Signed out. Local session cleared.";
        userCode = "";
        verificationUri = "";
        gamertag = "";
        xuid = "";
    }
}
