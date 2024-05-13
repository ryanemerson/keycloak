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

import static org.infinispan.client.rest.RestResponse.NOT_FOUND;
import static org.infinispan.client.rest.RestResponse.OK;
import static org.keycloak.connections.infinispan.InfinispanConnectionProvider.USER_SESSION_CACHE_NAME;

import java.io.IOException;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.security.auth.callback.CallbackHandler;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.configuration.Configuration;
import org.infinispan.client.hotrod.configuration.ServerConfiguration;
import org.infinispan.client.hotrod.security.BasicCallbackHandler;
import org.infinispan.client.rest.RestClient;
import org.infinispan.client.rest.RestResponse;
import org.infinispan.client.rest.configuration.RestClientConfigurationBuilder;
import org.infinispan.util.concurrent.CompletionStages;
import org.jboss.logging.Logger;
import org.keycloak.health.LoadBalancerCheckProvider;
import org.keycloak.health.xsite.XSiteCheckProvider;

public class InfinispanMultiSiteXSiteCheckProvider implements XSiteCheckProvider {
    private static final Logger LOG = Logger.getLogger(InfinispanMultiSiteXSiteCheckProvider.class);
    private final InfinispanConnectionProvider connectionProvider;

    public InfinispanMultiSiteXSiteCheckProvider(InfinispanConnectionProvider connectionProvider) {
        Objects.requireNonNull(connectionProvider, "connectionProvider");
        this.connectionProvider = connectionProvider;
    }

    /**
     * Non-blocking check if all caches and their persistence are available.
     * <p />
     * In a situation where any cache's remote cache is unreachable, this will report the "down" to the caller.
     * When the remote cache is down, it assumes that it is down for all Keycloak nodes in this site, all incoming
     * requests are likely to fail and that a loadbalancer should send traffic to the other site that might be healthy.
     * <p />
     * This code is non-blocking as the embedded Infinispan checks the connection to the remote store periodically
     * in the background (default: every second).
     * See {@link LoadBalancerCheckProvider#isDown()} to read more why this needs to be non-blocking.
     *
     * @return true if the component is down/unhealthy, false otherwise
     */
    @Override
    public boolean isDown(String site) {
        String cacheName = USER_SESSION_CACHE_NAME;
        RemoteCache<?, ?> cache = connectionProvider.getRemoteCache(cacheName);
        if (cache == null) {
            LOG.debugf("Cache '%s' does not exist yet.", cacheName);
            return true;
        }

        Configuration config = cache.getRemoteCacheContainer().getConfiguration();

        RestClientConfigurationBuilder configurationBuilder = new RestClientConfigurationBuilder();
        // TODO? This will only work for DIGEST and PLAIN authentication
        CallbackHandler ch = config.security().authentication().callbackHandler();
        if (ch != null) {
            if (ch instanceof BasicCallbackHandler basicCallbackHandler) {
                configurationBuilder
                      .security()
                      .authentication()
                      .username(basicCallbackHandler.getUsername())
                      .password(new String(basicCallbackHandler.getPassword()));
            } else {
                throw new IllegalArgumentException("Unexpect CallbackHandler " + ch.getClass().getName());
            }
        }

        if (config.security().ssl().enabled()) {
            try {
                // TODO handle his better!
                // Always trust the Infinispan server for now
                configurationBuilder.security().ssl().sslContext(SSLContext.getDefault()).trustManagers(new TrustManager[]{new ZeroSecurityTrustManager()});
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }

        ServerConfiguration serverCfg = config.servers().get(0);
        configurationBuilder.addServer()
              .host(serverCfg.host())
              .port(serverCfg.port());

        // TODO how to make non-blocking?
        // Periodic site check in the background?
        try (RestClient restClient = RestClient.forConfiguration(configurationBuilder.build());
             RestResponse rsp = CompletionStages.await(restClient.cacheManager("default").backupStatus(site))) {
            int status = rsp.getStatus();
            switch (status) {
                case OK:
                    String body = rsp.getBody();
                    if (body.contains("offline")) {
                        LOG.debugf("Site '%s' is down: %s", site, body);
                        return true;
                    }
                    return false;
                case NOT_FOUND:
                    LOG.debugf("Site '%s' NOT FOUND", site);
                    return true;
                default:
                    LOG.debugf("Site '%s' marked as down, unexpected response %d", site, status);
                    return true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException | IOException e) {
            LOG.debugf(e, "Unable to determine Site '%s' status", site);
            return true;
        }
    }

    @Override
    public void close() {
    }

    static class ZeroSecurityTrustManager extends X509ExtendedTrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {

        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {

        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {

        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {

        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {

        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
