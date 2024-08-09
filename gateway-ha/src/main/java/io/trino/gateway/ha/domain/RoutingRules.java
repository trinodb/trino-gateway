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
package io.trino.gateway.ha.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * RoutingRules
 *
 * @param name name of the routing rule
 * @param description description of the routing rule
 * @param priority priority of the routing rule
 * @param actions actions of the routing rule
 * @param condition condition of the routing rule
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RoutingRules(
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("priority") Integer priority,
        @JsonProperty("actions") List<String> actions,
        @JsonProperty("condition") String condition)
{
}
