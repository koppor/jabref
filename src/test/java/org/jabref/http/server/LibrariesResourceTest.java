package org.jabref.http.server;

import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LibrariesResourceTest extends ServerTest {

    @Override
    protected jakarta.ws.rs.core.Application configure() {
        ResourceConfig resourceConfig = new ResourceConfig(LibrariesResource.class);
        addPreferencesToResourceConfig(resourceConfig);
        return resourceConfig.getApplication();
    }

    @Test
    void oneTestLibrary() throws Exception {
        assertEquals("[\"" + ServerTest.idOfGeneralServerTestBib() + "\"]", target("/libraries").request().get(String.class));
    }
}
