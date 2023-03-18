package org.jabref.logic.shared.restserver.rest;

import java.util.Set;

import org.jabref.logic.shared.restserver.rest.base.Accumulation;
import org.jabref.logic.shared.restserver.rest.base.Libraries;
import org.jabref.logic.shared.restserver.rest.base.Library;
import org.jabref.logic.shared.restserver.rest.slr.Studies;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.MediaType;

@ApplicationPath("/")
@Path("")
public class Root extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        return Set.of(Root.class, Libraries.class, Library.class, Studies.class, Accumulation.class);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String getText() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("""
                <html>
                <head>
                </head>
                <body>
                """);
        stringBuilder.append("<h1>JabRef REST-ful http API</h1>");
        stringBuilder.append("Use a JSON client and navigate to <a href=\"libraries/\">libraries</a>.");
        stringBuilder.append("""
                </body>
                </html>
                """);
        return stringBuilder.toString();
    }
}
