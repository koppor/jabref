package org.jabref.http.server;

import org.jabref.preferences.PreferencesService;

import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LibrariesResourceTest extends JerseyTest {
    @Override
    protected jakarta.ws.rs.core.Application configure() {
        ResourceConfig resourceConfig = new ResourceConfig(LibrariesResource.class);
        resourceConfig.register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(ServerTest.preferencesService()).to(PreferencesService.class).ranked(2);
            }
        });
        return resourceConfig.getApplication();
    }

    @Test
    void initialData() throws Exception {
        assertEquals("[\"" + ServerTest.idOfGeneralServerTestBib() + "\"]", target("/libraries").request().get(String.class));
    }
}
