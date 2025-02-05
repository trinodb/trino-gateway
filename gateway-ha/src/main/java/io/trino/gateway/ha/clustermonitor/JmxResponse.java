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
package io.trino.gateway.ha.clustermonitor;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.stream.StreamSupport;

import static com.google.common.collect.ImmutableList.toImmutableList;

public record JmxResponse(List<JmxAttribute> attributes)
{
    public JmxResponse
    {
        attributes = ImmutableList.copyOf(attributes);
    }

    public static JmxResponse fromJson(JsonNode json)
    {
        List<JmxAttribute> attributes = StreamSupport.stream(json.get("attributes").spliterator(), false)
                .map(JmxAttribute::fromJson)
                .collect(toImmutableList());
        return new JmxResponse(attributes);
    }
}
