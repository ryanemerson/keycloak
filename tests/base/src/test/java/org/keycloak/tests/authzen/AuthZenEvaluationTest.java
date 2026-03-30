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
package org.keycloak.tests.authzen;

import java.io.IOException;
import java.util.Set;

import jakarta.ws.rs.core.Response;

import org.keycloak.admin.client.resource.AuthorizationResource;
import org.keycloak.http.simple.SimpleHttp;
import org.keycloak.http.simple.SimpleHttpResponse;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.authorization.ResourcePermissionRepresentation;
import org.keycloak.representations.idm.authorization.ResourceRepresentation;
import org.keycloak.representations.idm.authorization.RolePolicyRepresentation;
import org.keycloak.testframework.annotations.InjectClient;
import org.keycloak.testframework.annotations.InjectKeycloakUrls;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.InjectSimpleHttp;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.annotations.TestSetup;
import org.keycloak.testframework.injection.LifeCycle;
import org.keycloak.testframework.oauth.OAuthClient;
import org.keycloak.testframework.oauth.annotations.InjectOAuthClient;
import org.keycloak.testframework.realm.ManagedClient;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.server.KeycloakUrls;
import org.keycloak.tests.authzen.AuthZenClient.EvaluationResult;
import org.keycloak.testsuite.util.oauth.AccessTokenResponse;

import org.apache.http.entity.StringEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KeycloakIntegrationTest(config = AuthZenServerConfig.class)
public class AuthZenEvaluationTest {

    @InjectRealm(config = AuthZenRealmConfig.class, lifecycle = LifeCycle.CLASS)
    ManagedRealm realm;

    @InjectClient(config = AuthZenClientConfig.class)
    ManagedClient client;

    @InjectOAuthClient
    OAuthClient oauth;

    @InjectSimpleHttp
    SimpleHttp simpleHttp;

    @InjectKeycloakUrls
    KeycloakUrls keycloakUrls;

    @TestSetup
    public void setup() {
        configureAuthorizationResources();
    }

    @Test
    public void testAdminUserAccessAdminResource() throws IOException {
        EvaluationResult result = authzenClient("admin-user", "password")
              .evaluate(AuthZenClient.evaluationRequest()
                    .subject("user", "admin-user")
                    .action("access")
                    .resource("endpoint", "/admin")
                    .build());

        assertEquals(200, result.statusCode());
        assertTrue(result.decision());
    }

    @Test
    public void testRegularUserDeniedAdminResource() throws IOException {
        EvaluationResult result = authzenClient("regular-user", "password")
              .evaluate(AuthZenClient.evaluationRequest()
                    .subject("user", "regular-user")
                    .action("access")
                    .resource("endpoint", "/admin")
                    .build());

        assertEquals(200, result.statusCode());
        assertFalse(result.decision());
    }

    @Test
    public void testAdminUserAccessUsersResource() throws IOException {
        EvaluationResult result = authzenClient("admin-user", "password")
              .evaluate(AuthZenClient.evaluationRequest()
                    .subject("user", "admin-user")
                    .action("access")
                    .resource("endpoint", "/users")
                    .build());

        assertEquals(200, result.statusCode());
        assertTrue(result.decision());
    }

    @Test
    public void testRegularUserAccessUsersResource() throws IOException {
        EvaluationResult result = authzenClient("regular-user", "password")
              .evaluate(AuthZenClient.evaluationRequest()
                    .subject("user", "regular-user")
                    .action("access")
                    .resource("endpoint", "/users")
                    .build());

        assertEquals(200, result.statusCode());
        assertTrue(result.decision());
    }

    @Test
    public void testInvalidJsonReturnsBadRequest() throws IOException {
        String url = realmUrl() + "/authzen/access/v1/evaluation";
        AccessTokenResponse tokenResponse = oauth
              .client(client.getClientId(), client.getSecret())
              .doPasswordGrantRequest("admin-user", "password");

        try (SimpleHttpResponse response = simpleHttp.doPost(url)
              .auth(tokenResponse.getAccessToken())
              .header("Content-Type", "application/json")
              .entity(new StringEntity("{invalid json"))
              .asResponse()) {
            assertEquals(400, response.getStatus());
        }
    }

    @Test
    public void testUnauthenticatedUserReturnsUnauthorized() throws IOException {
        String url = realmUrl() + "/authzen/access/v1/evaluation";

        try (SimpleHttpResponse response = simpleHttp.doPost(url)
              .header("Content-Type", "application/json")
              .json(AuthZenClient.evaluationRequest()
                    .subject("user", "admin-user")
                    .action("access")
                    .resource("endpoint", "/admin")
                    .build())
              .asResponse()) {
            assertEquals(401, response.getStatus());
        }
    }

    private String realmUrl() {
        return keycloakUrls.getBase() + "/realms/" + realm.getName();
    }

    private AuthZenClient.Builder authzenClient(String username, String password) {
        AccessTokenResponse tokenResponse = oauth
              .client(client.getClientId(), client.getSecret())
              .doPasswordGrantRequest(username, password);
        return AuthZenClient.create(simpleHttp, realmUrl())
              .accessToken(tokenResponse.getAccessToken());
    }

    private void configureAuthorizationResources() {
        AuthorizationResource authorization = client.admin().authorization();

        // Create the /admin resource
        ResourceRepresentation adminResource = ResourceRepresentation.create()
              .name("/admin")
              .build();
        try (Response response = authorization.resources().create(adminResource)) {
            assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        }

        // Create the /users resource
        ResourceRepresentation usersResource = ResourceRepresentation.create()
              .name("/users")
              .build();
        try (Response response = authorization.resources().create(usersResource)) {
            assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        }

        // Create a role policy that requires the "admin" realm role
        RoleRepresentation adminRole = realm.admin().roles().get("admin").toRepresentation();
        RolePolicyRepresentation adminRolePolicy = new RolePolicyRepresentation();
        adminRolePolicy.setName("Require Admin Role");
        adminRolePolicy.addRole(adminRole.getId());
        try (Response response = authorization.policies().role().create(adminRolePolicy)) {
            assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        }
        adminRolePolicy = authorization.policies().role().findByName("Require Admin Role");

        // Create an "always grant" policy for the /users resource
        org.keycloak.representations.idm.authorization.PolicyRepresentation alwaysGrantPolicy =
              new org.keycloak.representations.idm.authorization.PolicyRepresentation();
        alwaysGrantPolicy.setName("Always Grant");
        alwaysGrantPolicy.setType("always-grant");
        try (Response response = authorization.policies().create(alwaysGrantPolicy)) {
            assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        }
        alwaysGrantPolicy = authorization.policies().findByName("Always Grant");

        // Create permission: /admin resource requires admin role
        ResourcePermissionRepresentation adminPermission = ResourcePermissionRepresentation.create()
              .name("Admin Resource Permission")
              .resources(Set.of(authorization.resources().findByName("/admin").get(0).getId()))
              .policies(Set.of(adminRolePolicy.getId()))
              .build();
        try (Response response = authorization.permissions().resource().create(adminPermission)) {
            assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        }

        // Create permission: /users resource is open to all authenticated users
        ResourcePermissionRepresentation usersPermission = ResourcePermissionRepresentation.create()
              .name("Users Resource Permission")
              .resources(Set.of(authorization.resources().findByName("/users").get(0).getId()))
              .policies(Set.of(alwaysGrantPolicy.getId()))
              .build();
        try (Response response = authorization.permissions().resource().create(usersPermission)) {
            assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        }
    }
}
