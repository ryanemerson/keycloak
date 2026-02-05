package org.keycloak.db.compatibility.verifier;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

record KeycloakMigrationParser(ClassLoader classLoader, String packageName) {
    Set<Migration> discoverAllMigrations() {
        File directory = getPackage();
        if (directory != null && directory.exists()) {
            return Stream.of(directory.listFiles())
                  .filter(file -> file.getName().endsWith(".class"))
                  .map(file -> packageName + '.' + file.getName().replace(".class", ""))
                  .map(Migration::new)
                  .collect(Collectors.toSet());
        }
        return Set.of();
    }

    private File getPackage() {
        if (packageName == null) {
            return null;
        }

        String path = packageName.replace('.', '/');
        URL resource = classLoader.getResource(path);
        if (resource == null) {
            throw new IllegalStateException("Unable to load package '%s'".formatted(packageName));
        }

        File directory;
        try {
            directory = new File(resource.toURI());
        } catch (URISyntaxException e) {
            // Should never happen
            throw new IllegalStateException(e);
        }
        return directory;
    }
}
