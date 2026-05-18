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
import sd2526.trab.impl.external.zoho.msgs.ZohoFolder;
import sd2526.trab.impl.external.zoho.msgs.ZohoFolderReply;
import sd2526.trab.impl.external.zoho.msgs.ZohoMessage;
import sd2526.trab.impl.external.zoho.msgs.ZohoMessageReply;
import sd2526.trab.impl.java.clients.Clients;
import sd2526.trab.impl.utils.IP;
import sd2526.trab.impl.utils.JSON;

import java.util.function.Predicate;

public class Zoho implements Messages {

    static final String MAIL_API_BASE = "https://mail.zoho.eu/api";

    // Standardized constants: NO trailing slashes to prevent 404 routing errors
    private static final String ACCOUNTS = "/accounts";
    private static final String FOLDERS = "/folders";
    private static final String MESSAGES = "/messages";
    private static final int MESSAGE_LIMIT = 200; 

    static final String CLIENT_ID = "1000.RO7AAP0IS3YUWKQT397EMOULH1CEWH";
    static final String CLIENT_SECRET = "8ba863010e3539e84efc012588d1ee25919374bc4f";
    static final String REFRESH_TOKEN = "1000.33d89cf6095dfd97d9120d37e4394770.8d9b80c42f812ab534bfbee41dd0cc23";

    static {
        System.out.println("[ZOHO-STATIC] Validating environment credentials...");
        if (CLIENT_ID == null || CLIENT_SECRET == null || REFRESH_TOKEN == null) {
            throw new IllegalStateException("CRITICAL ERROR: Environment variables missing!");
        }
    }

    protected final static String THIS_DOMAIN = IP.domain();
    protected final static String AT_THIS_DOMAIN = '@' + THIS_DOMAIN ;

    final OAuth20Service service;
    final ZohoTokenManager tokenManager;

    static Zoho instance;

    private Zoho() {
        System.out.println("[ZOHO-INIT] Constructing Zoho instance...");
        this.service = ZohoServiceFactory.buildService(CLIENT_ID, CLIENT_SECRET);
        this.tokenManager = new ZohoTokenManager(service, REFRESH_TOKEN);
    }

    synchronized public static Zoho getInstance() {
        if (instance == null) {
            instance = new Zoho();
        }
        return instance;
    }

    public ZohoAccount getAccount() throws Exception {
        // Fixes trailing slash -> produces https://mail.zoho.eu/api/accounts
        String url = MAIL_API_BASE + ACCOUNTS; 
        System.out.println("[ZOHO-API] Fetching account info from: " + url);
        OAuthRequest request = new OAuthRequest(Verb.GET, url);

        String token = tokenManager.getValidAccessToken();
        request.addHeader("Authorization", "Zoho-oauthtoken " + token);
        request.addHeader("Accept", "application/json");
        request.addHeader("Content-Type", "application/json");

        try (Response response = service.execute(request)) {
            System.out.println("[ZOHO-API] getAccount response code: " + response.getCode());
            if (response.isSuccessful()) {
                String body = response.getBody();
                var data = JSON.decode(body, ZohoAccountReply.class).data();
                if (data == null || data.isEmpty()) {
                    return null;
                }
                return data.get(0);
            } else {
                System.err.println("[ZOHO-ERROR] getAccount HTTP Error: " + response.getCode() + " / " + response.getBody());
                return null;
            }
        }
    }

    public String getAccessToken() throws Exception {
        return tokenManager.getValidAccessToken();
    }

    class ZohoMessageMapper {
        static List<ZohoMessage> toZohoPayload(Message message) {
            List<ZohoMessage> list = new ArrayList<>();
            for (String dest : message.getDestination()) {
                list.add(new ZohoMessage(message.getSender(), dest, message.getSubject(), message.getContents()));
            }
            return list;
        }

        static Message toMessage(ZohoMessage zohoMessage) {
            return new Message(zohoMessage.fromAddress(), Set.of(zohoMessage.toAddress()), zohoMessage.subject(), zohoMessage.content());
        }

