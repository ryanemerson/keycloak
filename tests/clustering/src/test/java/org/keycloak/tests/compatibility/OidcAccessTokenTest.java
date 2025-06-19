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
import org.keycloak.testframework.server.KeycloakServerConfig;
import org.keycloak.testframework.server.KeycloakServerConfigBuilder;
import org.keycloak.testsuite.util.oauth.AccessTokenResponse;

@KeycloakIntegrationTest(config = OidcAccessTokenTest.ServerConfig.class)
public class OidcAccessTokenTest {

    @InjectUser(config = OAuthUserConfig.class)
    ManagedUser user;

    @InjectLoadBalancer
    LoadBalancer loadBalancer;

    @InjectOAuthClient
    OAuthClient oAuthClient;

    @Test
    public void testAccessTokenRefresh() {
        oAuthClient.baseUrl(loadBalancer.node(0).getBase());
        AccessTokenResponse accessTokenResponse = oAuthClient.doPasswordGrantRequest(user.getUsername(), user.getPassword());

        oAuthClient.baseUrl(loadBalancer.node(1).getBase());
        AccessTokenResponse refreshResponse = oAuthClient.doRefreshTokenRequest(accessTokenResponse.getRefreshToken());
        Assertions.assertTrue(refreshResponse.isSuccess());
        Assertions.assertNotEquals(accessTokenResponse.getAccessToken(), refreshResponse.getAccessToken());
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
