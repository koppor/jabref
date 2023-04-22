package org.jabref.http.server;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.integration.OpenApiContextLocator;
import io.swagger.v3.oas.models.OpenAPI;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/")
public class RootResource {
    @GET
    @Produces(MediaType.TEXT_HTML)
    public String get() {
        return """
                <html>
                <body>
                <p>
                  JabRef http API runs. Please navigate to <a href="libraries">libraries</a>.
                </p>
                </body>
                """;
    }

    @GET
    @Path("openapi.json")
    @Produces(MediaType.APPLICATION_JSON)
    public String getOpenApiJson() {
        OpenAPI openAPI = OpenApiContextLocator.getInstance().getOpenApiContext("org.jabref.sync").read();
        return Json.pretty(openAPI);
    }
}
