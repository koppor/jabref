package org.jabref.http.server;

import java.net.URI;
import java.util.concurrent.CountDownLatch;

import org.jabref.model.entry.BibEntry;
import org.jabref.http.sync.state.SyncState;

import jakarta.ws.rs.SeBootstrap;

public class Server {

    private static SeBootstrap.Instance serverInstance;

    public static void main(final String[] args) throws InterruptedException {
        startServer(new CountDownLatch(1));
        addDummyDataToSyncState();
        Thread.currentThread().join();
    }

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

    static void addDummyDataToSyncState() {
        BibEntry entryE1V1 = new BibEntry().withCitationKey("e1.v1").withSharedBibEntryData(1, 1);
        BibEntry entryE1V2 = new BibEntry().withCitationKey("e1.v2").withSharedBibEntryData(1, 2);
        BibEntry entryE2V1 = new BibEntry().withCitationKey("e2.v1").withSharedBibEntryData(2, 1);

        SyncState.INSTANCE.putEntry(
                1, entryE1V1);
        SyncState.INSTANCE.putEntry(
                1, entryE2V1);
        SyncState.INSTANCE.putEntry(
                2, entryE1V2);
    }
}
