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
import org.keycloak.testframework.server.KeycloakServerConfig;
import org.keycloak.testframework.server.KeycloakServerConfigBuilder;
import org.keycloak.testframework.ui.annotations.InjectWebDriver;
import org.keycloak.testframework.util.ApiUtil;
import org.keycloak.testsuite.util.oauth.AccessTokenResponse;
import org.openqa.selenium.WebDriver;

@KeycloakIntegrationTest(config = OidcAccessTokenTest.ServerConfig.class)
public class OidcAccessTokenTest {

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
    public void testAccessTokenRefresh() {
        AccessTokenResponse accessTokenResponse = oauth(0).doPasswordGrantRequest(user.getUsername(), user.getPassword());

        AccessTokenResponse refreshResponse = oauth(1).doRefreshTokenRequest(accessTokenResponse.getRefreshToken());
        Assertions.assertTrue(refreshResponse.isSuccess());
        Assertions.assertNotEquals(accessTokenResponse.getAccessToken(), refreshResponse.getAccessToken());
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

    public static class ServerConfig implements KeycloakServerConfig {
        @Override
        public KeycloakServerConfigBuilder configure(KeycloakServerConfigBuilder config) {
            return config.option("hostname", "https://keycloak.org")
                  // Require to prevent "Invalid token issuer when interacting with tokens from distinct nodes in the cluster
                  .option("hostname-backchannel-dynamic", "true");
        }
    }
}
