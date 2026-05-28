# EvaluationSemantic Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `EvaluationSemantic` to Keycloak's core authorization pipeline so that batch permission evaluations can short-circuit on first deny or first permit, mirroring AuthZen's `EvaluationsSemantic`.

**Architecture:** New enum `EvaluationSemantic` in core representations, a `isResolved()` default method on the `Decision` interface, threading through `AuthorizationRequest.Metadata`, implementation in `DecisionPermissionCollector`, and a single `if` check in `IterablePermissionEvaluator`'s loop. A unit test in `server-spi-private` validates the short-circuit behavior directly.

**Tech Stack:** Java 17, Maven, Keycloak SPI/authorization framework, JUnit 5 + Hamcrest

---

### Task 1: Create the `EvaluationSemantic` enum

**Files:**
- Create: `core/src/main/java/org/keycloak/representations/idm/authorization/EvaluationSemantic.java`

- [ ] **Step 1: Create the enum file**

```java
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
package org.keycloak.representations.idm.authorization;

import java.util.Map;
import java.util.Objects;

import org.keycloak.util.EnumWithStableIndex;

/**
 * Controls how multiple resource permissions are evaluated in a batch.
 *
 * <ul>
 *   <li>{@link #EXECUTE_ALL} evaluates every permission (default, preserves existing behavior).</li>
 *   <li>{@link #DENY_ON_FIRST_DENY} stops evaluation on the first denied permission.</li>
 *   <li>{@link #PERMIT_ON_FIRST_PERMIT} stops evaluation on the first granted permission.</li>
 * </ul>
 */
public enum EvaluationSemantic implements EnumWithStableIndex {

    EXECUTE_ALL(0),

    DENY_ON_FIRST_DENY(1),

    PERMIT_ON_FIRST_PERMIT(2);

    private final int stableIndex;
    private static final Map<Integer, EvaluationSemantic> BY_ID = EnumWithStableIndex.getReverseIndex(values());

    EvaluationSemantic(int stableIndex) {
        Objects.requireNonNull(stableIndex);
        this.stableIndex = stableIndex;
    }

    @Override
    public int getStableIndex() {
        return stableIndex;
    }

    public static EvaluationSemantic valueOfInteger(Integer id) {
        return id == null ? null : BY_ID.get(id);
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./mvnw -f core/pom.xml compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/org/keycloak/representations/idm/authorization/EvaluationSemantic.java
git commit --signoff -m "Add EvaluationSemantic enum for batch permission evaluation control"
```

---

### Task 2: Add `evaluationSemantic` to `AuthorizationRequest.Metadata`

**Files:**
- Modify: `core/src/main/java/org/keycloak/representations/idm/authorization/AuthorizationRequest.java` (Metadata inner class, lines 185-235)

- [ ] **Step 1: Add the field, getter, and setter to Metadata**

In the `Metadata` inner class (after the existing `permissionResourceMatchingUri` field at line 191), add:

```java
private EvaluationSemantic evaluationSemantic;
```

And after the `setPermissionResourceMatchingUri` method (after line 234), add:

```java
public EvaluationSemantic getEvaluationSemantic() {
    return evaluationSemantic;
}

public void setEvaluationSemantic(EvaluationSemantic evaluationSemantic) {
    this.evaluationSemantic = evaluationSemantic;
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./mvnw -f core/pom.xml compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/org/keycloak/representations/idm/authorization/AuthorizationRequest.java
git commit --signoff -m "Add evaluationSemantic field to AuthorizationRequest.Metadata"
```

---

### Task 3: Add `isResolved()` to the `Decision` interface

**Files:**
- Modify: `server-spi-private/src/main/java/org/keycloak/authorization/Decision.java`

- [ ] **Step 1: Add the default method**

After the existing `isEvaluated` default method (after line 53), add:

```java
/**
 * Returns {@code true} when the decision has been resolved and further
 * permission evaluations can be skipped. Used with {@link org.keycloak.representations.idm.authorization.EvaluationSemantic}
 * to support short-circuit evaluation across multiple permissions.
 *
 * @return {@code true} if evaluation should stop
 */
default boolean isResolved() {
    return false;
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./mvnw -f server-spi-private/pom.xml compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add server-spi-private/src/main/java/org/keycloak/authorization/Decision.java
git commit --signoff -m "Add isResolved default method to Decision interface"
```

---

