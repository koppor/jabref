package org.jabref.http.server;

import java.util.Set;

import org.jabref.preferences.PreferenceServiceFactory;

import jakarta.inject.Inject;
import jakarta.ws.rs.ApplicationPath;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;

@ApplicationPath("/")
public class Application extends jakarta.ws.rs.core.Application {

    @Inject
    ServiceLocator serviceLocator;

    @Override
    public Set<Class<?>> getClasses() {
        initialize();
        return Set.of(RootResource.class, LibrariesResource.class, LibraryResource.class);
    }

    /**
     * Separate initialization method, because @Inject does not support injection at the constructor
     */
    private void initialize() {
        ServiceLocatorUtilities.addFactoryConstants(serviceLocator, new PreferenceServiceFactory());
    }
}
