package sd2526.trab.impl.rest.servers;

import java.util.List;

import jakarta.inject.Singleton;
import jakarta.ws.rs.Path;
import sd2526.trab.api.Message;
import sd2526.trab.api.rest.RestMessages;
import sd2526.trab.impl.api.rest.RestAdminMessages;
import sd2526.trab.impl.external.Zoho;

@Path(RestMessages.PATH)
@Singleton
public class ZohoMailResource extends RestResource implements RestMessages, RestAdminMessages {

    private final Zoho impl;
    
    public ZohoMailResource() {
        this.impl = Zoho.getInstance();
    }

    @Override
    public String postMessage(String pwd, Message msg) {
        System.out.println("[ZOHO-RESOURCE] Posting message: " + msg);
        return super.resultOrThrow(impl.postMessage(pwd, msg));
    }

    @Override
    public Message getMessage(String name, String mid, String pwd) {
        System.out.println("[ZOHO-RESOURCE] Retrieving message with id: " + mid + " for user: " + name);
        return super.resultOrThrow(impl.getInboxMessage(name, mid, pwd));
    }

    @Override
    public List<String> getMessages(String name, String pwd, String query) {
        System.out.println("[ZOHO-RESOURCE] Searching messages with query: " + query + " for user: " + name);
        return super.resultOrThrow(impl.searchInbox(name, pwd, query));
    }

    @Override
    public void removeFromUserInbox(String name, String mid, String pwd) {
        System.out.println("[ZOHO-RESOURCE] Removing message with id: " + mid + " from user: " + name);
        super.resultOrThrow(impl.removeInboxMessage(name, mid, pwd));
    }

    @Override
    public void deleteMessage(String name, String mid, String pwd) {
        System.out.println("[ZOHO-RESOURCE] Deleting message with id: " + mid + " from user: " + name);
        super.resultOrThrow(impl.deleteMessage(name, mid, pwd));
    }

    @Override
    public void remotePostMessage(Message m) {
        System.out.println("[ZOHO-RESOURCE] Remote Posting message: " + m);
        super.resultOrThrow(impl.postMessage("",m));
    }

    @Override
    public void remoteDeleteMessage(String mid) {
        System.out.println("[ZOHO-RESOURCE] Remote Deleting message with id: " + mid);
        super.resultOrThrow(impl.deleteMessage("", mid, ""));
    }

    @Override
    public void remoteDeleteUserInbox(String name) {
        System.out.println("[ZOHO-RESOURCE] Remote Removing all messages from user inbox: " + name);
        super.resultOrThrow(impl.removeInboxMessage(name, null, ""  ));
    }

}
