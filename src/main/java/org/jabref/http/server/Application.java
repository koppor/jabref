package org.jabref.http.server;

import java.util.Set;

import io.swagger.v3.jaxrs2.Reader;
import io.swagger.v3.jaxrs2.integration.JaxrsApplicationScanner;
import io.swagger.v3.oas.integration.GenericOpenApiContextBuilder;
import io.swagger.v3.oas.integration.OpenApiConfigurationException;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import jakarta.ws.rs.ApplicationPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationPath("/")
public class Application extends jakarta.ws.rs.core.Application {

    private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);

    public Application() {
        super();
        JaxrsApplicationScanner scanner = new JaxrsApplicationScanner();
        scanner.setApplication(this);
        Reader reader = new Reader(new OpenAPI());
        OpenAPI openAPI = reader.read(scanner.classes());
        Info info = new Info()
                .title("JabRef http API")
                .description("This is a sample JabDrive synchronization server.")
                .version("0.1.0")
                // .termsOfService("http://swagger.io/terms/")
                .contact(new Contact()
                        .email("jabdrive@jabref.org"))
                .license(new License()
                        .name("MIT")
                        .url("https://github.com/JabRef/jabref/blob/main/LICENSE.md"));
        openAPI.info(info);

        SwaggerConfiguration oasConfig = new SwaggerConfiguration()
                .openAPI(openAPI)
                .prettyPrint(true);

        try {
            new GenericOpenApiContextBuilder()
                    .resourceClasses(Set.of("org.jabref.testutils.interactive.sync.server.MweSyncRootResource"))
                    .openApiConfiguration(oasConfig)
                    .ctxId("org.jabref.sync")
                    .buildContext(true);
        } catch (OpenApiConfigurationException e) {
            LOGGER.error("Error in OpenAPI configuration", e);
        }
    }

    @Override
    public Set<Class<?>> getClasses() {
        return Set.of(RootResource.class);
    }
}
