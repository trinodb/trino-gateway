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
package io.trino.gateway.ha.router;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.trino.gateway.ha.domain.RoutingRule;

import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * The routing rules read from a {@link RoutingRulesStore} in one call.
 *
 * @param rules all stored rules, including those that could not be fully read, whose unreadable values are left empty
 * @param readErrors names of the rules that could not be fully read, mapped to the reason
 */
public record StoredRoutingRules(List<RoutingRule> rules, Map<String, String> readErrors)
{
    public StoredRoutingRules
    {
        rules = ImmutableList.copyOf(requireNonNull(rules, "rules is null"));
        readErrors = ImmutableMap.copyOf(requireNonNull(readErrors, "readErrors is null"));
    }
}
