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
import java.util.HashMap;
import java.util.Map;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

import org.keycloak.authorization.authzen.AuthZen;
import org.keycloak.http.simple.SimpleHttp;
import org.keycloak.http.simple.SimpleHttpRequest;
import org.keycloak.http.simple.SimpleHttpResponse;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

public final class AuthZenClient {

    private AuthZenClient() {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record WellKnownResponse(
            String policyDecisionPoint,
            String accessEvaluationEndpoint,
            String accessEvaluationsEndpoint
    ) {}

    public static Builder create(SimpleHttp simpleHttp, String realmUrl) {
        return new Builder(simpleHttp, realmUrl);
    }

    public static final class Builder {

        private final SimpleHttp simpleHttp;
        private final String realmUrl;
        private String accessToken;

        private Builder(SimpleHttp simpleHttp, String realmUrl) {
            this.simpleHttp = simpleHttp;
            this.realmUrl = realmUrl;
        }

        public Builder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public EvaluationResult evaluate(AuthZen.EvaluationRequest request) throws IOException {
            String url = realmUrl + "/authzen/access/v1/evaluation";

            try (SimpleHttpResponse response = req(simpleHttp.doPost(url).json(request))) {
                int status = response.getStatus();
                AuthZen.EvaluationResponse body = response.asJson(AuthZen.EvaluationResponse.class);
                return new EvaluationResult(status, body);
            }
        }

        public WellKnownResponse fetchWellKnownConfiguration() throws IOException {
            String url = realmUrl + "/.well-known/authzen-configuration";
            try(SimpleHttpResponse rsp = req(simpleHttp.doGet(url))) {
                return rsp.asJson(WellKnownResponse.class);
            }
        }

        private SimpleHttpResponse req(SimpleHttpRequest request) throws IOException {
            request.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
            request.acceptJson();
            if (accessToken != null) {
                request.auth(accessToken);
            }
            return request.asResponse();
        }
    }

    public record EvaluationResult(int statusCode, AuthZen.EvaluationResponse response) {

        public boolean decision() {
            return response.decision();
        }
    }

    public static EvaluationRequestBuilder evaluationRequest() {
        return new EvaluationRequestBuilder();
    }

    public static final class EvaluationRequestBuilder {
        private String subjectType;
        private String subjectId;
        private Map<String, Object> subjectProperties;

        private String resourceType;
        private String resourceId;
        private Map<String, Object> resourceProperties;

        private String actionName;

        public EvaluationRequestBuilder subject(String type, String id) {
            this.subjectType = type;
            this.subjectId = id;
            return this;
        }

        public EvaluationRequestBuilder subjectProperty(String key, Object value) {
            if (subjectProperties == null) {
                subjectProperties = new HashMap<>();
            }
            subjectProperties.put(key, value);
            return this;
        }

        public EvaluationRequestBuilder resource(String type, String id) {
            this.resourceType = type;
            this.resourceId = id;
            return this;
        }

        public EvaluationRequestBuilder resourceProperty(String key, Object value) {
            if (resourceProperties == null) {
                resourceProperties = new HashMap<>();
            }
            resourceProperties.put(key, value);
            return this;
        }

        public EvaluationRequestBuilder action(String name) {
            this.actionName = name;
            return this;
        }

        public AuthZen.EvaluationRequest build() {
            return new AuthZen.EvaluationRequest(
                    new AuthZen.Subject(subjectType, subjectId, subjectProperties),
                    new AuthZen.Resource(resourceType, resourceId, resourceProperties),
                    new AuthZen.Action(actionName)
            );
        }
    }
}
