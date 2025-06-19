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

package org.keycloak.tests.compatibility;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.keycloak.testframework.annotations.InjectLoadBalancer;
import org.keycloak.testframework.annotations.InjectUser;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.clustering.LoadBalancer;
import org.keycloak.testframework.oauth.OAuthClient;
import org.keycloak.testframework.oauth.annotations.InjectOAuthClient;
import org.keycloak.testframework.realm.ManagedUser;
import org.keycloak.testsuite.util.oauth.AuthorizationEndpointResponse;

@KeycloakIntegrationTest
public class OauthClientTest {

    @InjectUser(config = OAuthUserConfig.class)
    ManagedUser user;

    @InjectLoadBalancer
    LoadBalancer loadBalancer;

    @InjectOAuthClient
    OAuthClient oAuthClient;

    @Test
    public void testLoginLogout() {
        oAuthClient.baseUrl(loadBalancer.node(0).getBase());
        AuthorizationEndpointResponse response = oAuthClient.doLogin(user.getUsername(), user.getPassword());
        Assertions.assertTrue(response.isRedirected());

        oAuthClient.baseUrl(loadBalancer.node(1).getBase());
        String idTokenHint = oAuthClient.doAccessTokenRequest(response.getCode()).getIdToken();
        oAuthClient.logoutForm().idTokenHint(idTokenHint).open();
    }
}
