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
package io.trino.gateway.ha.security;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestUserMapping
{
    @Test
    void testDefaultIdentityMapping()
            throws Exception
    {
        UserMapping userMapping = UserMapping.createUserMapping(Optional.empty(), Optional.empty());

        assertThat(userMapping.mapUser("alice@example.com")).isEqualTo("alice@example.com");
    }

    @Test
    void testSinglePatternMapping()
            throws Exception
    {
        UserMapping userMapping = UserMapping.createUserMapping(Optional.of("(.*?)@.*"), Optional.empty());

        assertThat(userMapping.mapUser("alice@example.com")).isEqualTo("alice");
    }

    @Test
    void testFileMapping()
            throws Exception
    {
        UserMapping userMapping = UserMapping.createUserMapping(Optional.empty(), Optional.of("src/test/resources/auth/test-user-mapping.json"));

        assertThat(userMapping.mapUser("alice@example.com")).isEqualTo("alice_file");
    }

    @Test
    void testDeniedPrincipal()
    {
        UserMapping userMapping = new UserMapping(ImmutableList.of(new UserMapping.Rule("(.*?)@.*", "$1", false, UserMappingCase.KEEP)));

        assertThatThrownBy(() -> userMapping.mapUser("alice@example.com"))
                .isInstanceOf(UserMappingException.class)
                .hasMessage("Principal is not allowed");
    }

    @Test
    void testLowerAndUpperCase()
            throws Exception
    {
        UserMapping lower = new UserMapping(ImmutableList.of(new UserMapping.Rule("(.*)@EXAMPLE\\.COM", "$1", true, UserMappingCase.LOWER)));
        UserMapping upper = new UserMapping(ImmutableList.of(new UserMapping.Rule("(.*)@example\\.com", "$1", true, UserMappingCase.UPPER)));

        assertThat(lower.mapUser("ALICE@EXAMPLE.COM")).isEqualTo("alice");
        assertThat(upper.mapUser("alice@example.com")).isEqualTo("ALICE");
    }

    @Test
    void testNoMatchAndEmptyMappedUser()
    {
        UserMapping noMatch = new UserMapping(ImmutableList.of(new UserMapping.Rule("(.*?)@.*")));
        UserMapping emptyMappedUser = new UserMapping(ImmutableList.of(new UserMapping.Rule("(.*?)@.*", "  ", true, UserMappingCase.KEEP)));

        assertThatThrownBy(() -> noMatch.mapUser("alice"))
                .isInstanceOf(UserMappingException.class)
                .hasMessage("No user mapping patterns match the principal");
        assertThatThrownBy(() -> emptyMappedUser.mapUser("alice@example.com"))
                .isInstanceOf(UserMappingException.class)
                .hasMessage("Principal matched, but mapped user is empty");
    }

    @Test
    void testPatternAndFileAreMutuallyExclusive()
    {
        assertThatThrownBy(() -> UserMapping.createUserMapping(
                Optional.of("(.*?)@.*"),
                Optional.of("src/test/resources/auth/test-user-mapping.json")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("user mapping pattern and file can not both be set");
    }
}
