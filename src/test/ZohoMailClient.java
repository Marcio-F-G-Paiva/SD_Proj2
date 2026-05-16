package test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import sd2526.trab.api.Message;
import sd2526.trab.api.java.Result;
import sd2526.trab.api.java.Messages;
import sd2526.trab.impl.api.java.AdminMessages;
import sd2526.trab.impl.external.Zoho;
import sd2526.trab.impl.external.zoho.msgs.ZohoAccount;
import sd2526.trab.impl.utils.JSON;

/**
 * Client for Zoho Mail API with OAuth authentication
 */
public class ZohoMailClient implements Messages, AdminMessages {

    private static final Logger Log = Logger.getLogger(ZohoMailClient.class.getName());
    private static final String ZOHO_API_BASE = "https://mail.zoho.eu/api";
    private static final String MESSAGES_PATH = "/accounts/%s/messages";
    private static final String INBOX_QUERY = "?folder=Inbox&limit=200";

    private final Zoho zoho;
    private final HttpClient httpClient;
    private ZohoAccount account;

    public ZohoMailClient() {
        zoho = Zoho.getInstance();
        httpClient = HttpClient.newHttpClient();
    }

    public void sendMessage(Message message) throws Exception {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        if (message.getSender() == null || message.getSender().isBlank()) {
            throw new IllegalArgumentException("Sender cannot be empty");
        }
        if (message.getDestination() == null || message.getDestination().isEmpty()) {
            throw new IllegalArgumentException("Destination cannot be empty");
        }

        var accountId = getAccountId();
        if (message.getId() == null || message.getId().isBlank()) {
            message.setId(String.valueOf(System.currentTimeMillis()));
        }

        var toAddress = String.join(",", message.getDestination());
        var payload = Map.<String, Object>of(
                "fromAddress", message.getSender(),
                "toAddress", toAddress,
                "subject", safeString(message.getSubject()),
                "content", safeString(message.getContents()),
                "contentType", "text/plain"
        );

        var url = ZOHO_API_BASE + String.format(MESSAGES_PATH, accountId);
        var request = authenticatedRequest(url)
                .POST(HttpRequest.BodyPublishers.ofString(JSON.encode(payload)))
                .header("Content-Type", "application/json")
                .build();

        var response = send(request);
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Unable to send message to Zoho: " + response.statusCode() + " -> " + response.body());
        }

