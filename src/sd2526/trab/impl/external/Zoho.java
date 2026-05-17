package sd2526.trab.impl.external;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;

import sd2526.trab.api.Message;
import sd2526.trab.api.java.Messages;
import sd2526.trab.api.java.Result;
import sd2526.trab.impl.external.zoho.ZohoServiceFactory;
import sd2526.trab.impl.external.zoho.ZohoTokenManager;
import sd2526.trab.impl.external.zoho.msgs.ZohoAccount;
import sd2526.trab.impl.external.zoho.msgs.ZohoAccountReply;
import sd2526.trab.impl.external.zoho.msgs.ZohoMessage;
import sd2526.trab.impl.external.zoho.msgs.ZohoMessageReply;
import sd2526.trab.impl.utils.JSON;

public class Zoho implements Messages{

    static final String MAIL_API_BASE = "https://mail.zoho.eu/api";

    // WORKED YESTERDAY DOES NOT WORK NOW GREAT I SURE LOVE SD!
    static final String CLIENT_ID = System.getenv("CLIENT_ID");
    static final String CLIENT_SECRET = System.getenv("CLIENT_SECRET");
    static final String REFRESH_TOKEN = System.getenv("REFRESH_TOKEN");

    static {
        if (CLIENT_ID == null || CLIENT_SECRET == null || REFRESH_TOKEN == null) {
            throw new IllegalStateException("CRITICAL ERROR: Environment variables CLIENT_ID, CLIENT_SECRET, or REFRESH_TOKEN are missing!\nPlease set them before running the application with:\nexport CLIENT_ID=your_client_id\nexport CLIENT_SECRET=your_client_secret\nexport REFRESH_TOKEN=your_refresh_token");
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
        OAuthRequest request = new OAuthRequest(Verb.GET, MAIL_API_BASE + ACCOUNTS);

        // Manually inject the Zoho-specific token format
        String token = tokenManager.getValidAccessToken();
        request.addHeader("Authorization", "Zoho-oauthtoken " + token);

        //var accessToken = new OAuth2AccessToken(tokenManager.getValidAccessToken());
        //service.signRequest(accessToken, request);

        try (Response response = service.execute(request)) {
            if (response.isSuccessful()) {
                String body = response.getBody();
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
    
    // --- Message related helpers ---
    class ZohoMessageMapper {
        static List<ZohoMessage> toZohoPayload(Message message) {
            List<ZohoMessage> list = new ArrayList<>();
            for (String dest : message.getDestination()) {
                list.add(new ZohoMessage(
                    message.getSender(),
                    dest,
                    message.getSubject(),
                    message.getContents()
                ));
            }
            return list;
            
        }
        static Message toMessage(ZohoMessage zohoMessage) {
            return new Message(
                zohoMessage.fromAddress(),
                Set.of(zohoMessage.toAddress()),
                zohoMessage.subject(),
                zohoMessage.content()
            );
        }

        // Note: Assumed to be defined here or elsewhere within this scope as called below
        static List<Message> parseMessages(String body) {
            List<ZohoMessage> data = JSON.decode(body, ZohoMessageReply.class).data();
            if (data == null) return Collections.emptyList();
            return data.stream().map(ZohoMessageMapper::toMessage).collect(Collectors.toList());
        }
    }

    @Override
    public Result<String> postMessage(String pwd, Message msg) {
        try{
            if(sendMessage(msg))
                return Result.ok();
                
        }catch(Exception e){
            return Result.error(Result.ErrorCode.BAD_REQUEST);
        }
        return Result.error(Result.ErrorCode.INTERNAL_ERROR);
    }

    public boolean sendMessage(Message message) throws Exception {
        if (message == null) throw new IllegalArgumentException("message");
        var account = getAccount();
        if (account == null) throw new IllegalStateException("Zoho account not available");

        var url = MAIL_API_BASE + "/accounts/" + account.accountId() + "/messages";
        var payload = ZohoMessageMapper.toZohoPayload(message);

        OAuthRequest request = new OAuthRequest(Verb.POST, url);
        request.addHeader("Accept", "application/json");
        request.addHeader("Content-Type", "application/json");
        request.setPayload(JSON.encode(payload));

        // Manually inject the Zoho-specific token format
        String token = tokenManager.getValidAccessToken();
        request.addHeader("Authorization", "Zoho-oauthtoken " + token);

        try (Response response = service.execute(request)) {
            return response.isSuccessful();
        }
    }

    @Override
    public Result<Message> getInboxMessage(String name, String mid, String pwd) {
        try{
            Message msg = getMessage(mid);
            if(msg!= null)
                return Result.ok(msg);
        } catch(Exception e){

        }
        return Result.error(Result.ErrorCode.INTERNAL_ERROR);
    }

    private Message getMessage(String zohoMessageId) throws Exception {
        if (zohoMessageId == null) return null;
        var account = getAccount();
        var url = MAIL_API_BASE + "/accounts/" + account.accountId() + "/messages/" + zohoMessageId;

        OAuthRequest request = new OAuthRequest(Verb.GET, url);
        
        // Manually inject the Zoho-specific token format
        String token = tokenManager.getValidAccessToken();
        request.addHeader("Authorization", "Zoho-oauthtoken " + token);

        try (Response response = service.execute(request)) {
            if (response.isSuccessful()) {
                var body = response.getBody();
                var list = ZohoMessageMapper.parseMessages(body);
                return list.isEmpty() ? null : list.get(0);
            } else {
                if (response.getCode() == 404) return null;
                throw new IllegalStateException("Zoho getMessage failed: " + response.getCode() + " / " + response.getBody());
            }
        }
    }

    @Override
    public Result<List<String>> getAllInboxMessages(String name, String pwd) {
        try{
            List<Message> msgs = listMessages();
            List<String> mids = msgs.stream().map((m)->m.getId()).toList();
            return Result.ok(mids);
        }catch(Exception e){

        }
        return Result.error(Result.ErrorCode.INTERNAL_ERROR);
    }

    private List<Message> listMessages() throws Exception {
        
        // String q = "?limit=" + limit;
        // if (folder != null && !folder.isBlank()) q = "?folder=" + folder + "&limit=" + limit;
        ZohoAccount account = getAccount();
        String url = MAIL_API_BASE + "/accounts/" + account.accountId() + "/messages/view";

        OAuthRequest request = new OAuthRequest(Verb.GET, url);
        
        // Manually inject the Zoho-specific token format
        String token = tokenManager.getValidAccessToken();
        request.addHeader("Authorization", "Zoho-oauthtoken " + token);

        try (Response response = service.execute(request)) {
            if (response.isSuccessful()) {
                String body = response.getBody();
                return ZohoMessageMapper.parseMessages(body);
            } else {
                throw new IllegalStateException("Zoho listMessages failed: " + response.getCode() + " / " + response.getBody());
            }
        }
    }

    @Override
    public Result<Void> removeInboxMessage(String name, String mid, String pwd) {
        // TODO: Differentiate from deleteMessage
        try{
            deleteMessage(mid);
            return Result.ok();
        }catch(Exception e){

        }
        return Result.error(Result.ErrorCode.INTERNAL_ERROR);
    }

    @Override
    public Result<Void> deleteMessage(String name, String mid, String pwd) {
        try{
            deleteMessage(mid);
            return Result.ok();
        }catch(Exception e){

        }
        return Result.error(Result.ErrorCode.INTERNAL_ERROR);
    }

    private void deleteMessage(String zohoMessageId) throws Exception {
        if (zohoMessageId == null) return;
        var account = getAccount();
        var url = MAIL_API_BASE + "/accounts/" + account.accountId() + "/messages/" + zohoMessageId;

        OAuthRequest request = new OAuthRequest(Verb.DELETE, url);
        
        // Manually inject the Zoho-specific token format
        String token = tokenManager.getValidAccessToken();
        request.addHeader("Authorization", "Zoho-oauthtoken " + token);

        try (Response response = service.execute(request)) {
            if (response.isSuccessful() || response.getCode() == 204) {
                return;
            } else if (response.getCode() == 404) {
                return;
            } else {
                throw new IllegalStateException("Zoho deleteMessage failed: " + response.getCode() + " / " + response.getBody());
            }
        }
    }

    @Override
    public Result<List<String>> searchInbox(String name, String pwd, String query) {
        try{
            List<Message> msgs = listMessages(query);
            List<String> mids = msgs.stream().map((m)->m.getId()).toList();
            return Result.ok(mids);
        } catch(Exception e){

        }
        return Result.error(Result.ErrorCode.INTERNAL_ERROR);
    }

    private List<Message> listMessages(String query) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'listMessages'");
    }
}