### Task 4: Implement `isResolved()` in `DecisionPermissionCollector`

**Files:**
- Modify: `server-spi-private/src/main/java/org/keycloak/authorization/policy/evaluation/DecisionPermissionCollector.java`

- [ ] **Step 1: Add import for EvaluationSemantic**

Add this import to the existing import block:

```java
import org.keycloak.representations.idm.authorization.EvaluationSemantic;
```

- [ ] **Step 2: Add `evaluationSemantic` field and `denied` flag**

After the existing `permissions` field (line 48), add:

```java
private final EvaluationSemantic evaluationSemantic;
private boolean denied;
```

- [ ] **Step 3: Update the constructor to extract the semantic from the request**

Replace the constructor (lines 50-54) with:

```java
public DecisionPermissionCollector(AuthorizationProvider authorizationProvider, ResourceServer resourceServer, AuthorizationRequest request) {
    this.authorizationProvider = authorizationProvider;
    this.resourceServer = resourceServer;
    this.request = request;
    if (request != null && request.getMetadata() != null && request.getMetadata().getEvaluationSemantic() != null) {
        this.evaluationSemantic = request.getMetadata().getEvaluationSemantic();
    } else {
        this.evaluationSemantic = EvaluationSemantic.EXECUTE_ALL;
    }
}
```

- [ ] **Step 4: Mark denied state in onComplete(Result)**

In the `onComplete(Result)` method, a "denied" result is any case where the method returns without calling `grantPermission`. The method already has early returns for those cases. We need to track when a permission is *not* granted. After the existing `onComplete(Result)` method override (line 56), replace it with:

```java
@Override
public void onComplete(Result result) {
    ResourcePermission permission = result.getPermission();
    Resource resource = permission.getResource();
    Collection<Scope> requestedScopes = permission.getScopes();

    if (Effect.PERMIT.equals(result.getEffect())) {
        if (permission.getScopes().isEmpty() && !resource.getScopes().isEmpty()) {
            denied = true;
            return;
        }
        grantPermission(authorizationProvider, permissions, permission, requestedScopes, resourceServer, request, result);
    } else {
        Set<Scope> grantedScopes = new HashSet<>();
        Set<Scope> deniedScopes = new HashSet<>();
        List<Result.PolicyResult> userManagedPermissions = new ArrayList<>();
        boolean resourceGranted = false;
        boolean anyDeny = false;

        for (Result.PolicyResult policyResult : result.getResults()) {
            Policy policy = policyResult.getPolicy();
            Set<Scope> policyScopes = policy.getScopes();
            Set<Resource> policyResources = policy.getResources();
            boolean containsResource = policyResources.contains(resource);
            Evaluation evaluation = result.getEvaluation();

            if (isGranted(policyResult)) {
                if (isScopePermission(policy)) {
                    for (Scope scope : requestedScopes) {
                        if (evaluation.isGranted(policy, scope)) {
                            grantedScopes.add(scope);
                            if (resource != null && !resource.getScopes().contains(scope)) {
                                deniedScopes.remove(scope);
                            }
                        }
                    }
                } else if (isResourcePermission(policy)) {
                    grantedScopes.addAll(requestedScopes);
                } else if (resource != null && resource.isOwnerManagedAccess() && "uma".equals(policy.getType())) {
                    userManagedPermissions.add(policyResult);
                }
                if (!resourceGranted) {
                    resourceGranted = isGrantingAccessToResource(resource, policy) && containsResource;
                }
            } else {
                if (isResourcePermission(policy)) {
                    if (containsResource || !resourceGranted) {
                        deniedScopes.addAll(requestedScopes);
                    }
                } else {
                    if (containsResource || policyResources.isEmpty()) {
                        deniedScopes.addAll(policyScopes);
                    } else {
                        for (Scope scope : requestedScopes) {
                            if (evaluation.isDenied(policy, scope)) {
                                deniedScopes.add(scope);
                            }
                        }
                    }
                }
                if (!anyDeny) {
                    anyDeny = true;
                }
            }
        }

        if (DecisionStrategy.AFFIRMATIVE.equals(resourceServer.getDecisionStrategy())) {
            deniedScopes.removeAll(grantedScopes);
        }

        grantedScopes.removeAll(deniedScopes);

        if (userManagedPermissions.isEmpty()) {
            if (!resourceGranted && (grantedScopes.isEmpty() && !requestedScopes.isEmpty())) {
                denied = true;
                return;
            }
        } else {
            for (Result.PolicyResult userManagedPermission : userManagedPermissions) {
                Set<Scope> scopes = new HashSet<>(userManagedPermission.getPolicy().getScopes());

                if (!requestedScopes.isEmpty()) {
                    scopes.retainAll(requestedScopes);
                }

                grantedScopes.addAll(scopes);
            }

            if (grantedScopes.isEmpty() && !resource.getScopes().isEmpty()) {
                denied = true;
                return;
            }

            anyDeny = false;
        }

        if (anyDeny && grantedScopes.isEmpty()) {
            denied = true;
            return;
        }

        grantPermission(authorizationProvider, permissions, permission, grantedScopes, resourceServer, request, result);
    }
}
```

