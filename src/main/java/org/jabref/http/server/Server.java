package org.jabref.http.server;

import java.net.URI;
import java.util.concurrent.CountDownLatch;

import jakarta.ws.rs.SeBootstrap;

public class Server {

    private static SeBootstrap.Instance serverInstance;

    static void startServer(CountDownLatch latch) {
        SeBootstrap.start(Application.class).thenAccept(instance -> {
            instance.stopOnShutdown(stopResult ->
                    System.out.printf("Stop result: %s [Native stop result: %s].%n", stopResult,
                            stopResult.unwrap(Object.class)));
            final URI uri = instance.configuration().baseUri();
            System.out.printf("Instance %s running at %s [Native handle: %s].%n", instance, uri,
                    instance.unwrap(Object.class));
            System.out.println("Send SIGKILL to shutdown.");
            serverInstance = instance;
            latch.countDown();
        });
    }

    static void stopServer() {
        serverInstance.stop();
    }
}
