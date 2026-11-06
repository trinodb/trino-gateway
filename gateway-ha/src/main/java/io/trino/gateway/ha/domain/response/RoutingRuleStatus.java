/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.gateway.ha.domain.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.trino.gateway.ha.domain.RoutingRule;
import jakarta.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A stored routing rule as shown in the admin UI. The rule's fields are serialized at the top level, next to the status.
 *
 * @param active false if routing skips the rule because it is not valid
 * @param error why the rule is not applied, or, for an active rule, the error it has failed with; null if neither
 */
public record RoutingRuleStatus(
        @JsonUnwrapped RoutingRule rule,
        boolean active,
        @Nullable String error)
{
    public RoutingRuleStatus
    {
        requireNonNull(rule, "rule is null");
    }

    public static RoutingRuleStatus active(RoutingRule rule)
    {
        return new RoutingRuleStatus(rule, true, null);
    }

    public static RoutingRuleStatus activeWithError(RoutingRule rule, String error)
    {
        return new RoutingRuleStatus(rule, true, requireNonNull(error, "error is null"));
    }

    public static RoutingRuleStatus notApplied(RoutingRule rule, String error)
    {
        return new RoutingRuleStatus(rule, false, requireNonNull(error, "error is null"));
    }
}
