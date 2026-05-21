package sd2526.trab.impl.rest.servers;

import java.util.logging.Logger;

import org.glassfish.jersey.server.ResourceConfig;
import sd2526.trab.api.java.Messages;
import sd2526.trab.impl.external.Zoho;

public class ZohoMailServer extends AbstractRestServer {

    public static final int PORT = 5678;

    private static Logger Log = Logger.getLogger(ZohoMailServer.class.getName());

    ZohoMailServer() {
        super(Log, Messages.SERVICE_NAME, PORT);
    }

    @Override
    void registerResources(ResourceConfig config) {
        config.register(ZohoMailResource.class);
    }

    public static void main(String[] args) {
        if (args.length > 0 && "true".equalsIgnoreCase(args[0])) {
            try {
                System.out.println("Starting with a clean state...");
                Zoho.getInstance().deleteAllMessages();
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Failed to delete all messages. Aborting.");
                return;
            }
        }
        new ZohoMailServer().start();
    }
}