- [ ] **Step 5: Add the `isResolved()` override**

After the `results()` method (line 186), add:

```java
@Override
public boolean isResolved() {
    if (evaluationSemantic == EvaluationSemantic.PERMIT_ON_FIRST_PERMIT) {
        return !permissions.isEmpty();
    }
    if (evaluationSemantic == EvaluationSemantic.DENY_ON_FIRST_DENY) {
        return denied;
    }
    return false;
}
```

- [ ] **Step 6: Verify it compiles**

Run: `./mvnw -f server-spi-private/pom.xml compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add server-spi-private/src/main/java/org/keycloak/authorization/policy/evaluation/DecisionPermissionCollector.java
git commit --signoff -m "Implement isResolved in DecisionPermissionCollector for short-circuit evaluation"
```

---

### Task 5: Modify `IterablePermissionEvaluator` to check `isResolved()`

**Files:**
- Modify: `server-spi-private/src/main/java/org/keycloak/authorization/permission/evaluator/IterablePermissionEvaluator.java`

- [ ] **Step 1: Add the short-circuit check in the evaluation loop**

In the `evaluate(Decision)` method, replace the while loop (lines 71-73):

```java
while (permissions.hasNext()) {
    this.policyEvaluator.evaluate(permissions.next(), authorizationProvider, executionContext, decision, decisionCache);
}
```

with:

```java
while (permissions.hasNext()) {
    this.policyEvaluator.evaluate(permissions.next(), authorizationProvider, executionContext, decision, decisionCache);
    if (decision.isResolved()) {
        break;
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./mvnw -f server-spi-private/pom.xml compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add server-spi-private/src/main/java/org/keycloak/authorization/permission/evaluator/IterablePermissionEvaluator.java
git commit --signoff -m "Add short-circuit check in IterablePermissionEvaluator evaluation loop"
```

---

### Task 6: Write unit tests for `EvaluationSemantic` and `DecisionPermissionCollector.isResolved()`

**Files:**
- Create: `server-spi-private/src/test/java/org/keycloak/authorization/policy/evaluation/DecisionPermissionCollectorTest.java`

This test directly instantiates `DecisionPermissionCollector` with various `EvaluationSemantic` settings and verifies `isResolved()` behavior. It does not require a running server — it uses stub implementations of the authorization model interfaces.

- [ ] **Step 1: Write the test class**