        static List<Message> parseMessages(String body) {
            List<ZohoMessage> data = JSON.decode(body, ZohoMessageReply.class).data();
            if (data == null) return Collections.emptyList();
            return data.stream().map(ZohoMessageMapper::toMessage).collect(Collectors.toList());
        }
    }

    @Override
    public Result<String> postMessage(String pwd, Message msg) {
        try {
            if (sendMessage(msg)) return Result.ok();
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(Result.ErrorCode.BAD_REQUEST);
        }
        return Result.error(Result.ErrorCode.INTERNAL_ERROR);
    }

    public boolean sendMessage(Message message) throws Exception {
        if (message == null) throw new IllegalArgumentException("message cannot be null");
        var account = getAccount();
        if (account == null) throw new IllegalStateException("Zoho account not available");

        // Clean slash assembly: /api/accounts/{id}/messages
        var url = MAIL_API_BASE + ACCOUNTS + "/" + account.accountId() + MESSAGES;
        var payload = ZohoMessageMapper.toZohoPayload(message);

        OAuthRequest request = new OAuthRequest(Verb.POST, url);
        request.addHeader("Accept", "application/json");
        request.addHeader("Content-Type", "application/json");
        request.setPayload(JSON.encode(payload));

        String token = tokenManager.getValidAccessToken();
        request.addHeader("Authorization", "Zoho-oauthtoken " + token);

        try (Response response = service.execute(request)) {
            return response.isSuccessful();
        }
    }

