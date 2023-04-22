package org.jabref.http.server;

import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpdatesResourceTest extends ServerTest {

    @Override
    protected jakarta.ws.rs.core.Application configure() {
        ResourceConfig resourceConfig = new ResourceConfig(UpdatesResource.class);
        addPreferencesToResourceConfig(resourceConfig);
        addGsonToResourceConfig(resourceConfig);
        return resourceConfig.getApplication();
    }

    @Test
    void initialData() {
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
                ]""", target("/updates").queryParam("lastUpdate", "0").request().get(String.class));
    }
}
