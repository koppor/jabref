package org.jabref.http.server;

import org.jabref.http.MediaType;

import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LibraryResourceTest extends ServerTest {

    @Override
    protected jakarta.ws.rs.core.Application configure() {
        ResourceConfig resourceConfig = new ResourceConfig(LibraryResource.class);
        addPreferencesToResourceConfig(resourceConfig);
        return resourceConfig.getApplication();
    }

    @Test
    void initialData() throws Exception {
        assertEquals("""
                @Misc{Author2023test,
                  author = {Demo Author},
                  title  = {Demo Title},
                  year   = {2023},
                }

                @Comment{jabref-meta: databaseType:bibtex;}
                """, target("/libraries/" + ServerTest.idOfGeneralServerTestBib()).request(MediaType.BIBTEX).get(String.class));
    }
}