        Log.info("Sent message to Zoho mailbox " + getMailboxAddress() + " for local message " + message.getId());
    }

    public List<Message> getInbox(String user) throws Exception {
        if (!matchesUser(user)) {
            return List.of();
        }

        var accountId = getAccountId();
        var url = ZOHO_API_BASE + String.format(MESSAGES_PATH, accountId) + INBOX_QUERY;
        var request = authenticatedRequest(url).GET().build();
        var response = send(request);

        if (response.statusCode() / 100 != 2) {
            throw new IOException("Unable to fetch Zoho inbox: " + response.statusCode() + " -> " + response.body());
        }

        return parseMessages(response.body());
    }

    public Message getInboxMessage(String user, String mid) throws Exception {
        return getInbox(user).stream().filter(m -> mid.equals(m.getId())).findFirst().orElse(null);
    }

    public List<String> getAllInboxMessages(String user) throws Exception {
        return getInbox(user).stream().map(Message::getId).collect(Collectors.toList());
    }

    public List<String> searchInbox(String user, String query) throws Exception {
        if (query == null || query.isBlank()) {
            return getAllInboxMessages(user);
        }

        var lowerQuery = query.toLowerCase(Locale.ROOT);
        return getInbox(user).stream()
                .filter(m -> contains(m, lowerQuery))
                .map(Message::getId)
                .collect(Collectors.toList());
    }

    public void removeInboxMessage(String user, String mid) throws Exception {
        if (!matchesUser(user)) {
            return;
        }
        deleteMessage(mid);
    }

    public void deleteMessage(String messageId) throws Exception {
        var accountId = getAccountId();
        var url = ZOHO_API_BASE + String.format(MESSAGES_PATH, accountId) + "/" + messageId;
        var request = authenticatedRequest(url).DELETE().build();
        var response = send(request);
        if (response.statusCode() / 100 != 2 && response.statusCode() != 204) {
            throw new IOException("Unable to delete Zoho message " + messageId + ": " + response.statusCode() + " -> " + response.body());
        }
        Log.info("Deleted Zoho message " + messageId);
    }

    public void deleteUserInbox(String user) throws Exception {
        for (var mid : getAllInboxMessages(user)) {
            deleteMessage(mid);
        }
    }

    public void removeFromUserInbox(String user, String mid) throws Exception {
        removeInboxMessage(user, mid);
    }

    private HttpRequest.Builder authenticatedRequest(String url) throws Exception {
        var token = zoho.getAccessToken();
        return HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Zoho-oauthtoken " + token)
                .header("Accept", "application/json");
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private synchronized ZohoAccount getAccount() throws Exception {
        if (account == null) {
            account = zoho.getAccount();
            if (account == null) {
                throw new IllegalStateException("Unable to resolve Zoho account");
            }
        }
        return account;
    }

    private String getAccountId() throws Exception {
        return getAccount().accountId();
    }

    private String getMailboxAddress() throws Exception {
        return getAccount().mailboxAddress();
    }

    private boolean matchesUser(String user) throws Exception {
        if (user == null || user.isBlank()) {
            return false;
        }
        var accountAddress = getMailboxAddress();
        if (accountAddress.equalsIgnoreCase(user)) {
            return true;
        }
        if (!user.contains("@")) {
            return accountAddress.toLowerCase(Locale.ROOT).startsWith(user.toLowerCase(Locale.ROOT) + "@");
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private List<Message> parseMessages(String body) {
        var json = JSON.decode(body, Map.class);
        var data = (List<Map<String, Object>>) json.get("data");
        if (data == null) {
            data = (List<Map<String, Object>>) json.get("messages");
        }
        if (data == null) {
            return List.of();
        }
        var messages = new ArrayList<Message>(data.size());
        for (var item : data) {
            messages.add(toMessage(item));
        }
        return messages;
    }

    private Message toMessage(Map<String, Object> item) {
        var id = firstString(item, "messageId", "id", "mailMessageId");
        var sender = firstString(item, "fromAddress", "from", "sender", "fromEmail", "senderEmail");
        var subject = firstString(item, "subject", "title");
        var content = firstString(item, "content", "plainText", "contentText", "body");
        if (content == null) {
            var contentObj = item.get("content");
            if (contentObj instanceof Map<?, ?> contentMap) {
                content = firstString((Map<String, Object>) contentMap, "plainText", "text", "body");
            }
        }
        var recipients = collectRecipients(item, "toAddress", "toAddresses", "to", "toList");

        if (id == null || id.isBlank()) {
            id = String.valueOf(System.currentTimeMillis());
        }
        if (sender == null || sender.isBlank()) {
            sender = "unknown@unknown";
        }
        if (content == null) {
            content = "";
        }
        if (recipients.isEmpty()) {
            recipients = List.of("unknown@unknown");
        }

        return new Message(id, sender, new HashSet<>(recipients), subject, content);
    }

    @SuppressWarnings("unchecked")
    private static String firstString(Map<String, Object> item, String... keys) {
        for (var key : keys) {
            var value = item.get(key);
            if (value instanceof String s && !s.isBlank()) {
                return s;
            }
            if (value instanceof Map<?, ?> nested) {
                var nestedValue = firstString((Map<String, Object>) nested, "email", "address", "name", "text", "body");
                if (nestedValue != null && !nestedValue.isBlank()) {
                    return nestedValue;
                }
            }
            if (value instanceof List<?> list) {
                for (var element : list) {
                    if (element instanceof String s && !s.isBlank()) {
                        return s;
                    }
                    if (element instanceof Map<?, ?> map) {
                        var nestedValue = firstString((Map<String, Object>) map, "email", "address", "name");
                        if (nestedValue != null && !nestedValue.isBlank()) {
                            return nestedValue;
                        }
                    }
                }
            }
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> collectRecipients(Map<String, Object> item, String... keys) {
        for (var key : keys) {
            var value = item.get(key);
            var recipients = extractAddresses(value);
            if (!recipients.isEmpty()) {
                return recipients;
            }
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static List<String> extractAddresses(Object value) {
        if (value instanceof String s && !s.isBlank()) {
            return splitAddresses(s);
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .flatMap(item -> extractAddresses(item).stream())
                    .collect(Collectors.toList());
        }
        if (value instanceof Map<?, ?> map) {
            var address = firstString((Map<String, Object>) map, "email", "address", "toAddress", "name");
            if (address != null && !address.isBlank()) {
                return splitAddresses(address);
            }
            return map.values().stream()
                    .flatMap(item -> extractAddresses(item).stream())
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    private static List<String> splitAddresses(String value) {
        return List.of(value.split("[;,]"))
                .stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private boolean contains(Message message, String lowerQuery) {
        return safeContains(message.getId(), lowerQuery)
                || safeContains(message.getSender(), lowerQuery)
                || safeContains(message.getSubject(), lowerQuery)
                || safeContains(message.getContents(), lowerQuery)
                || message.getDestination().stream().anyMatch(dest -> safeContains(dest, lowerQuery));
    }

    private static boolean safeContains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }

    @Override
    public Result<Void> remotePostMessage(Message m) {
        if (m == null) {
            return Result.error(Result.ErrorCode.BAD_REQUEST);
        }
        try {
            sendMessage(m);
            return Result.ok();
        } catch (IllegalArgumentException e) {
            return Result.error(Result.ErrorCode.BAD_REQUEST);
        } catch (Exception e) {
            Log.severe("remotePostMessage failed: " + e.getMessage());
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Void> remoteDeleteMessage(String mid) {
        if (mid == null || mid.isBlank()) {
            return Result.error(Result.ErrorCode.BAD_REQUEST);
        }
        try {
            deleteMessage(mid);
            return Result.ok();
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("404")) {
                return Result.ok();
            }
            Log.severe("remoteDeleteMessage failed: " + e.getMessage());
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        } catch (Exception e) {
            Log.severe("remoteDeleteMessage failed: " + e.getMessage());
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Void> remoteDeleteUserInbox(String name) {
        if (name == null || name.isBlank()) {
            return Result.error(Result.ErrorCode.BAD_REQUEST);
        }
        try {
            if (!matchesUser(name)) {
                return Result.error(Result.ErrorCode.FORBIDDEN);
            }
            deleteUserInbox(name);
            return Result.ok();
        } catch (Exception e) {
            Log.severe("remoteDeleteUserInbox failed: " + e.getMessage());
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<String> postMessage(String pwd, Message msg) {
        if (pwd == null || msg == null) {
            return Result.error(Result.ErrorCode.BAD_REQUEST);
        }
        if (msg.getSender() == null || msg.getSender().isBlank()) {
            return Result.error(Result.ErrorCode.BAD_REQUEST);
        }
        try {
            if (!matchesUser(msg.getSender())) {
                return Result.error(Result.ErrorCode.FORBIDDEN);
            }
        } catch (Exception e) {
            Log.severe("postMessage failed while validating sender: " + e.getMessage());
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
        if (msg.getId() == null || msg.getId().isBlank()) {
            msg.setId(String.valueOf(System.currentTimeMillis()));
        }
        try {
            sendMessage(msg);
            return Result.ok(msg.getId());
        } catch (IllegalArgumentException e) {
            return Result.error(Result.ErrorCode.BAD_REQUEST);
        } catch (Exception e) {
            Log.severe("postMessage failed: " + e.getMessage());
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Message> getInboxMessage(String name, String mid, String pwd) {
        if (name == null || name.isBlank() || mid == null || mid.isBlank() || pwd == null) {
            return Result.error(Result.ErrorCode.BAD_REQUEST);
        }
        try {
            if (!matchesUser(name)) {
                return Result.error(Result.ErrorCode.FORBIDDEN);
            }
            var message = getInboxMessage(name, mid);
            return message == null ? Result.error(Result.ErrorCode.NOT_FOUND) : Result.ok(message);
        } catch (Exception e) {
            Log.severe("getInboxMessage failed: " + e.getMessage());
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<List<String>> getAllInboxMessages(String name, String pwd) {
        if (name == null || name.isBlank() || pwd == null) {
            return Result.error(Result.ErrorCode.BAD_REQUEST);
        }
        try {
            if (!matchesUser(name)) {
                return Result.error(Result.ErrorCode.FORBIDDEN);
            }
            return Result.ok(getAllInboxMessages(name));
        } catch (Exception e) {
            Log.severe("getAllInboxMessages failed: " + e.getMessage());
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Void> removeInboxMessage(String name, String mid, String pwd) {
        if (name == null || name.isBlank() || mid == null || mid.isBlank() || pwd == null) {
            return Result.error(Result.ErrorCode.BAD_REQUEST);
        }
        try {
            if (!matchesUser(name)) {
                return Result.error(Result.ErrorCode.FORBIDDEN);
            }
            var message = getInboxMessage(name, mid);
            if (message == null) {
                return Result.error(Result.ErrorCode.NOT_FOUND);
            }
            deleteMessage(mid);
            return Result.ok();
        } catch (Exception e) {
            Log.severe("removeInboxMessage failed: " + e.getMessage());
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Void> deleteMessage(String name, String mid, String pwd) {
        if (name == null || name.isBlank() || mid == null || mid.isBlank() || pwd == null) {
            return Result.error(Result.ErrorCode.BAD_REQUEST);
        }
        try {
            if (!matchesUser(name)) {
                return Result.error(Result.ErrorCode.FORBIDDEN);
            }
            var message = getInboxMessage(name, mid);
            if (message == null) {
                return Result.error(Result.ErrorCode.NOT_FOUND);
            }
            deleteMessage(mid);
            return Result.ok();
        } catch (Exception e) {
            Log.severe("deleteMessage failed: " + e.getMessage());
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<List<String>> searchInbox(String name, String pwd, String query) {
        if (name == null || name.isBlank() || pwd == null || query == null) {
            return Result.error(Result.ErrorCode.BAD_REQUEST);
        }
        try {
            if (!matchesUser(name)) {
                return Result.error(Result.ErrorCode.FORBIDDEN);
            }
            return Result.ok(searchInbox(name, query));
        } catch (Exception e) {
            Log.severe("searchInbox failed: " + e.getMessage());
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }
}
