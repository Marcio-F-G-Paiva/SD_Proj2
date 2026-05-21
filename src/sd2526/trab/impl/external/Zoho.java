package sd2526.trab.impl.external;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Zoho implements Messages {

    static final String MAIL_API_BASE = "https://mail.zoho.eu/api";
    private static final String ACCOUNTS = "/accounts";
    private static final String FOLDERS = "/folders";
    private static final String MESSAGES = "/messages";
    private static final int MESSAGE_LIMIT = 200;
    private static final String METADATA_DELIMITER = "\n------\n";

    private static final String CLIENT_ID = "1000.KV9CB8Z3MCYI1DTCQDOTZXCL9HSH6C";
    private static final String CLIENT_SECRET = "your_real_client_secret";
    private static final String REFRESH_TOKEN = "1000.your_real_refresh_token";

    protected final static String THIS_DOMAIN = IP.domain();
    protected final static String AT_THIS_DOMAIN = '@' + THIS_DOMAIN;

    final OAuth20Service service;
    final ZohoTokenManager tokenManager;

    static Zoho instance;

    private Zoho() {
        this.service = ZohoServiceFactory.buildService(CLIENT_ID, CLIENT_SECRET);
        this.tokenManager = new ZohoTokenManager(service, REFRESH_TOKEN);
    }

    synchronized public static Zoho getInstance() {
        if (instance == null) {
            instance = new Zoho();
        }
        return instance;
    }

    public void deleteAllMessages() throws Exception {
        List<Message> messages = listMessages();
        for (Message msg : messages) {
            deleteMessage(msg.getId());
        }
    }

    public ZohoAccount getAccount() throws Exception {
        String url = MAIL_API_BASE + ACCOUNTS;
        OAuthRequest request = new OAuthRequest(Verb.GET, url);
        service.signRequest(tokenManager.getValidAccessToken(), request);

        try (Response response = service.execute(request)) {
            if (response.isSuccessful()) {
                var data = JSON.decode(response.getBody(), ZohoAccountReply.class).data();
                return (data == null || data.isEmpty()) ? null : data.get(0);
            } else {
                throw new IllegalStateException("Failed to get Zoho account: " + response.getBody());
            }
        }
    }

    static class ZohoMessageMapper {
        static String toZohoContent(Message message) {
            return message.getContents() + METADATA_DELIMITER +
                    "id=" + message.getId() + "\n" +
                    "sender=" + message.getSender() + "\n" +
                    "destination=" + String.join(",", message.getDestination()) + "\n" +
                    "creationTime=" + message.getCreationTime();
        }

        static Message toMessage(ZohoMessage zohoMessage) {
            String content = zohoMessage.content();
            String[] parts = content.split(METADATA_DELIMITER);
            String originalContent = parts.length > 0 ? parts[0] : "";

            Message msg = new Message();
            msg.setContents(originalContent);
            msg.setSubject(zohoMessage.subject());
            msg.setId(zohoMessage.messageId());

            if (parts.length > 1) {
                String[] metadata = parts[1].split("\n");
                for (String line : metadata) {
                    String[] kv = line.split("=", 2);
                    if (kv.length == 2) {
                        switch (kv[0]) {
                            case "id":
                                msg.setId(kv[1]);
                                break;
                            case "sender":
                                msg.setSender(kv[1]);
                                break;
                            case "destination":
                                msg.setDestination(Set.of(kv[1].split(",")));
                                break;
                            case "creationTime":
                                msg.setCreationTime(Long.parseLong(kv[1]));
                                break;
                        }
                    }
                }
            }
            return msg;
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
            String mid = sendMessage(msg);
            return mid != null ? Result.ok(mid) : Result.error(Result.ErrorCode.INTERNAL_ERROR);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(Result.ErrorCode.BAD_REQUEST);
        }
    }

    public String sendMessage(Message message) throws Exception {
        var account = getAccount();
        var url = MAIL_API_BASE + ACCOUNTS + "/" + account.accountId() + MESSAGES;

        List<ZohoMessage> payload = new ArrayList<>();
        for (String dest : message.getDestination()) {
            payload.add(new ZohoMessage(null, message.getSender(), dest, message.getSubject(), ZohoMessageMapper.toZohoContent(message)));
        }

        OAuthRequest request = new OAuthRequest(Verb.POST, url);
        request.setPayload(JSON.encode(payload));
        request.addHeader("Content-Type", "application/json");
        service.signRequest(tokenManager.getValidAccessToken(), request);

        try (Response response = service.execute(request)) {
            if (response.isSuccessful()) {
                var data = JSON.decode(response.getBody(), ZohoMessageReply.class).data();
                if (data != null && !data.isEmpty()) {
                    return data.get(0).messageId();
                }
            }
            throw new IllegalStateException("Failed to send message: " + response.getBody());
        }
    }

    @Override
    public Result<Message> getInboxMessage(String name, String mid, String pwd) {
        try {
            Message msg = getMessage(mid);
            return msg != null ? Result.ok(msg) : Result.error(Result.ErrorCode.NOT_FOUND);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    private Message getMessage(String zohoMessageId) throws Exception {
        var account = getAccount();
        var url = MAIL_API_BASE + ACCOUNTS + "/" + account.accountId() + MESSAGES + "/" + zohoMessageId;

        OAuthRequest request = new OAuthRequest(Verb.GET, url);
        service.signRequest(tokenManager.getValidAccessToken(), request);

        try (Response response = service.execute(request)) {
            if (response.isSuccessful()) {
                var list = ZohoMessageMapper.parseMessages(response.getBody());
                return list.isEmpty() ? null : list.get(0);
            } else if (response.getCode() == 404) {
                return null;
            }
            throw new IllegalStateException("Failed to get message: " + response.getBody());
        }
    }

    @Override
    public Result<List<String>> getAllInboxMessages(String name, String pwd) {
        try {
            return Result.ok(listMessages().stream().map(Message::getId).collect(Collectors.toList()));
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    private String getFolderId(String folderName) throws Exception {
        var account = getAccount();
        String url = MAIL_API_BASE + ACCOUNTS + "/" + account.accountId() + FOLDERS;
        OAuthRequest request = new OAuthRequest(Verb.GET, url);
        service.signRequest(tokenManager.getValidAccessToken(), request);

        try (Response response = service.execute(request)) {
            if (response.isSuccessful()) {
                List<ZohoFolder> folders = JSON.decode(response.getBody(), ZohoFolderReply.class).data();
                return folders.stream()
                        .filter(f -> f.folderName().equalsIgnoreCase(folderName))
                        .map(ZohoFolder::folderId)
                        .findFirst()
                        .orElse(null);
            }
            throw new IllegalStateException("Failed to get folder ID: " + response.getBody());
        }
    }

    private List<Message> listMessages() throws Exception {
        var account = getAccount();
        String url = MAIL_API_BASE + ACCOUNTS + "/" + account.accountId() + MESSAGES + "/view?limit=" + MESSAGE_LIMIT;
        OAuthRequest request = new OAuthRequest(Verb.GET, url);
        service.signRequest(tokenManager.getValidAccessToken(), request);

        try (Response response = service.execute(request)) {
            if (response.isSuccessful()) {
                return ZohoMessageMapper.parseMessages(response.getBody());
            }
            throw new IllegalStateException("Failed to list messages: " + response.getBody());
        }
    }

    @Override
    public Result<Void> removeInboxMessage(String name, String mid, String pwd) {
        try {
            deleteMessage(mid);
            return Result.ok();
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Void> deleteMessage(String name, String mid, String pwd) {
        try {
            Message msg = getMessage(mid);
            if (msg == null) return Result.error(Result.ErrorCode.NOT_FOUND);

            getRemoteRecipientAddresses(msg).forEach(dest -> Clients.AdminMessagesClient.get(dest).remoteDeleteMessage(mid));
            deleteMessage(mid);
            return Result.ok();
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    private List<String> getRemoteRecipientAddresses(Message msg) {
        return msg.getDestination().stream().filter(Predicate.not(this::isLocalAddress)).collect(Collectors.toList());
    }

    protected boolean isLocalAddress(String address) {
        return address.endsWith(AT_THIS_DOMAIN);
    }

    private void deleteMessage(String mid) throws Exception {
        if (mid == null) return;
        var account = getAccount();
        String folderId = getFolderId("Inbox");
        if (folderId == null) throw new IllegalStateException("Inbox folder not found");

        var url = MAIL_API_BASE + ACCOUNTS + "/" + account.accountId() + FOLDERS + "/" + folderId + MESSAGES + "/" + mid + "?expunge=true";
        OAuthRequest request = new OAuthRequest(Verb.DELETE, url);
        service.signRequest(tokenManager.getValidAccessToken(), request);

        try (Response response = service.execute(request)) {
            if (!response.isSuccessful() && response.getCode() != 204) {
                throw new IllegalStateException("Failed to delete message: " + response.getBody());
            }
        }
    }

    @Override
    public Result<List<String>> searchInbox(String name, String pwd, String query) {
        try {
            List<Message> msgs = (query == null || query.strip().isEmpty()) ? listMessages() : listMessages(query);
            return Result.ok(msgs.stream().map(Message::getId).collect(Collectors.toList()));
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    private List<Message> listMessages(String query) throws Exception {
        var account = getAccount();
        String url = MAIL_API_BASE + ACCOUNTS + "/" + account.accountId() + MESSAGES + "/search?limit=" + MESSAGE_LIMIT + "&searchKey=entire:" + query;
        OAuthRequest request = new OAuthRequest(Verb.GET, url);
        service.signRequest(tokenManager.getValidAccessToken(), request);

        try (Response response = service.execute(request)) {
            if (response.isSuccessful()) {
                return ZohoMessageMapper.parseMessages(response.getBody());
            }
            throw new IllegalStateException("Failed to search messages: " + response.getBody());
        }
    }
}
