package dev.bedrocksync.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.bedrocksync.BedrockSyncClient;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Microsoft sign-in using the OAuth 2.0 device code flow, then Xbox Live
 * (XBL) and XSTS authorization to obtain the gamertag.
 *
 * - The user enters credentials only on microsoft.com, never in this mod.
 * - No refresh token is requested or stored; tokens are discarded after use.
 * - Only Xbox identity display data (gamertag, XUID) is kept.
 *
 * This does NOT obtain a Minecraft Java profile. Java online-mode servers
 * require a Java Edition entitlement, which this flow does not provide.
 */
public final class MicrosoftAuthService {

    static final String DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    static final String XBL_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate";
    static final String XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    static final String SCOPE = "XboxLive.signin";
    static final String DEVICE_GRANT = "urn:ietf:params:oauth:grant-type:device_code";

    private final String clientId;
    private final HttpClient http;
    private final ExecutorService executor;

    public MicrosoftAuthService(String clientId) {
        this.clientId = clientId == null ? "" : clientId;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "BedrockSync-Auth");
            t.setDaemon(true);
            return t;
        });
    }

    /** Starts sign-in in the background. Progress is reported through the session. */
    public void signIn(BedrockSession session) {
        if (clientId.isBlank()) {
            session.fail("Set \"client_id\" in config/bedrocksync.json first (see README).");
            return;
        }
        session.setPending();
        executor.submit(() -> runSignIn(session));
    }

    private void runSignIn(BedrockSession session) {
        try {
            DeviceCode dc = requestDeviceCode();
            session.setDeviceCode(dc.userCode(), dc.verificationUri());
            String msAccessToken = pollForAccessToken(dc);
            XboxIdentity identity = authenticateXbox(msAccessToken);
            session.setSignedIn(identity.gamertag(), identity.xuid());
            BedrockSyncClient.LOGGER.info("Xbox sign-in succeeded for gamertag '{}'.", identity.gamertag());
        } catch (AuthException e) {
            BedrockSyncClient.LOGGER.warn("Sign-in failed: {}", e.getMessage());
            session.fail(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            session.fail("Sign-in was interrupted.");
        } catch (IOException | RuntimeException e) {
            BedrockSyncClient.LOGGER.warn("Sign-in network/parse error: {}", e.toString());
            session.fail("Network or response error during sign-in. Check your connection.");
        }
    }

    private DeviceCode requestDeviceCode() throws IOException, InterruptedException, AuthException {
        HttpResponse<String> r = postForm(DEVICE_CODE_URL, Map.of(
                "client_id", clientId,
                "scope", SCOPE));
        if (r.statusCode() != 200) {
            throw new AuthException("Could not start Microsoft sign-in (HTTP " + r.statusCode()
                    + "). Check client_id and that public client flows are enabled.");
        }
        JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
        return new DeviceCode(
                o.get("device_code").getAsString(),
                o.get("user_code").getAsString(),
                o.get("verification_uri").getAsString(),
                o.get("expires_in").getAsInt(),
                Math.max(1, o.has("interval") ? o.get("interval").getAsInt() : 5));
    }

    private String pollForAccessToken(DeviceCode dc) throws IOException, InterruptedException, AuthException {
        long deadline = System.nanoTime() + Duration.ofSeconds(dc.expiresIn()).toNanos();
        int intervalSec = dc.interval();

        while (System.nanoTime() < deadline) {
            Thread.sleep(intervalSec * 1000L);
            HttpResponse<String> r = postForm(TOKEN_URL, Map.of(
                    "grant_type", DEVICE_GRANT,
                    "client_id", clientId,
                    "device_code", dc.deviceCode()));
            JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();

            if (r.statusCode() == 200 && o.has("access_token")) {
                return o.get("access_token").getAsString();
            }
            String error = o.has("error") ? o.get("error").getAsString() : "unknown_error";
            switch (error) {
                case "authorization_pending" -> { /* keep waiting for the user */ }
                case "slow_down" -> intervalSec += 5;
                case "authorization_declined" -> throw new AuthException("Sign-in was declined.");
                case "expired_token" -> throw new AuthException("The sign-in code expired. Try again.");
                default -> throw new AuthException("Microsoft sign-in failed: " + error);
            }
        }
        throw new AuthException("Sign-in timed out. Try again.");
    }

    private XboxIdentity authenticateXbox(String msAccessToken)
            throws IOException, InterruptedException, AuthException {

        JsonObject xblProps = new JsonObject();
        xblProps.addProperty("AuthMethod", "RPS");
        xblProps.addProperty("SiteName", "user.auth.xboxlive.com");
        xblProps.addProperty("RpsTicket", "d=" + msAccessToken);

        JsonObject xblBody = new JsonObject();
        xblBody.add("Properties", xblProps);
        xblBody.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xblBody.addProperty("TokenType", "JWT");

        HttpResponse<String> xbl = postJson(XBL_AUTH_URL, xblBody);
        if (xbl.statusCode() != 200) {
            throw new AuthException("Xbox Live sign-in failed (HTTP " + xbl.statusCode() + ").");
        }
        String xblToken = JsonParser.parseString(xbl.body()).getAsJsonObject().get("Token").getAsString();

        JsonArray userTokens = new JsonArray();
        userTokens.add(xblToken);
        JsonObject xstsProps = new JsonObject();
        xstsProps.addProperty("SandboxId", "RETAIL");
        xstsProps.add("UserTokens", userTokens);

        JsonObject xstsBody = new JsonObject();
        xstsBody.add("Properties", xstsProps);
        xstsBody.addProperty("RelyingParty", "http://xboxlive.com");
        xstsBody.addProperty("TokenType", "JWT");

        HttpResponse<String> xsts = postJson(XSTS_AUTH_URL, xstsBody);
        if (xsts.statusCode() != 200) {
            throw new AuthException(describeXstsError(xsts));
        }

        JsonObject xo = JsonParser.parseString(xsts.body()).getAsJsonObject();
        JsonObject xui = xo.getAsJsonObject("DisplayClaims")
                .getAsJsonArray("xui").get(0).getAsJsonObject();
        String gamertag = xui.has("gtg") ? xui.get("gtg").getAsString() : "(unknown)";
        String xuid = xui.has("xid") ? xui.get("xid").getAsString() : "";
        return new XboxIdentity(gamertag, xuid);
    }

    /** Maps documented XSTS error codes to actionable messages. Never includes token data. */
    private static String describeXstsError(HttpResponse<String> r) {
        long xerr = -1L;
        try {
            JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
            if (o.has("XErr")) {
                xerr = o.get("XErr").getAsLong();
            }
        } catch (RuntimeException ignored) {
            // fall through to the generic message
        }
        if (xerr == 2148916233L) {
            return "This Microsoft account has no Xbox profile. Create a gamertag at xbox.com first.";
        }
        if (xerr == 2148916235L) {
            return "Xbox Live is not available in your country or region.";
        }
        if (xerr == 2148916236L || xerr == 2148916237L) {
            return "This account needs adult verification on Xbox.";
        }
        if (xerr == 2148916238L) {
            return "This is a child account. It must be added to a family group on Xbox.";
        }
        return "Xbox authorization failed (XErr " + xerr + ").";
    }

    private HttpResponse<String> postForm(String url, Map<String, String> params)
            throws IOException, InterruptedException {
        String body = params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(String url, JsonObject json)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("x-xbl-contract-version", "1")
                .POST(HttpRequest.BodyPublishers.ofString(json.toString(), StandardCharsets.UTF_8))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    /** Device code response. The device_code is secret for the duration of the flow only. */
    record DeviceCode(String deviceCode, String userCode, String verificationUri, int expiresIn, int interval) {}

    /** Xbox display identity. */
    public record XboxIdentity(String gamertag, String xuid) {}

    /** User-facing sign-in failure. Message is safe to display. */
    public static final class AuthException extends Exception {
        private static final long serialVersionUID = 1L;

        public AuthException(String message) {
            super(message);
        }
    }
}
