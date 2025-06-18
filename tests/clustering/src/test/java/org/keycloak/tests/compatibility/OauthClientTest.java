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

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.testframework.annotations.InjectHttpClient;
import org.keycloak.testframework.annotations.InjectLoadBalancer;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.InjectUser;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.clustering.LoadBalancer;
import org.keycloak.testframework.oauth.DefaultOAuthClientConfiguration;
import org.keycloak.testframework.oauth.OAuthClient;
import org.keycloak.testframework.oauth.TestApp;
import org.keycloak.testframework.oauth.annotations.InjectTestApp;
import org.keycloak.testframework.realm.ClientConfigBuilder;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.realm.ManagedUser;
import org.keycloak.testframework.ui.annotations.InjectWebDriver;
import org.keycloak.testframework.util.ApiUtil;
import org.keycloak.testsuite.util.oauth.AuthorizationEndpointResponse;
import org.openqa.selenium.WebDriver;

@KeycloakIntegrationTest
public class OauthClientTest {

    @InjectLoadBalancer
    LoadBalancer loadBalancer;

    @InjectHttpClient
    HttpClient httpClient;

    @InjectRealm
    ManagedRealm realm;

    @InjectUser(config = OAuthUserConfig.class)
    ManagedUser user;

    @InjectTestApp
    TestApp testApp;

    @InjectWebDriver
    WebDriver webDriver;

    @Test
    public void testLoginLogout() {
        AuthorizationEndpointResponse response = oauth(0).doLogin(user.getUsername(), user.getPassword());
        Assertions.assertTrue(response.isRedirected());

        String idTokenHint = oauth(1).doAccessTokenRequest(response.getCode()).getIdToken();
        oauth(1).logoutForm().idTokenHint(idTokenHint).open();
    }

    // TODO replace with just setting baseUrl on OAuthClient?
    private OAuthClient oauth(int index) {
        String baseUrl = loadBalancer.node(index).getBase();

        String redirectUri = testApp.getRedirectionUri();
        ClientRepresentation testAppClient = new DefaultOAuthClientConfiguration().configure(ClientConfigBuilder.create())
              .redirectUris(redirectUri)
              .build();

        String clientId = testAppClient.getClientId();
        String clientSecret = testAppClient.getSecret();
        ApiUtil.handleCreatedResponse(realm.admin().clients().create(testAppClient));

        OAuthClient oAuthClient = new OAuthClient(baseUrl, (CloseableHttpClient) httpClient, webDriver);
        oAuthClient.config()
              .realm(realm.getName())
              .client(clientId, clientSecret)
              .redirectUri(redirectUri)
              .scope("openid profile");
        return oAuthClient;
    }
}
