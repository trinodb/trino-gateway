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
package io.trino.gateway.ha.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

final class TestQueryMetadata
{
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void testJsonSerializationComplete()
            throws Exception
    {
        QueryMetadata original = new QueryMetadata("backend1", "group1", "http://external1");

        String json = OBJECT_MAPPER.writeValueAsString(original);
        QueryMetadata deserialized = OBJECT_MAPPER.readValue(json, QueryMetadata.class);

        assertThat(deserialized).isEqualTo(original);
        assertThat(deserialized.backend()).isEqualTo("backend1");
        assertThat(deserialized.routingGroup()).isEqualTo("group1");
        assertThat(deserialized.externalUrl()).isEqualTo("http://external1");
    }

    @Test
    void testJsonSerializationWithNullFields()
            throws Exception
    {
        QueryMetadata original = new QueryMetadata("backend1", null, null);

        String json = OBJECT_MAPPER.writeValueAsString(original);
        QueryMetadata deserialized = OBJECT_MAPPER.readValue(json, QueryMetadata.class);

        assertThat(deserialized).isEqualTo(original);
        assertThat(deserialized.backend()).isEqualTo("backend1");
        assertThat(deserialized.routingGroup()).isNull();
        assertThat(deserialized.externalUrl()).isNull();
    }

    @Test
    void testJsonSerializationAllNull()
            throws Exception
    {
        QueryMetadata original = new QueryMetadata(null, null, null);

        String json = OBJECT_MAPPER.writeValueAsString(original);
        QueryMetadata deserialized = OBJECT_MAPPER.readValue(json, QueryMetadata.class);

        assertThat(deserialized).isEqualTo(original);
        assertThat(deserialized.backend()).isNull();
        assertThat(deserialized.routingGroup()).isNull();
        assertThat(deserialized.externalUrl()).isNull();
    }

    @Test
    void testJsonFormat()
            throws Exception
    {
        QueryMetadata metadata = new QueryMetadata("backend1", "group1", "http://external1");
        String json = OBJECT_MAPPER.writeValueAsString(metadata);

        // Verify JSON contains expected fields
        assertThat(json).contains("\"backend\"");
        assertThat(json).contains("\"routingGroup\"");
        assertThat(json).contains("\"externalUrl\"");
        assertThat(json).contains("\"backend1\"");
        assertThat(json).contains("\"group1\"");
        assertThat(json).contains("\"http://external1\"");
    }

    @Test
    void testMerge()
    {
        QueryMetadata base = new QueryMetadata("backend1", "group1", null);
        QueryMetadata update = QueryMetadata.withExternalUrl("http://external1");

        QueryMetadata merged = base.merge(update);

        assertThat(merged.backend()).isEqualTo("backend1");
        assertThat(merged.routingGroup()).isEqualTo("group1");
        assertThat(merged.externalUrl()).isEqualTo("http://external1");
    }

    @Test
    void testMergeOverwrite()
    {
        QueryMetadata base = new QueryMetadata("backend1", "group1", "http://external1");
        QueryMetadata update = new QueryMetadata("backend2", null, null);

        QueryMetadata merged = base.merge(update);

        assertThat(merged.backend()).isEqualTo("backend2");
        assertThat(merged.routingGroup()).isEqualTo("group1");
        assertThat(merged.externalUrl()).isEqualTo("http://external1");
    }

    @Test
    void testMergeAllFields()
    {
        QueryMetadata base = new QueryMetadata("backend1", null, null);
        QueryMetadata update = new QueryMetadata("backend2", "group2", "http://external2");

        QueryMetadata merged = base.merge(update);

        assertThat(merged.backend()).isEqualTo("backend2");
        assertThat(merged.routingGroup()).isEqualTo("group2");
        assertThat(merged.externalUrl()).isEqualTo("http://external2");
    }

    @Test
    void testStaticFactories()
    {
        QueryMetadata withBackend = QueryMetadata.withBackend("backend1");
        assertThat(withBackend.backend()).isEqualTo("backend1");
        assertThat(withBackend.routingGroup()).isNull();
        assertThat(withBackend.externalUrl()).isNull();

        QueryMetadata withRoutingGroup = QueryMetadata.withRoutingGroup("group1");
        assertThat(withRoutingGroup.backend()).isNull();
        assertThat(withRoutingGroup.routingGroup()).isEqualTo("group1");
        assertThat(withRoutingGroup.externalUrl()).isNull();

        QueryMetadata withExternalUrl = QueryMetadata.withExternalUrl("http://external1");
        assertThat(withExternalUrl.backend()).isNull();
        assertThat(withExternalUrl.routingGroup()).isNull();
        assertThat(withExternalUrl.externalUrl()).isEqualTo("http://external1");
    }

    @Test
    void testIsEmpty()
    {
        assertThat(new QueryMetadata(null, null, null).isEmpty()).isTrue();
        assertThat(new QueryMetadata("backend1", null, null).isEmpty()).isFalse();
        assertThat(new QueryMetadata(null, "group1", null).isEmpty()).isFalse();
        assertThat(new QueryMetadata(null, null, "http://external1").isEmpty()).isFalse();
    }

    @Test
    void testIsComplete()
    {
        assertThat(new QueryMetadata("backend1", "group1", "http://external1").isComplete()).isTrue();
        assertThat(new QueryMetadata("backend1", "group1", null).isComplete()).isFalse();
        assertThat(new QueryMetadata("backend1", null, "http://external1").isComplete()).isFalse();
        assertThat(new QueryMetadata(null, "group1", "http://external1").isComplete()).isFalse();
        assertThat(new QueryMetadata(null, null, null).isComplete()).isFalse();
    }

    @Test
    void testEqualsAndHashCode()
    {
        QueryMetadata metadata1 = new QueryMetadata("backend1", "group1", "http://external1");
        QueryMetadata metadata2 = new QueryMetadata("backend1", "group1", "http://external1");
        QueryMetadata metadata3 = new QueryMetadata("backend2", "group1", "http://external1");

        assertThat(metadata1).isEqualTo(metadata2);
        assertThat(metadata1.hashCode()).isEqualTo(metadata2.hashCode());
        assertThat(metadata1).isNotEqualTo(metadata3);
    }
}
