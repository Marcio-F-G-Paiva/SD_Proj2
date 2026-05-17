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
public class ZohoMailResource extends RestResource implements RestMessages {

    private final Zoho impl;
    
    public ZohoMailResource() {
        this.impl = Zoho.getInstance();
    }

    @Override
    public String postMessage(String pwd, Message msg) {
        return super.resultOrThrow(impl.postMessage(pwd, msg));
    }

    @Override
    public Message getMessage(String name, String mid, String pwd) {
        return super.resultOrThrow(impl.getInboxMessage(name, mid, pwd));
    }

    @Override
    public List<String> getMessages(String name, String pwd, String query) {
        return super.resultOrThrow(impl.searchInbox(name, pwd, query));
    }

    @Override
    public void removeFromUserInbox(String name, String mid, String pwd) {
        super.resultOrThrow(impl.removeInboxMessage(name, mid, pwd));
    }

    @Override
    public void deleteMessage(String name, String mid, String pwd) {
        super.resultOrThrow(impl.deleteMessage(name, mid, pwd));
    }

}
