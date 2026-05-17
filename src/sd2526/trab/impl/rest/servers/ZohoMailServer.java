package sd2526.trab.impl.rest.servers;

import java.util.logging.Logger;

import org.glassfish.jersey.server.ResourceConfig;

public class ZohoMailServer extends AbstractRestServer {

    public static final int PORT = 5678;

    private static Logger Log = Logger.getLogger(ZohoMailServer.class.getName());

    ZohoMailServer() {
        super(Log, "ZohoMail", PORT);
    }

    @Override
    void registerResources(ResourceConfig config) {
        config.register(ZohoMailResource.class);
    }

    public static void main(String[] args) {
        new ZohoMailServer().start();
    }
}
