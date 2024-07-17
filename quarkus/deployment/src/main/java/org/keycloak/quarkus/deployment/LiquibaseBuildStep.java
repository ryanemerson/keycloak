package org.keycloak.quarkus.deployment;

import io.quarkus.builder.item.EmptyBuildItem;

/**
 * A barrier build item that can be consumed by other build steps when Liquibase is required
 */
public class LiquibaseBuildStep extends EmptyBuildItem {
}
