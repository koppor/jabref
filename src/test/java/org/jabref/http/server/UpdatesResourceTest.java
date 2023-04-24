package org.jabref.http.server;

import org.glassfish.jersey.server.ResourceConfig;
import org.jabref.http.sync.state.SyncState;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.metadata.MetaData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpdatesResourceTest extends ServerTest {

    private final String path = "/li<axSXSXXSSSSAAbraries/" + TestBibFile.GENERAL_SERVER_TEST.id + "/updates";

    BibDatabaseContext bibDatabaseContext = new BibDatabaseContext(new BibDatabase(), new MetaData());

    SyncState syncState = new SyncState(bibDatabaseContext);

    @Override
    protected jakarta.ws.rs.core.Application configure() {
        ResourceConfig resourceConfig = new ResourceConfig(UpdatesResource.class);
        addPreferencesToResourceConfig(resourceConfig);
        addGsonToResourceConfig(resourceConfig);
        return resourceConfig.getApplication();
    }

    @Test
    void noLastUpdateSupplied() {
        assertEquals("[]", target(path).request().get(String.class));
    }

    @Test
    void initialData() {
        assertEquals("[]", target(path).queryParam("lastUpdate", "0").request().get(String.class));
    }

    @Test
    void twoVersions() {
        BibEntry entryE1V1 = new BibEntry().withCitationKey("e1.v1").withSharedBibEntryData(1, 1).withChanged(true);
        BibEntry entryE1V2 = new BibEntry().withCitationKey("e1.v2").withSharedBibEntryData(1, 2).withChanged(true);
        BibEntry entryE2V1 = new BibEntry().withCitationKey("e2.v1").withSharedBibEntryData(2, 1).withChanged(true);
        bibDatabaseContext.getDatabase().insertEntries(entryE1V2, entryE2V1);
        syncState.putEntry(
                1, entryE1V1);
        syncState.putEntry(
                1, entryE2V1);
        syncState.putEntry(
                2, entryE1V2);
        assertEquals("""
                [
                   {
                     "sharingMetadata": {
                       "sharedID": 1,
                       "version": 2
                     },
                     "userComments": "",
                     "citationKey": "e1.v2",
                     "bibtex": "@Misc{e1.v2,\\n}\\n"
                   },
                   {
                     "sharingMetadata": {
                       "sharedID": 2,
                       "version": 1
                     },
                     "userComments": "",
                     "citationKey": "e2.v1",
                     "bibtex": "@Misc{e2.v1,\\n}\\n"
                   }
                 ]""", target(path).queryParam("lastUpdate", "0").request().get(String.class));
    }
}
