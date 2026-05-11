package sd2526.trab.impl.grpc.servers;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.List;
import java.util.logging.Logger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;

import io.grpc.Server;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import sd2526.trab.impl.discovery.Discovery;
import sd2526.trab.impl.java.servers.AbstractServer;
import sd2526.trab.impl.utils.IP;

public abstract class AbstractGrpcServer extends AbstractServer {

    private static final String SERVER_BASE_URI = "grpc://%s:%s%s";

    private static final String GRPC_CTX = "/grpc";

    protected final Server server;

    protected AbstractGrpcServer(Logger log, String service, int port) throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException, SSLException {
        super(log, service, String.format(SERVER_BASE_URI, IP.hostAddress(), port, GRPC_CTX));

        String keyStoreFilename = System.getProperty("javax.net.ssl.keyStore");
        String keyStorePassword = System.getProperty("javax.net.ssl.keyStorePassword");
        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        try(FileInputStream input = new FileInputStream(keyStoreFilename)) {
            keystore.load(input, keyStorePassword.toCharArray());
        } catch (IOException ex) {
            System.getLogger(AbstractGrpcServer.class.getName()).log(System.Logger.Level.ERROR, "Failed to load key store", ex);
        } catch (Exception ex) {
            System.getLogger(AbstractGrpcServer.class.getName()).log(System.Logger.Level.ERROR, "Failed to initialize key store", ex);
        }
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
        KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keystore, keyStorePassword.toCharArray());
        SslContext context = GrpcSslContexts.configure(
        SslContextBuilder.forServer(keyManagerFactory)
        ).build();
        
        var builder = NettyServerBuilder.forPort(port).sslContext(context);
        for (var s : controllers(super.serverURI)) {
            builder.addService(s);
        }

        this.server = builder.build();
    }

    protected abstract List<GrpcController> controllers(String uri);

    protected void start() throws IOException {

        Discovery.getInstance().announce(serviceName(), super.serverURI);

        Log.info(String.format("%s gRPC Server ready @ %s\n", service, serverURI));

        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("*** shutting down gRPC server since JVM is shutting down");
            server.shutdownNow();
            System.err.println("*** server shut down");
        }));
    }

}
