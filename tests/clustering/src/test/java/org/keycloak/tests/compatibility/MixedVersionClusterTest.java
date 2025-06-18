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

import java.time.Duration;
import java.util.Date;
import java.util.List;

import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.cookie.Cookie;
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
import org.keycloak.testframework.realm.UserConfig;
import org.keycloak.testframework.realm.UserConfigBuilder;
import org.keycloak.testframework.ui.annotations.InjectPage;
import org.keycloak.testframework.ui.annotations.InjectWebDriver;
import org.keycloak.testframework.ui.page.LoginPage;
import org.keycloak.testframework.ui.page.WelcomePage;
import org.keycloak.testframework.util.ApiUtil;
import org.keycloak.testsuite.util.oauth.AccessTokenResponse;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

@KeycloakIntegrationTest
public class MixedVersionClusterTest {

    @InjectLoadBalancer
    LoadBalancer loadBalancer;

//    @InjectHttpClient(cookieStore = Cookies.class)
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

    @InjectPage
    LoginPage loginPage;

    @InjectPage
    WelcomePage welcomePage;

//    @Test
//    public void testUrls() {
//        // TODO annotation based to skip if running in non-clustered mode.
//        Assumptions.assumeFalse(webDriver instanceof HtmlUnitDriver);
//        Assumptions.assumeTrue(loadBalancer.clusterSize() == 2);
//        System.out.println("url0->" + loadBalancer.node(0).getBaseUrl());
//        System.out.println("url1->" + loadBalancer.node(1).getBaseUrl());
//        System.out.println("redirectUri->" + testApp.getRedirectionUri());
//        System.out.println("webdriver->" + webDriver.getCurrentUrl());
//        System.out.println("realm->" + realm.getBaseUrl());
//        System.out.println("loginPage->" + loginPage.getExpectedPageId());
//
//        webDriver.get(loadBalancer.node(0).getBaseUrl().toString() + "/admin/master/console");
//        assertOnAdminConsole();
//        System.out.println("webdriver->" + webDriver.getCurrentUrl());
//        System.out.println(webDriver.getPageSource());
//        loginPage.fillLogin("admin", "admin");
//        loginPage.submit();
////        welcomePage.fillRegistration("test-user@localhost", "password");
////        welcomePage.submit();
//
////        loginPage.waitForPage();
////        loginPage.fillLogin("test-user@localhost", "password");
////        loginPage.submit();
////        Thread.sleep(TimeUnit.MINUTES.toMillis(1));
//    }

    private void assertOnAdminConsole() {
        new WebDriverWait(webDriver, Duration.ofSeconds(10)).until(d -> webDriver.getTitle().equals("Keycloak Administration Console") || webDriver.getTitle().equals("Sign in to Keycloak"));
    }

//    @Test
//    public void testOIDCLoginLogout() {
//        // TODO make InjectOAuthClient cluster & LoadBalancer aware?
//        OAuthClient client1 = oAuthClient(0);
//        AuthorizationEndpointResponse rsp = client1.doLogin("test-user@localhost", "password");
//        String code = rsp.getCode();
//        System.out.println(code);
//    }

//    @Test
//    public void testLoginLogout() {
//        AuthorizationEndpointResponse response = oauth(0).doLogin(user.getUsername(), user.getPassword());
//        Assertions.assertTrue(response.isRedirected());
//
//        String idTokenHint = oauth(1).doAccessTokenRequest(response.getCode()).getIdToken();
//        oauth(1).logoutForm().idTokenHint(idTokenHint).open();
//    }

    @Test
    public void testRefresh() {
        AccessTokenResponse accessTokenResponse = oauth(0).doPasswordGrantRequest(user.getUsername(), user.getPassword());

        AccessTokenResponse refreshResponse = oauth(0).doRefreshTokenRequest(accessTokenResponse.getRefreshToken());
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

    public static class Cookies implements CookieStore {
        @Override
        public void addCookie(Cookie cookie) {
            System.out.println(cookie);
        }

        @Override
        public List<Cookie> getCookies() {
            return List.of();
        }

        @Override
        public boolean clearExpired(Date date) {
            return false;
        }

        @Override
        public void clear() {
            System.out.println("clear cookies");
        }
    }

    public static class OAuthUserConfig implements UserConfig {

        @Override
        public UserConfigBuilder configure(UserConfigBuilder user) {
            return user.username("myuser").name("First", "Last")
                  .email("test@local")
                  .password("password");
        }
    }
}