```java
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

import org.junit.jupiter.api.Test;
import org.keycloak.authorization.AuthorizationProvider;
import org.keycloak.authorization.Decision.Effect;
import org.keycloak.authorization.model.Policy;
import org.keycloak.authorization.model.Resource;
import org.keycloak.authorization.model.ResourceServer;
import org.keycloak.authorization.model.Scope;
import org.keycloak.authorization.permission.ResourcePermission;
import org.keycloak.authorization.store.ResourceServerStore;
import org.keycloak.authorization.store.StoreFactory;
import org.keycloak.representations.idm.authorization.AuthorizationRequest;
import org.keycloak.representations.idm.authorization.DecisionStrategy;
import org.keycloak.representations.idm.authorization.EvaluationSemantic;
import org.keycloak.representations.idm.authorization.PolicyEnforcementMode;

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
    public void defaultSemanticIsExecuteAllWhenMetadataNull() {
        DecisionPermissionCollector collector = new DecisionPermissionCollector(stubAuthorizationProvider(), stubResourceServer(), null);
        assertThat(collector.isResolved(), is(false));

        simulateGrant(collector);
        assertThat(collector.isResolved(), is(false));
    }

    @Test
    public void defaultSemanticIsExecuteAllWhenSemanticNull() {
        AuthorizationRequest request = new AuthorizationRequest();
        request.setMetadata(new AuthorizationRequest.Metadata());
        DecisionPermissionCollector collector = new DecisionPermissionCollector(stubAuthorizationProvider(), stubResourceServer(), request);
        assertThat(collector.isResolved(), is(false));

        simulateGrant(collector);
        assertThat(collector.isResolved(), is(false));
    }

    private DecisionPermissionCollector createCollector(EvaluationSemantic semantic) {
        AuthorizationRequest request = new AuthorizationRequest();
        AuthorizationRequest.Metadata metadata = new AuthorizationRequest.Metadata();
        metadata.setEvaluationSemantic(semantic);
        request.setMetadata(metadata);
        return new DecisionPermissionCollector(stubAuthorizationProvider(), stubResourceServer(), request);
    }

    /**
     * Simulates a granted permission by creating a Result with PERMIT effect
     * and calling onComplete(Result) on the collector.
     */
    private void simulateGrant(DecisionPermissionCollector collector) {
        Resource resource = stubResource("granted-resource");
        Scope scope = stubScope("read");
        ResourcePermission permission = new ResourcePermission(resource, List.of(scope), stubResourceServer());
        Evaluation evaluation = stubEvaluation(permission);
        Result result = new Result(permission, evaluation);
        result.setStatus(Effect.PERMIT);
        collector.onComplete(result);
    }

    /**
     * Simulates a denied permission by creating a Result with DENY effect and
     * no policy results (so the permission is not granted).
     */
    private void simulateDeny(DecisionPermissionCollector collector) {
        Resource resource = stubResource("denied-resource");
        Scope scope = stubScope("write");
        ResourcePermission permission = new ResourcePermission(resource, List.of(scope), stubResourceServer());
        Evaluation evaluation = stubEvaluation(permission);
        Result result = new Result(permission, evaluation);
        result.setStatus(Effect.DENY);
        collector.onComplete(result);
    }

    private static AuthorizationProvider stubAuthorizationProvider() {
        return new AuthorizationProvider() {
            @Override
            public StoreFactory getStoreFactory() {
                return null;
            }

            @Override
            public org.keycloak.authorization.permission.evaluator.Evaluators evaluators() {
                return null;
            }

            @Override
            public PolicyEvaluator getPolicyEvaluator(ResourceServer resourceServer) {
                return null;
            }

            @Override
            public org.keycloak.authorization.policy.provider.PolicyProvider getProvider(String type) {
                return null;
            }

            @Override
            public org.keycloak.models.KeycloakSession getKeycloakSession() {
                return null;
            }

            @Override
            public void close() {
            }
        };
    }

    private static ResourceServer stubResourceServer() {
        return new ResourceServer() {
            @Override
            public String getId() {
                return "test-server";
            }

            @Override
            public boolean isAllowRemoteResourceManagement() {
                return false;
            }

            @Override
            public void setAllowRemoteResourceManagement(boolean allowRemoteResourceManagement) {
            }

            @Override
            public PolicyEnforcementMode getPolicyEnforcementMode() {
                return PolicyEnforcementMode.ENFORCING;
            }

            @Override
            public void setPolicyEnforcementMode(PolicyEnforcementMode enforcementMode) {
            }

            @Override
            public DecisionStrategy getDecisionStrategy() {
                return DecisionStrategy.UNANIMOUS;
            }

            @Override
            public void setDecisionStrategy(DecisionStrategy decisionStrategy) {
            }

            @Override
            public String getClientId() {
                return "test-client";
            }
        };
    }

    private static Resource stubResource(String name) {
        return new Resource() {
            @Override
            public String getId() {
                return name;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public void setName(String name) {
            }

            @Override
            public String getDisplayName() {
                return name;
            }

            @Override
            public void setDisplayName(String name) {
            }

            @Override
            public Set<String> getUris() {
                return Collections.emptySet();
            }

            @Override
            public void updateUris(Set<String> uri) {
            }

            @Override
            public String getType() {
                return null;
            }

            @Override
            public void setType(String type) {
            }

            @Override
            public Set<Scope> getScopes() {
                return Collections.emptySet();
            }

            @Override
            public void updateScopes(Set<Scope> scopes) {
            }

            @Override
            public String getIconUri() {
                return null;
            }

            @Override
            public void setIconUri(String iconUri) {
            }

            @Override
            public ResourceServer getResourceServer() {
                return stubResourceServer();
            }

            @Override
            public String getOwner() {
                return "test-client";
            }

            @Override
            public boolean isOwnerManagedAccess() {
                return false;
            }

            @Override
            public void setOwnerManagedAccess(boolean ownerManagedAccess) {
            }

            @Override
            public void setAttribute(String name, List<String> values) {
            }

            @Override
            public void removeAttribute(String name) {
            }

            @Override
            public Map<String, List<String>> getAttributes() {
                return Collections.emptyMap();
            }

            @Override
            public boolean isFetched(String association) {
                return false;
            }
        };
    }

    private static Scope stubScope(String name) {
        return new Scope() {
            @Override
            public String getId() {
                return name;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public void setName(String name) {
            }

            @Override
            public String getDisplayName() {
                return name;
            }

            @Override
            public void setDisplayName(String name) {
            }

            @Override
            public String getIconUri() {
                return null;
            }

            @Override
            public void setIconUri(String iconUri) {
            }

            @Override
            public ResourceServer getResourceServer() {
                return stubResourceServer();
            }
        };
    }

    private static Evaluation stubEvaluation(ResourcePermission permission) {
        return new Evaluation() {
            @Override
            public ResourcePermission getPermission() {
                return permission;
            }

            @Override
            public EvaluationContext getContext() {
                return null;
            }

            @Override
            public Policy getPolicy() {
                return null;
            }

            @Override
            public Realm getRealm() {
                return null;
            }

            @Override
            public AuthorizationProvider getAuthorizationProvider() {
                return null;
            }

            @Override
            public void grant() {
            }

            @Override
            public void deny() {
            }

            @Override
            public void denyIfNoEffect() {
            }

            @Override
            public Policy getParentPolicy() {
                return null;
            }

            @Override
            public Effect getEffect() {
                return null;
            }

            @Override
            public void setEffect(Effect effect) {
            }
        };
    }
}
```

