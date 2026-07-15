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
package io.trino.gateway.ha.security.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static io.trino.gateway.ha.security.util.AuthenticationTypeResolver.resolveEffectiveTypes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class TestAuthenticationTypeResolver
{
    @Test
    void testReturnsConfiguredTypesInOrder()
    {
        assertThat(resolveEffectiveTypes(List.of("oauth", "form"), true, true))
                .containsExactly("oauth", "form");
        assertThat(resolveEffectiveTypes(List.of("form", "oauth"), true, true))
                .containsExactly("form", "oauth");
    }

    @Test
    void testSkipsTypeWithoutConfiguredManager()
    {
        // oauth is listed first but not configured; it must be filtered out so the UI
        // never advertises a method that would lead to a dead login button.
        assertThat(resolveEffectiveTypes(List.of("oauth", "form"), false, true))
                .containsExactly("form");
        assertThat(resolveEffectiveTypes(List.of("oauth", "form"), true, false))
                .containsExactly("oauth");
    }

    @Test
    void testSkipsUnknownTypesButKeepsValidOnes()
    {
        assertThat(resolveEffectiveTypes(List.of("oath", "form"), true, true))
                .containsExactly("form");
    }

    @Test
    void testThrowsWhenNullOrEmpty()
    {
        assertThatThrownBy(() -> resolveEffectiveTypes(null, true, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolveEffectiveTypes(List.of(), true, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testThrowsWhenNoUsableTypesRemain()
    {
        // every listed type is either unknown or has no configured manager, so the
        // chain would be empty; fail fast instead of 403-ing every later request.
        assertThatThrownBy(() -> resolveEffectiveTypes(List.of("oauth"), false, false))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> resolveEffectiveTypes(List.of("oath"), true, true))
                .isInstanceOf(IllegalStateException.class);
    }
}
