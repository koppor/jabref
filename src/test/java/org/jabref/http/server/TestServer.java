package org.jabref.http.server;

import java.util.concurrent.CountDownLatch;

public class TestServer {
    public static void main(final String[] args) throws InterruptedException {
        Server.startServer(new CountDownLatch(1));
        Thread.currentThread().join();
    }
}