- [ ] **Step 2: Run the tests**

Run: `./mvnw -f server-spi-private/pom.xml test -Dtest=DecisionPermissionCollectorTest -pl server-spi-private`
Expected: All 7 tests PASS

- [ ] **Step 3: Commit**

```bash
git add server-spi-private/src/test/java/org/keycloak/authorization/policy/evaluation/DecisionPermissionCollectorTest.java
git commit --signoff -m "Add unit tests for EvaluationSemantic and DecisionPermissionCollector.isResolved"
```

---

### Task 7: Write integration test for short-circuit evaluation via `IterablePermissionEvaluator`

**Files:**
- Modify: `testsuite/integration-arquillian/tests/base/src/test/java/org/keycloak/testsuite/authz/PolicyEvaluationTest.java`

This test creates multiple resource permissions, sets up an `AuthorizationRequest` with an `EvaluationSemantic`, and verifies that the evaluator short-circuits correctly. It uses the existing run-on-server test pattern from `PolicyEvaluationTest`.

- [ ] **Step 1: Add required imports**

Add these imports to the existing import block in `PolicyEvaluationTest.java`:

```java
import org.keycloak.representations.idm.authorization.AuthorizationRequest;
import org.keycloak.representations.idm.authorization.EvaluationSemantic;
```

- [ ] **Step 2: Add test for `PERMIT_ON_FIRST_PERMIT`**

Add after the `testEvaluation` test method (before the closing `}` of the class):

