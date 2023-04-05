package org.jabref.http.server;

import java.util.Set;

import jakarta.ws.rs.ApplicationPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationPath("/")
public class Application extends jakarta.ws.rs.core.Application {

    private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);

    @Override
    public Set<Class<?>> getClasses() {
        return Set.of(RootResource.class);
    }
}
