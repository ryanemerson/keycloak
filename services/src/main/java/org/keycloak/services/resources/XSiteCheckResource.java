/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
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
package org.keycloak.services.resources;

import java.util.Set;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestPath;
import org.keycloak.health.xsite.XSiteCheckProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.utils.MediaType;

import io.smallrye.common.annotation.NonBlocking;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Determine whether x-site connections are online so that a load-balancer can manage traffic accordingly.
 * <p>
 * This is non-blocking, so that the load balancer can still retrieve the status even if the Keycloak instance is
 * trying to withstand a high load. See {@link XSiteCheckProvider#isDown(String)} for a longer explanation.
 *
 * @author <a href="mailto:remerson@redhat.com">Ryan Emerson</a>
 */
@Provider
@Path("/xsite-check/{site}")
@NonBlocking
public class XSiteCheckResource {

    protected static final Logger logger = Logger.getLogger(XSiteCheckResource.class);

    @Context
    KeycloakSession session;

    /**
     * Health check for an external Infinispan site in a multi-site setup.
     * <p />
     * While a loadbalancer will usually check for the returned status code, the additional text <code>UP</code> or <code>DOWN</down>
     * is returned for humans to see the status in the browser.
     * <p />
     * In contrast to other management endpoints of Quarkus, no information is returned to the caller about the internal state of Keycloak
     * as this endpoint might be publicly available from the internet and should return as little information as possible.
     *
     * @return HTTP status 503 and DOWN when down, and HTTP status 200 and UP when up.
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN_UTF_8)
    public Response getXSiteStatus(@RestPath String site) {
        Set<XSiteCheckProvider> healthStatusProviders = session.getAllProviders(XSiteCheckProvider.class);
        if (healthStatusProviders.stream().anyMatch(p -> p.isDown(site))) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity("DOWN").build();
        } else {
            return Response.ok().entity("UP").build();
        }
    }
}