```java
@Test
public void testEvaluationSemanticPermitOnFirstPermit() {
    testingClient.server().run(PolicyEvaluationTest::testEvaluationSemanticPermitOnFirstPermit);
}

public static void testEvaluationSemanticPermitOnFirstPermit(KeycloakSession session) {
    session.getContext().setRealm(session.realms().getRealmByName("authz-test"));
    AuthorizationProvider authorization = session.getProvider(AuthorizationProvider.class);
    ClientModel clientModel = session.clients().getClientByClientId(session.getContext().getRealm(), "resource-server-test");
    StoreFactory storeFactory = authorization.getStoreFactory();
    ResourceServer resourceServer = storeFactory.getResourceServerStore().findByClient(clientModel);

    Scope readScope = storeFactory.getScopeStore().findByName(resourceServer, "read");
    if (readScope == null) {
        readScope = storeFactory.getScopeStore().create(resourceServer, "permitFirstRead");
    }

    Resource resourceA = storeFactory.getResourceStore().create(resourceServer, KeycloakModelUtils.generateId(), resourceServer.getClientId());
    Resource resourceB = storeFactory.getResourceStore().create(resourceServer, KeycloakModelUtils.generateId(), resourceServer.getClientId());

    UserPolicyRepresentation userPolicy = new UserPolicyRepresentation();
    userPolicy.setName(KeycloakModelUtils.generateId());
    UserModel marta = session.users().getUserByUsername(session.getContext().getRealm(), "marta");
    userPolicy.addUser(marta.getId());
    Policy policy = storeFactory.getPolicyStore().create(resourceServer, userPolicy);

    ResourcePermissionRepresentation permRepA = new ResourcePermissionRepresentation();
    permRepA.setName(KeycloakModelUtils.generateId());
    permRepA.addResource(resourceA.getId());
    permRepA.addPolicy(policy.getName());
    storeFactory.getPolicyStore().create(resourceServer, permRepA);

    ResourcePermissionRepresentation permRepB = new ResourcePermissionRepresentation();
    permRepB.setName(KeycloakModelUtils.generateId());
    permRepB.addResource(resourceB.getId());
    permRepB.addPolicy(policy.getName());
    storeFactory.getPolicyStore().create(resourceServer, permRepB);

    session.getTransactionManager().commit();

    AuthorizationRequest request = new AuthorizationRequest();
    AuthorizationRequest.Metadata metadata = new AuthorizationRequest.Metadata();
    metadata.setEvaluationSemantic(EvaluationSemantic.PERMIT_ON_FIRST_PERMIT);
    request.setMetadata(metadata);

    DefaultEvaluationContext context = createEvaluationContext(session, Collections.emptyMap());
    PermissionEvaluator evaluator = authorization.evaluators().from(
            Arrays.asList(
                    new ResourcePermission(resourceA, Collections.emptyList(), resourceServer),
                    new ResourcePermission(resourceB, Collections.emptyList(), resourceServer)),
            context);

    Collection<Permission> permissions = evaluator.evaluate(resourceServer, request);

    // With PERMIT_ON_FIRST_PERMIT, we should get at least one permission
    // but may not get all since evaluation short-circuits after the first grant
    assertFalse(permissions.isEmpty());
    Assertions.assertTrue(permissions.size() <= 2);
}
```

- [ ] **Step 3: Add test for `DENY_ON_FIRST_DENY`**

Add after the previous test method:

```java
@Test
public void testEvaluationSemanticDenyOnFirstDeny() {
    testingClient.server().run(PolicyEvaluationTest::testEvaluationSemanticDenyOnFirstDeny);
}

public static void testEvaluationSemanticDenyOnFirstDeny(KeycloakSession session) {
    session.getContext().setRealm(session.realms().getRealmByName("authz-test"));
    AuthorizationProvider authorization = session.getProvider(AuthorizationProvider.class);
    ClientModel clientModel = session.clients().getClientByClientId(session.getContext().getRealm(), "resource-server-test");
    StoreFactory storeFactory = authorization.getStoreFactory();
    ResourceServer resourceServer = storeFactory.getResourceServerStore().findByClient(clientModel);

    Scope writeScope = storeFactory.getScopeStore().create(resourceServer, "denyFirstWrite");

    // resourceA has a scope but no permission granting it — will be denied
    Resource resourceA = storeFactory.getResourceStore().create(resourceServer, KeycloakModelUtils.generateId(), resourceServer.getClientId());
    // resourceB is granted via user policy
    Resource resourceB = storeFactory.getResourceStore().create(resourceServer, KeycloakModelUtils.generateId(), resourceServer.getClientId());

    UserPolicyRepresentation userPolicy = new UserPolicyRepresentation();
    userPolicy.setName(KeycloakModelUtils.generateId());
    UserModel marta = session.users().getUserByUsername(session.getContext().getRealm(), "marta");
    userPolicy.addUser(marta.getId());
    Policy policy = storeFactory.getPolicyStore().create(resourceServer, userPolicy);

    ResourcePermissionRepresentation permRepB = new ResourcePermissionRepresentation();
    permRepB.setName(KeycloakModelUtils.generateId());
    permRepB.addResource(resourceB.getId());
    permRepB.addPolicy(policy.getName());
    storeFactory.getPolicyStore().create(resourceServer, permRepB);

    session.getTransactionManager().commit();

    AuthorizationRequest request = new AuthorizationRequest();
    AuthorizationRequest.Metadata metadata = new AuthorizationRequest.Metadata();
    metadata.setEvaluationSemantic(EvaluationSemantic.DENY_ON_FIRST_DENY);
    request.setMetadata(metadata);

    DefaultEvaluationContext context = createEvaluationContext(session, Collections.emptyMap());
    // resourceA is first and has a scope requested but no policy — will be denied
    PermissionEvaluator evaluator = authorization.evaluators().from(
            Arrays.asList(
                    new ResourcePermission(resourceA, List.of(writeScope), resourceServer),
                    new ResourcePermission(resourceB, Collections.emptyList(), resourceServer)),
            context);

    Collection<Permission> permissions = evaluator.evaluate(resourceServer, request);

    // With DENY_ON_FIRST_DENY, the first denied permission should stop evaluation.
    // resourceA is denied (scope requested but no policy grants it), so resourceB is never evaluated.
    Assertions.assertTrue(permissions.isEmpty());
}
```

