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
package org.keycloak.authorization.policy.evaluation;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.keycloak.authorization.Decision.Effect;
import org.keycloak.authorization.model.Resource;
import org.keycloak.authorization.model.ResourceServer;
import org.keycloak.authorization.model.Scope;
import org.keycloak.authorization.permission.ResourcePermission;
import org.keycloak.representations.idm.authorization.AuthorizationRequest;
import org.keycloak.representations.idm.authorization.DecisionStrategy;
import org.keycloak.representations.idm.authorization.EvaluationSemantic;
import org.keycloak.representations.idm.authorization.PolicyEnforcementMode;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class DecisionPermissionCollectorTest {

    @Test
    public void executeAllIsNeverResolved() {
        DecisionPermissionCollector collector = createCollector(EvaluationSemantic.EXECUTE_ALL);
        assertThat(collector.isResolved(), is(false));

        simulateGrant(collector);
        assertThat(collector.isResolved(), is(false));
    }

    @Test
    public void permitOnFirstPermitResolvesWhenGranted() {
        DecisionPermissionCollector collector = createCollector(EvaluationSemantic.PERMIT_ON_FIRST_PERMIT);
        assertThat(collector.isResolved(), is(false));

        simulateGrant(collector);
        assertThat(collector.isResolved(), is(true));
    }

    @Test
    public void permitOnFirstPermitNotResolvedOnDeny() {
        DecisionPermissionCollector collector = createCollector(EvaluationSemantic.PERMIT_ON_FIRST_PERMIT);
        assertThat(collector.isResolved(), is(false));

        simulateDeny(collector);
        assertThat(collector.isResolved(), is(false));
    }

    @Test
    public void denyOnFirstDenyResolvesWhenDenied() {
        DecisionPermissionCollector collector = createCollector(EvaluationSemantic.DENY_ON_FIRST_DENY);
        assertThat(collector.isResolved(), is(false));

        simulateDeny(collector);
        assertThat(collector.isResolved(), is(true));
    }

    @Test
    public void denyOnFirstDenyNotResolvedOnGrant() {
        DecisionPermissionCollector collector = createCollector(EvaluationSemantic.DENY_ON_FIRST_DENY);
        assertThat(collector.isResolved(), is(false));

        simulateGrant(collector);
        assertThat(collector.isResolved(), is(false));
    }

    @Test
    public void defaultSemanticIsExecuteAllWhenRequestNull() {
        DecisionPermissionCollector collector = new DecisionPermissionCollector(null, stubResourceServer(), null);
        assertThat(collector.isResolved(), is(false));

        simulateGrant(collector);
        assertThat(collector.isResolved(), is(false));
    }

    @Test
    public void defaultSemanticIsExecuteAllWhenSemanticNull() {
        AuthorizationRequest request = new AuthorizationRequest();
        request.setMetadata(new AuthorizationRequest.Metadata());
        DecisionPermissionCollector collector = new DecisionPermissionCollector(null, stubResourceServer(), request);
        assertThat(collector.isResolved(), is(false));

        simulateGrant(collector);
        assertThat(collector.isResolved(), is(false));
    }

    private DecisionPermissionCollector createCollector(EvaluationSemantic semantic) {
        AuthorizationRequest request = new AuthorizationRequest();
        AuthorizationRequest.Metadata metadata = new AuthorizationRequest.Metadata();
        metadata.setEvaluationSemantic(semantic);
        request.setMetadata(metadata);
        return new DecisionPermissionCollector(null, stubResourceServer(), request);
    }

    private void simulateGrant(DecisionPermissionCollector collector) {
        Resource resource = stubResource("granted-resource");
        Scope scope = stubScope("read");
        ResourcePermission permission = new ResourcePermission(resource, List.of(scope), stubResourceServer());
        Evaluation evaluation = stubEvaluation(permission);
        Result result = new Result(permission, evaluation);
        result.setStatus(Effect.PERMIT);
        collector.onComplete(result);
    }

    private void simulateDeny(DecisionPermissionCollector collector) {
        Resource resource = stubResource("denied-resource");
        Scope scope = stubScope("write");
        ResourcePermission permission = new ResourcePermission(resource, List.of(scope), stubResourceServer());
        Evaluation evaluation = stubEvaluation(permission);
        Result result = new Result(permission, evaluation);
        result.setStatus(Effect.DENY);
        collector.onComplete(result);
    }

    private static ResourceServer stubResourceServer() {
        return new ResourceServer() {
            @Override public String getId() { return "test-server"; }
            @Override public boolean isAllowRemoteResourceManagement() { return false; }
            @Override public void setAllowRemoteResourceManagement(boolean b) {}
            @Override public PolicyEnforcementMode getPolicyEnforcementMode() { return PolicyEnforcementMode.ENFORCING; }
            @Override public void setPolicyEnforcementMode(PolicyEnforcementMode m) {}
            @Override public DecisionStrategy getDecisionStrategy() { return DecisionStrategy.UNANIMOUS; }
            @Override public void setDecisionStrategy(DecisionStrategy s) {}
            @Override public String getClientId() { return "test-client"; }
        };
    }

    private static Resource stubResource(String name) {
        return new Resource() {
            @Override public String getId() { return name; }
            @Override public String getName() { return name; }
            @Override public void setName(String n) {}
            @Override public String getDisplayName() { return name; }
            @Override public void setDisplayName(String n) {}
            @Override public Set<String> getUris() { return Collections.emptySet(); }
            @Override public void updateUris(Set<String> u) {}
            @Override public String getType() { return null; }
            @Override public void setType(String t) {}
            @Override public List<Scope> getScopes() { return Collections.emptyList(); }
            @Override public void updateScopes(Set<Scope> s) {}
            @Override public String getIconUri() { return null; }
            @Override public void setIconUri(String u) {}
            @Override public ResourceServer getResourceServer() { return stubResourceServer(); }
            @Override public String getOwner() { return "test-client"; }
            @Override public boolean isOwnerManagedAccess() { return false; }
            @Override public void setOwnerManagedAccess(boolean b) {}
            @Override public void setAttribute(String n, List<String> v) {}
            @Override public void removeAttribute(String n) {}
            @Override public Map<String, List<String>> getAttributes() { return Collections.emptyMap(); }
            @Override public List<String> getAttribute(String n) { return Collections.emptyList(); }
            @Override public String getSingleAttribute(String n) { return null; }
        };
    }

    private static Scope stubScope(String name) {
        return new Scope() {
            @Override public String getId() { return name; }
            @Override public String getName() { return name; }
            @Override public void setName(String n) {}
            @Override public String getDisplayName() { return name; }
            @Override public void setDisplayName(String n) {}
            @Override public String getIconUri() { return null; }
            @Override public void setIconUri(String u) {}
            @Override public ResourceServer getResourceServer() { return stubResourceServer(); }
        };
    }

    private static Evaluation stubEvaluation(ResourcePermission permission) {
        return new Evaluation() {
            @Override public ResourcePermission getPermission() { return permission; }
            @Override public EvaluationContext getContext() { return null; }
            @Override public org.keycloak.authorization.model.Policy getPolicy() { return null; }
            @Override public Realm getRealm() { return null; }
            @Override public org.keycloak.authorization.AuthorizationProvider getAuthorizationProvider() { return null; }
            @Override public void grant() {}
            @Override public void deny() {}
            @Override public void denyIfNoEffect() {}
            @Override public org.keycloak.authorization.model.Policy getParentPolicy() { return null; }
            @Override public Effect getEffect() { return null; }
            @Override public void setEffect(Effect e) {}
        };
    }
}