    @Override
    public Result<Message> getInboxMessage(String name, String mid, String pwd) {
        try {
            Message msg = getMessage(mid);
            if (msg != null) return Result.ok(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.error(Result.ErrorCode.INTERNAL_ERROR);
    }

    private Message getMessage(String zohoMessageId) throws Exception {
        if (zohoMessageId == null) return null;
        var account = getAccount();
        if (account == null) throw new IllegalStateException("Zoho account not available");
        
        // Clean slash assembly: /api/accounts/{id}/messages/{msgId}
        var url = MAIL_API_BASE + ACCOUNTS + "/" + account.accountId() + MESSAGES + "/" + zohoMessageId;

        OAuthRequest request = new OAuthRequest(Verb.GET, url);
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
        try {
            List<Message> msgs = listMessages();
            List<String> mids = msgs.stream().map(Message::getId).toList();
            return Result.ok(mids);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.error(Result.ErrorCode.INTERNAL_ERROR);
    }

    private String getFolderId(String folderName) throws Exception {
        ZohoAccount account = getAccount();
        if (account == null) throw new IllegalStateException("Zoho account not available");
        
        // Clean slash assembly: /api/accounts/{id}/folders
        String url = MAIL_API_BASE + ACCOUNTS + "/" + account.accountId() + FOLDERS;
        
        OAuthRequest request = new OAuthRequest(Verb.GET, url);
        String token = tokenManager.getValidAccessToken();
        request.addHeader("Accept", "application/json");
        request.addHeader("Content-Type", "application/json");
        request.addHeader("Authorization", "Zoho-oauthtoken " + token);

        try (Response response = service.execute(request)) {
            if (response.isSuccessful()) {
                String body = response.getBody();
                List<ZohoFolder> folders = JSON.decode(body, ZohoFolderReply.class).data();
                for (ZohoFolder folder : folders) {
                    if (folder.folderName().equalsIgnoreCase(folderName)) {
                        return folder.folderId();
                    }
                }
                return null;
            } else {
                throw new IllegalStateException("Zoho getFolderId failed: " + response.getCode() + " body " + response.getBody());
            }
        }
    }

    private List<Message> listMessages() throws Exception {
        ZohoAccount account = getAccount();
        if (account == null) throw new IllegalStateException("Zoho account not available");

        String q ="?limit=" + MESSAGE_LIMIT;
        
        // Clean slash assembly: /api/accounts/{id}/messages/view
        String url = MAIL_API_BASE + ACCOUNTS + "/" + account.accountId() + MESSAGES + "/view" + q;

        OAuthRequest request = new OAuthRequest(Verb.GET, url);
        String token = tokenManager.getValidAccessToken();
        request.addHeader("Accept", "application/json");
        request.addHeader("Content-Type", "application/json");
        request.addHeader("Authorization", "Zoho-oauthtoken " + token);

        try (Response response = service.execute(request)) {
            if (response.isSuccessful()) {
                return ZohoMessageMapper.parseMessages(response.getBody());
            } else {
                throw new IllegalStateException("Zoho listMessages failed: " + response.getCode());
            }
        }
    }

    @Override
    public Result<Void> removeInboxMessage(String name, String mid, String pwd) {
        try {
            deleteMessage(mid);
            return Result.ok();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.error(Result.ErrorCode.INTERNAL_ERROR);
    }

    @Override
    public Result<Void> deleteMessage(String name, String mid, String pwd) {
        try {
            Message msg = getMessage(mid);
            if (msg == null) return Result.error(Result.ErrorCode.NOT_FOUND);
            
            List<String> remoteAddresses = getRemoteRecipientAddresses(msg);
            for (String dest : remoteAddresses) {
                Clients.AdminMessagesClient.get(dest).remoteDeleteMessage(mid);
            }
            deleteMessage(mid);
            return Result.ok();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.error(Result.ErrorCode.INTERNAL_ERROR);
    }

    protected boolean isLocalAddress(String address) {
        return address.endsWith(AT_THIS_DOMAIN);
    }

    private List<String> getRemoteRecipientAddresses(Message msg) {
        return msg.getDestination().stream().filter(Predicate.not(this::isLocalAddress)).collect(Collectors.toList());          
    } 
        
    private void deleteMessage(String mid) throws Exception {
        if (mid == null) return;
        var account = getAccount();
        if (account == null) throw new IllegalStateException("Zoho account not available");

        String folderId = getFolderId("Inbox");
        if (folderId == null) throw new IllegalStateException("Zoho Inbox folder not found");

        // Clean slash assembly: /api/accounts/{id}/folders/{id}/messages/{mid}
        var url = MAIL_API_BASE + ACCOUNTS + "/" + account.accountId() + FOLDERS + "/" + folderId + MESSAGES + "/" + mid + "?expunge=true";

        OAuthRequest request = new OAuthRequest(Verb.DELETE, url);
        String token = tokenManager.getValidAccessToken();
        request.addHeader("Accept", "application/json");
        request.addHeader("Content-Type", "application/json");
        request.addHeader("Authorization", "Zoho-oauthtoken " + token);

        try (Response response = service.execute(request)) {
            if (response.isSuccessful() || response.getCode() == 204) return;
            throw new IllegalStateException("Zoho deleteMessage failed: " + response.getCode());
        }
    }

    @Override
    public Result<List<String>> searchInbox(String name, String pwd, String query) {
        try {
            List<Message> msgs = listMessages(query);
            List<String> mids = msgs.stream().map(Message::getId).toList();
            return Result.ok(mids);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.error(Result.ErrorCode.INTERNAL_ERROR);
    }

    private List<Message> listMessages(String query) throws Exception {
        ZohoAccount account = getAccount();
        if (account == null) throw new IllegalStateException("Zoho account not available");
        
        String q = "?limit=" + MESSAGE_LIMIT + "&searchKey=entire:" + query;
        
        // Clean slash assembly: /api/accounts/{id}/messages/search
        String url = MAIL_API_BASE + ACCOUNTS + "/" + account.accountId() + MESSAGES + "/search" + q;
        System.out.println("[ZOHO-API] Executing text search against URL: " + url);

        OAuthRequest request = new OAuthRequest(Verb.GET, url);
        String token = tokenManager.getValidAccessToken();
        request.addHeader("Accept", "application/json");
        request.addHeader("Content-Type", "application/json");
        request.addHeader("Authorization", "Zoho-oauthtoken " + token);

        try (Response response = service.execute(request)) {
            if (response.isSuccessful()) {
                return ZohoMessageMapper.parseMessages(response.getBody());
            } else {
                throw new IllegalStateException("Zoho listMessages failed: " + response.getCode());
            }
        }
    }   
}