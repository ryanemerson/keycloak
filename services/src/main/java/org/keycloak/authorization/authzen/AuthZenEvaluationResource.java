/*
 * Copyright 2025 Red Hat, Inc. and/or its affiliates
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
package org.keycloak.authorization.authzen;

import java.util.Collection;
import java.util.List;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.keycloak.authorization.AuthorizationProvider;
import org.keycloak.authorization.common.DefaultEvaluationContext;
import org.keycloak.authorization.common.KeycloakIdentity;
import org.keycloak.authorization.model.Resource;
import org.keycloak.authorization.model.ResourceServer;
import org.keycloak.authorization.permission.ResourcePermission;
import org.keycloak.authorization.store.ResourceStore;
import org.keycloak.authorization.store.StoreFactory;
import org.keycloak.authorization.util.Tokens;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.idm.authorization.Permission;
import org.keycloak.util.JsonSerialization;

public class AuthZenEvaluationResource {

    private final KeycloakSession session;

    public AuthZenEvaluationResource(KeycloakSession session) {
        this.session = session;
    }

    @POST
    @Path("access/v1/evaluation")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response evaluate(String requestBody) {
        try {
            AccessToken token = Tokens.getAccessToken(session);
            if (token == null) {
                throw new NotAuthorizedException("Bearer");
            }

            AuthZen.EvaluationRequest request;
            try {
                request = JsonSerialization.readValue(requestBody, AuthZen.EvaluationRequest.class);
            } catch (Exception e) {
                throw new BadRequestException("Invalid JSON request body");
            }

            AuthorizationProvider authorization = session.getProvider(AuthorizationProvider.class);
            StoreFactory storeFactory = authorization.getStoreFactory();

            ClientModel client = session.getContext().getRealm().getClientByClientId(token.getIssuedFor());
            ResourceServer resourceServer = storeFactory.getResourceServerStore().findByClient(client);
            if (resourceServer == null) {
                throw new BadRequestException("Client is not configured as a resource server");
            }

            ResourceStore resourceStore = storeFactory.getResourceStore();
            Resource resource = resourceStore.findByName(resourceServer, request.resource().id());
            if (resource == null) {
                return Response.ok(new AuthZen.EvaluationResponse(false)).build();
            }

            KeycloakIdentity identity = new KeycloakIdentity(session, token);
            DefaultEvaluationContext context = new DefaultEvaluationContext(identity, session);
            ResourcePermission permission = new ResourcePermission(resource, resource.getScopes(), resourceServer);

            Collection<Permission> granted = authorization.evaluators()
                  .from(List.of(permission), context)
                  .evaluate(resourceServer, null);

            boolean decision = !granted.isEmpty();
            return Response.ok(new AuthZen.EvaluationResponse(decision)).build();
        } catch (Exception e) {
            throw e;
        }
    }
}
