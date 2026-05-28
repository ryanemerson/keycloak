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
