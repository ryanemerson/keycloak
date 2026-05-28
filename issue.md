## Description

Keycloak's core Authorization Services currently always evaluates **all** `ResourcePermission` objects in a batch before returning results. There is no mechanism to short-circuit evaluation across permissions — every permission is fully evaluated regardless of intermediate outcomes.

The AuthZen integration (`authzen/services/.../AuthZen.java`) introduced `EvaluationsSemantic`, an enum that controls batch-level iteration behavior for its evaluations endpoint:

- **`EXECUTE_ALL`** — evaluate every item in the batch (current Keycloak default behavior)
- **`DENY_ON_FIRST_DENY`** — stop and return on the first denied evaluation
- **`PERMIT_ON_FIRST_PERMIT`** — stop and return on the first permitted evaluation

This concept is valuable beyond AuthZen. Keycloak's own authorization evaluation pipeline — used by the token endpoint, policy enforcer, and admin evaluation API — would benefit from the same short-circuit semantics for both correctness (callers expressing intent) and performance (avoiding unnecessary policy evaluation).

### How Keycloak authorization evaluation works today

Keycloak already has `DecisionStrategy` (`core/.../DecisionStrategy.java`) which controls how multiple **policies within a single permission** are aggregated (AFFIRMATIVE, UNANIMOUS, CONSENSUS). This is applied in `AbstractDecisionCollector.isGranted()` and `DecisionPermissionCollector.onComplete()`.

The gap is at the **outer loop** level. `IterablePermissionEvaluator.evaluate()` (`server-spi-private/.../IterablePermissionEvaluator.java:61-83`) iterates all `ResourcePermission` objects unconditionally:

```java
while (permissions.hasNext()) {
    this.policyEvaluator.evaluate(permissions.next(), authorizationProvider, executionContext, decision, decisionCache);
}
decision.onComplete();
```

There is no way for the `Decision` callback to signal early termination to the evaluator loop.

### Key classes involved

| Class / Interface | Location | Role |
|---|---|---|
| `IterablePermissionEvaluator` | `server-spi-private/.../permission/evaluator/` | The evaluation loop that iterates `ResourcePermission` objects — primary integration point |
| `Decision<D>` | `server-spi-private/.../Decision.java` | Callback interface receiving per-policy decisions; needs a termination signal |
| `DecisionPermissionCollector` | `server-spi-private/.../policy/evaluation/` | Concrete `Decision` impl that aggregates granted permissions; would implement termination logic |
| `AbstractDecisionCollector` | `server-spi-private/.../policy/evaluation/` | Base class for decision collection; applies `DecisionStrategy` per policy |
| `AuthorizationRequest` / `Metadata` | `core/.../idm/authorization/` | Request representation; its `Metadata` inner class carries evaluation options |
| `PermissionEvaluator` | `server-spi-private/.../permission/evaluator/` | Public interface callers use; already accepts `AuthorizationRequest` |
| `DecisionStrategy` | `core/.../idm/authorization/` | Existing enum for intra-permission policy aggregation (the complementary concept) |
| `DefaultPolicyEvaluator` | `server-spi-private/.../policy/evaluation/` | Evaluates policies for a single `ResourcePermission`; no changes needed |

### Proposed implementation

1. **New enum** `EvaluationSemantic` in `org.keycloak.representations.idm.authorization` alongside `DecisionStrategy`, with values `EXECUTE_ALL`, `DENY_ON_FIRST_DENY`, `PERMIT_ON_FIRST_PERMIT` and implementing `EnumWithStableIndex`.

2. **Extend `Decision` interface** with a default method `boolean isResolved()` returning `false`, so existing implementations are unaffected.

3. **Thread the semantic through `AuthorizationRequest.Metadata`** by adding an `evaluationSemantic` field. This flows through the existing API plumbing without interface changes.

4. **Implement in `DecisionPermissionCollector`**: override `isResolved()` to check the semantic against current results — return `true` when `PERMIT_ON_FIRST_PERMIT` and a permission has been granted, or when `DENY_ON_FIRST_DENY` and a permission has been denied.

5. **Modify `IterablePermissionEvaluator.evaluate()`**: add a single check after each iteration:
   ```java
   while (permissions.hasNext()) {
       this.policyEvaluator.evaluate(permissions.next(), ...);
       if (decision.isResolved()) {
           break;
       }
   }
   ```

`EvaluationsSemantic` and `DecisionStrategy` are complementary — one controls the outer loop (across permissions), the other controls inner aggregation (across policies within a permission). Both coexist naturally.

## Value Proposition

- **Performance**: Callers that only need to know "is anything denied?" or "is anything permitted?" can avoid evaluating all remaining permissions once the answer is determined. This is especially impactful for requests involving many resource-permission pairs.
- **Expressiveness**: Callers can declare their evaluation intent directly, rather than post-processing a full result set.
- **Alignment with AuthZen**: The AuthZen integration already implements this concept at the REST layer (`AuthZenResource.evaluations()`). Pushing it into the core evaluation engine means AuthZen can delegate to the engine rather than reimplementing short-circuit logic, and all other authorization consumers (token endpoint, policy enforcer, admin API) gain the same capability.
- **Backward compatibility**: `EXECUTE_ALL` as the default preserves existing behavior. The `isResolved()` default method on `Decision` returns `false`, so no existing code is affected.

## Goals

- Introduce an `EvaluationSemantic` enum in the core authorization representations with `EXECUTE_ALL`, `DENY_ON_FIRST_DENY`, and `PERMIT_ON_FIRST_PERMIT` values.
- Extend the `Decision` interface with a default `isResolved()` method to signal early termination.
- Allow `AuthorizationRequest.Metadata` to carry the evaluation semantic so callers can specify it.
- Implement short-circuit logic in `DecisionPermissionCollector` and `IterablePermissionEvaluator`.
- Enable AuthZen's `AuthZenResource` to delegate batch semantics to the core engine rather than implementing its own loop-level short-circuiting.

## Non-Goals

- Changing the existing `DecisionStrategy` behavior or its role in intra-permission policy aggregation.
- Adding new REST API endpoints — the semantic is threaded through existing internal APIs and can be exposed by consumers (AuthZen, token endpoint, etc.) as they see fit.
- Modifying `DefaultPolicyEvaluator` or the per-permission policy evaluation logic.
- Exposing `EvaluationSemantic` in the Admin UI or resource server configuration — this is a per-request option, not a server-level setting.

## Notes

The AuthZen implementation in `AuthZenResource.evaluations()` (lines 104-126) currently implements short-circuit semantics at the REST handler level by looping over evaluation items and breaking early. Once this enhancement is in place, that logic could be simplified to pass the semantic through to the core engine via `AuthorizationRequest.Metadata`, removing the manual loop control from the REST layer.

The `IterablePermissionEvaluator` change is intentionally minimal — a single `if (decision.isResolved()) break;` in the evaluation loop. This keeps the blast radius small and makes the feature easy to reason about.
