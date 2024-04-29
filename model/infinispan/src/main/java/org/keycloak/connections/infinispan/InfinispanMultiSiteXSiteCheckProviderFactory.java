/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.connections.infinispan;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.common.Profile;
import org.keycloak.health.xsite.XSiteCheckProvider;
import org.keycloak.health.xsite.XSiteCheckProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.EnvironmentDependentProviderFactory;


public class InfinispanMultiSiteXSiteCheckProviderFactory implements XSiteCheckProviderFactory, EnvironmentDependentProviderFactory {

    private XSiteCheckProvider InfinispanMultiSiteXSiteCheckProvider;
    private static final XSiteCheckProvider ALWAYS_HEALTHY = new XSiteCheckProvider() {
        @Override public boolean isDown(String site) { return false; }
        @Override public void close() {}
    };
    private static final Logger LOG = Logger.getLogger(InfinispanMultiSiteXSiteCheckProviderFactory.class);

    @Override
    public XSiteCheckProvider create(KeycloakSession session) {
        if (InfinispanMultiSiteXSiteCheckProvider == null) {
            InfinispanConnectionProvider infinispanConnectionProvider = session.getProvider(InfinispanConnectionProvider.class);
            if (infinispanConnectionProvider == null) {
                LOG.warn("InfinispanConnectionProvider is not available. Load balancer check will be always healthy for Infinispan.");
                InfinispanMultiSiteXSiteCheckProvider = ALWAYS_HEALTHY;
            } else {
                InfinispanMultiSiteXSiteCheckProvider = new InfinispanMultiSiteXSiteCheckProvider(infinispanConnectionProvider);
            }
        }
        return InfinispanMultiSiteXSiteCheckProvider;
    }

    @Override
    public void init(Config.Scope config) {

    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {

    }

    @Override
    public void close() {

    }

    @Override
    public String getId() {
        return "infinispan-multisite";
    }

    @Override
    public boolean isSupported(Config.Scope config) {
        return Profile.isFeatureEnabled(Profile.Feature.MULTI_SITE);
    }
}