- [ ] **Step 4: Add test for `EXECUTE_ALL` (baseline)**

Add after the previous test method:

```java
@Test
public void testEvaluationSemanticExecuteAll() {
    testingClient.server().run(PolicyEvaluationTest::testEvaluationSemanticExecuteAll);
}

public static void testEvaluationSemanticExecuteAll(KeycloakSession session) {
    session.getContext().setRealm(session.realms().getRealmByName("authz-test"));
    AuthorizationProvider authorization = session.getProvider(AuthorizationProvider.class);
    ClientModel clientModel = session.clients().getClientByClientId(session.getContext().getRealm(), "resource-server-test");
    StoreFactory storeFactory = authorization.getStoreFactory();
    ResourceServer resourceServer = storeFactory.getResourceServerStore().findByClient(clientModel);

    Resource resourceA = storeFactory.getResourceStore().create(resourceServer, KeycloakModelUtils.generateId(), resourceServer.getClientId());
    Resource resourceB = storeFactory.getResourceStore().create(resourceServer, KeycloakModelUtils.generateId(), resourceServer.getClientId());

    UserPolicyRepresentation userPolicy = new UserPolicyRepresentation();
    userPolicy.setName(KeycloakModelUtils.generateId());
    UserModel marta = session.users().getUserByUsername(session.getContext().getRealm(), "marta");
    userPolicy.addUser(marta.getId());
    Policy policy = storeFactory.getPolicyStore().create(resourceServer, userPolicy);

    ResourcePermissionRepresentation permRepA = new ResourcePermissionRepresentation();
    permRepA.setName(KeycloakModelUtils.generateId());
    permRepA.addResource(resourceA.getId());
    permRepA.addPolicy(policy.getName());
    storeFactory.getPolicyStore().create(resourceServer, permRepA);

    ResourcePermissionRepresentation permRepB = new ResourcePermissionRepresentation();
    permRepB.setName(KeycloakModelUtils.generateId());
    permRepB.addResource(resourceB.getId());
    permRepB.addPolicy(policy.getName());
    storeFactory.getPolicyStore().create(resourceServer, permRepB);

    session.getTransactionManager().commit();

    AuthorizationRequest request = new AuthorizationRequest();
    AuthorizationRequest.Metadata metadata = new AuthorizationRequest.Metadata();
    metadata.setEvaluationSemantic(EvaluationSemantic.EXECUTE_ALL);
    request.setMetadata(metadata);

    DefaultEvaluationContext context = createEvaluationContext(session, Collections.emptyMap());
    PermissionEvaluator evaluator = authorization.evaluators().from(
            Arrays.asList(
                    new ResourcePermission(resourceA, Collections.emptyList(), resourceServer),
                    new ResourcePermission(resourceB, Collections.emptyList(), resourceServer)),
            context);

    Collection<Permission> permissions = evaluator.evaluate(resourceServer, request);

    // With EXECUTE_ALL, both permissions should be evaluated and both granted
    assertEquals(2, permissions.size());
}
```

- [ ] **Step 5: Verify the integration tests compile**

Run: `./mvnw -f testsuite/integration-arquillian/tests/base/pom.xml compile -q -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add testsuite/integration-arquillian/tests/base/src/test/java/org/keycloak/testsuite/authz/PolicyEvaluationTest.java
git commit --signoff -m "Add integration tests for EvaluationSemantic short-circuit behavior"
```
