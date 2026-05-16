package sd2526.trab.impl.external;

import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;

import sd2526.trab.impl.external.zoho.ZohoServiceFactory;
import sd2526.trab.impl.external.zoho.ZohoTokenManager;
import sd2526.trab.impl.external.zoho.msgs.ZohoAccount;
import sd2526.trab.impl.external.zoho.msgs.ZohoAccountReply;
import sd2526.trab.impl.utils.JSON;

public class Zoho {

    static final String MAIL_API_BASE = "https://mail.zoho.eu/api";

    static final String CLIENT_ID = System.getenv("CLIENT_ID");
    static final String CLIENT_SECRET = System.getenv("CLIENT_SECRET");
    static final String REFRESH_TOKEN = System.getenv("REFRESH_TOKEN");

    static {
        if (CLIENT_ID == null || CLIENT_SECRET == null || REFRESH_TOKEN == null) {
            throw new IllegalStateException("CRITICAL ERROR: Environment variables CLIENT_ID, CLIENT_SECRET, or REFRESH_TOKEN are missing!");
        }
    }

    private static final String ACCOUNTS = "/accounts";

    final OAuth20Service service;
    final ZohoTokenManager tokenManager;

    static Zoho instance;

    private Zoho() {
        service = ZohoServiceFactory.buildService(CLIENT_ID, CLIENT_SECRET);
        tokenManager = new ZohoTokenManager(service, REFRESH_TOKEN);
        System.out.println();
        System.out.println("Zoho client initialized with CLIENT_ID=" + CLIENT_ID + " and REFRESH_TOKEN=" + REFRESH_TOKEN );
        System.out.println();
    }

    synchronized public static Zoho getInstance() {
        if (instance == null) {
            instance = new Zoho();
        }
        return instance;
    }

    public ZohoAccount getAccount() throws Exception {
        var accessToken = new OAuth2AccessToken(tokenManager.getValidAccessToken());

        OAuthRequest request = new OAuthRequest(Verb.GET, MAIL_API_BASE + ACCOUNTS);
        //service.signRequest(accessToken, request);

        // Manually inject the Zoho-specific token format
        String token = tokenManager.getValidAccessToken();
        request.addHeader("Authorization", "Zoho-oauthtoken " + token);

        try (Response response = service.execute(request)) {
            if (response.isSuccessful()) {
                var body = response.getBody();
                var data = JSON.decode(body, ZohoAccountReply.class).data();
                if (data == null || data.isEmpty()) {
                    return null;
                }
                return data.get(0);
            } else {
                System.err.println(response.getCode() + "/" + response.getBody());
                return null;
            }
        }
    }
    public String getAccessToken() throws Exception {
        return tokenManager.getValidAccessToken();
    }
}
