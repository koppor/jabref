package org.jabref.http.server;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerTest {
    private static HttpClient httpClient;

    public static void main(final String[] args) throws InterruptedException {
        Server.startServer(new CountDownLatch(1));
        Thread.currentThread().join();
    }

    @BeforeAll
    public static void startServer() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Server.startServer(countDownLatch);
        httpClient = HttpClient.newHttpClient();
        countDownLatch.await();
    }

    @AfterAll
    public static void stopServer() {
        Server.stopServer();
    }

    @Test
    void initialData() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                                         .uri(URI.create("http://localhost:8080/updates?lastUpdate=0"))
                                         .GET()
                                         .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals("""
                [
                  {
                    "sharingMetadata": {
                      "sharedID": 1,
                      "version": 2
                    },
                    "type": "Misc",
                    "citationKey": "e1.v2",
                    "content": {},
                    "userComments": ""
                  },
                  {
                    "sharingMetadata": {
                      "sharedID": 2,
                      "version": 1
                    },
                    "type": "Misc",
                    "citationKey": "e2.v1",
                    "content": {},
                    "userComments": ""
                  }
                ]""", response.body());
    }
}
