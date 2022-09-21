package org.jabref.logic.shared.restserver.rest.slr;

import java.io.IOException;
import java.util.List;

import org.jabref.logic.importer.ParseException;
import org.jabref.logic.shared.restserver.core.properties.ServerPropertyService;
import org.jabref.logic.shared.restserver.core.repository.StudyService;
import org.jabref.logic.shared.restserver.rest.base.Library;
import org.jabref.logic.shared.restserver.rest.model.CrawlStatus;
import org.jabref.logic.shared.restserver.rest.model.StudyDTO;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("studies")
public class Studies {
    private static final Logger LOGGER = LoggerFactory.getLogger(Studies.class);
    private final StudyService studyService;

    public Studies() {
        studyService = StudyService.getInstance(ServerPropertyService.getInstance().getWorkingDirectory());
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getStudyNames() throws IOException {
        try {
            return studyService.getStudyNames();
        } catch (IOException e) {
            LOGGER.error("Error retrieving study names.", e);
            throw e;
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createStudy(StudyDTO study) throws IOException {
        try {
            if (studyService.studyExists(study.studyDefinition.getTitle())) {
                return Response.status(Response.Status.CONFLICT).entity("The given study name is already in use.").build();
            }
            studyService.createStudy(study.studyDefinition);
            return Response.ok()
                           .build();
        } catch (IOException e) {
            LOGGER.error("Error retrieving study names.", e);
            throw e;
        }
    }

    // TODO: How can we remove the /results
    @Path("{studyName}/results")
    public Library getStudyResults(@PathParam("studyName") String studyName) {
        return new Library(studyService.getStudyPath(studyName), "studyResult");
    }

    @DELETE
    @Path("{studyName}")
    public Response deleteStudy(@PathParam("studyName") String studyName) throws IOException {
        try {
            if (studyService.deleteStudy(studyName)) {
                return Response.ok()
                               .build();
            }
            return Response.status(Response.Status.NOT_FOUND)
                           .build();
        } catch (IOException e) {
            LOGGER.error("Error deleting study.", e);
            throw e;
        }
    }

    @POST
    // Workaround to fix Tomcat CORS Filter issue with missing media type header: https://stackoverflow.com/questions/59204624/cors-failing-when-post-request-has-no-body-and-server-response-is-a-403-forbidde
    @Consumes(MediaType.TEXT_PLAIN)
    @Path("{studyName}/crawl")
    public void crawlStudy(@PathParam("studyName") String studyName, String unused) throws IOException, ParseException {
        try {
            // Note: This only starts a new crawl if no other crawl is currently running for this study
            studyService.startCrawl(studyName);
        } catch (IOException | ParseException e) {
            LOGGER.error("Error during crawling", e);
            throw e;
        }
    }

    @GET
    @Path("{studyName}/crawl")
    @Produces(MediaType.APPLICATION_JSON)
    public CrawlStatus getCrawlStatus(@PathParam("studyName") String studyName) {
        return new CrawlStatus(studyService.isCrawlRunning(studyName));
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{studyName}/studyDefinition")
    public StudyDTO getStudyDefinition(@PathParam("studyName") String studyName) throws IOException {
        try {
            return new StudyDTO(studyService.getStudyDefinition(studyName));
        } catch (IOException e) {
            LOGGER.error("Error retrieving study definition.", e);
            throw e;
        }
    }
}
