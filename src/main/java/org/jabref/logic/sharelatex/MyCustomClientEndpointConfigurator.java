package org.jabref.logic.sharelatex;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.HandshakeResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyCustomClientEndpointConfigurator extends ClientEndpointConfig.Configurator {

    private static final Logger LOGGER = LoggerFactory.getLogger(MyCustomClientEndpointConfigurator.class);
    private final String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.16; rv:84.0) Gecko/20100101 Firefox/84.0";
    private final String serverOrigin;
    private final Map<String, String> cookies;

    public MyCustomClientEndpointConfigurator(String serverOrigin, Map<String, String> cookies) {
        super();
        this.serverOrigin = serverOrigin;
        this.cookies = cookies;
    }

    @Override
    public void beforeRequest(Map<String, List<String>> headers) {
        headers.put("User-Agent", Arrays.asList(userAgent));
        headers.put("Origin", Arrays.asList(serverOrigin));

        String result = cookies.entrySet()
                .stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("; "));
        headers.put("Cookie", Arrays.asList(result));
    }

    @Override
    public void afterResponse(HandshakeResponse handshakeResponse) {
        final Map<String, List<String>> headers = handshakeResponse.getHeaders();
        LOGGER.debug("headers {}", headers);
    }
}
