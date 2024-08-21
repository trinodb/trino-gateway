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

import com.google.common.collect.ImmutableSet;
import io.airlift.json.JsonCodec;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

final class TestTrinoQueryProperties
{
    @Test
    void testJsonCreator()
    {
        JsonCodec<TrinoQueryProperties> codec = JsonCodec.jsonCodec(TrinoQueryProperties.class);
        TrinoQueryProperties trinoQueryProperties = new TrinoQueryProperties(
                "SELECT c1 from c.s.t1",
                "SELECT",
                "SELECT",
                new String[] {"c.s.t1"},
                Optional.empty(),
                Optional.empty(),
                ImmutableSet.of("c"),
                ImmutableSet.of("s"),
                ImmutableSet.of("c.s"),
                true,
                true);

        String trinoQueryPropertiesJson = codec.toJson(trinoQueryProperties);
        TrinoQueryProperties deserializedTrinoQueryProperties = codec.fromJson(trinoQueryPropertiesJson);

        assertThat(deserializedTrinoQueryProperties.getBody()).isEqualTo(trinoQueryProperties.getBody());
        assertThat(deserializedTrinoQueryProperties.getQueryType()).isEqualTo(trinoQueryProperties.getQueryType());
        assertThat(deserializedTrinoQueryProperties.getResourceGroupQueryType()).isEqualTo(trinoQueryProperties.getResourceGroupQueryType());
        assertThat(deserializedTrinoQueryProperties.getTables()).isEqualTo(trinoQueryProperties.getTables());
        assertThat(deserializedTrinoQueryProperties.getDefaultCatalog()).isEqualTo(trinoQueryProperties.getDefaultCatalog());
        assertThat(deserializedTrinoQueryProperties.getDefaultSchema()).isEqualTo(trinoQueryProperties.getDefaultSchema());
        assertThat(deserializedTrinoQueryProperties.getSchemas()).isEqualTo(trinoQueryProperties.getSchemas());
        assertThat(deserializedTrinoQueryProperties.getCatalogs()).isEqualTo(trinoQueryProperties.getCatalogs());
        assertThat(deserializedTrinoQueryProperties.getCatalogSchemas()).isEqualTo(trinoQueryProperties.getCatalogSchemas());
        assertThat(deserializedTrinoQueryProperties.isNewQuerySubmission()).isEqualTo(trinoQueryProperties.isNewQuerySubmission());
        assertThat(deserializedTrinoQueryProperties.isQueryParsingSuccessful()).isEqualTo(trinoQueryProperties.isQueryParsingSuccessful());
    }
}
