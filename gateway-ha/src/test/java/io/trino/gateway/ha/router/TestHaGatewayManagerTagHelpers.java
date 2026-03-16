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

import org.junit.jupiter.api.Test;

import java.util.List;

import static io.trino.gateway.ha.router.HaGatewayManager.tagsFromString;
import static io.trino.gateway.ha.router.HaGatewayManager.tagsToString;
import static org.assertj.core.api.Assertions.assertThat;

final class TestHaGatewayManagerTagHelpers
{
    // -----------------------------------------------------------------------
    // tagsToString
    // -----------------------------------------------------------------------

    @Test
    void tagsToStringReturnsNullForNullInput()
    {
        assertThat(tagsToString(null)).isNull();
    }

    @Test
    void tagsToStringReturnsNullForEmptyList()
    {
        assertThat(tagsToString(List.of())).isNull();
    }

    @Test
    void tagsToStringReturnsSingleTag()
    {
        assertThat(tagsToString(List.of("prod"))).isEqualTo("prod");
    }

    @Test
    void tagsToStringJoinsMultipleTagsWithComma()
    {
        assertThat(tagsToString(List.of("prod", "us-east-1", "critical")))
                .isEqualTo("prod,us-east-1,critical");
    }

    // -----------------------------------------------------------------------
    // tagsFromString
    // -----------------------------------------------------------------------

    @Test
    void tagsFromStringReturnsEmptyListForNull()
    {
        assertThat(tagsFromString(null)).isEmpty();
    }

    @Test
    void tagsFromStringReturnsEmptyListForEmptyString()
    {
        assertThat(tagsFromString("")).isEmpty();
    }

    @Test
    void tagsFromStringReturnsEmptyListForBlankString()
    {
        assertThat(tagsFromString("   ")).isEmpty();
    }

    @Test
    void tagsFromStringReturnsSingleTag()
    {
        assertThat(tagsFromString("prod")).containsExactly("prod");
    }

    @Test
    void tagsFromStringParsesMultipleTags()
    {
        assertThat(tagsFromString("prod,us-east-1,critical"))
                .containsExactly("prod", "us-east-1", "critical");
    }

    @Test
    void tagsFromStringTrimsWhitespaceAroundTags()
    {
        assertThat(tagsFromString("prod , us-east-1 , critical"))
                .containsExactly("prod", "us-east-1", "critical");
    }

    @Test
    void tagsFromStringOmitsEmptySegments()
    {
        assertThat(tagsFromString(",prod,,us-east-1,"))
                .containsExactly("prod", "us-east-1");
    }

    @Test
    void tagsToStringAndFromStringAreInverses()
    {
        var original = List.of("prod", "us-east-1", "critical");
        assertThat(tagsFromString(tagsToString(original))).isEqualTo(original);
    }
}
