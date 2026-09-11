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

import static io.trino.gateway.ha.config.AuthenticationType.FORM;
import static io.trino.gateway.ha.config.AuthenticationType.OAUTH;
import static io.trino.gateway.ha.security.util.AuthenticationTypeResolver.resolveChainTypes;
import static io.trino.gateway.ha.security.util.AuthenticationTypeResolver.resolveEffectiveTypes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class TestAuthenticationTypeResolver
{
    @Test
    void testReturnsConfiguredTypesInOrder()
    {
        assertThat(resolveEffectiveTypes(List.of("oauth", "form"), true, true))
                .containsExactly(OAUTH, FORM);
        assertThat(resolveEffectiveTypes(List.of("form", "oauth"), true, true))
                .containsExactly(FORM, OAUTH);
    }

    @Test
    void testDeduplicatesRepeatedTypes()
    {
        // Duplicates would otherwise register a filter twice and make /loginType return
        // duplicate values that collide as React keys / RadioGroup values on the login page.
        assertThat(resolveEffectiveTypes(List.of("form", "form"), true, true))
                .containsExactly(FORM);
        assertThat(resolveEffectiveTypes(List.of("oauth", "form", "oauth"), true, true))
                .containsExactly(OAUTH, FORM);
    }

    @Test
    void testSkipsTypeWithoutConfiguredManager()
    {
        // oauth is listed first but not configured; it must be filtered out so the UI
        // never advertises a method that would lead to a dead login button.
        assertThat(resolveEffectiveTypes(List.of("oauth", "form"), false, true))
                .containsExactly(FORM);
        assertThat(resolveEffectiveTypes(List.of("oauth", "form"), true, false))
                .containsExactly(OAUTH);
    }

    @Test
    void testThrowsOnUnknownType()
    {
        // A misspelled or unsupported type must fail fast at startup rather than be silently skipped.
        assertThatThrownBy(() -> resolveEffectiveTypes(List.of("oath", "form"), true, true))
                .isInstanceOf(IllegalArgumentException.class);
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
        // every listed type is known but has no configured manager, so the chain would be
        // empty; fail fast instead of 403-ing every later request.
        assertThatThrownBy(() -> resolveEffectiveTypes(List.of("oauth"), false, false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testChainAcceptsConfiguredButUnlistedMethod()
    {
        // The regression this fix targets: defaultType lists only oauth, but a form block is
        // also configured. form/basic must stay accepted (appended after oauth) so upgrading an
        // existing deployment does not silently drop automation's Basic auth.
        assertThat(resolveChainTypes(List.of("oauth"), true, true))
                .containsExactly(OAUTH, FORM);
        assertThat(resolveChainTypes(List.of("form"), true, true))
                .containsExactly(FORM, OAUTH);
    }

    @Test
    void testChainHonorsListedOrderAndDeduplicates()
    {
        assertThat(resolveChainTypes(List.of("oauth", "form"), true, true))
                .containsExactly(OAUTH, FORM);
        assertThat(resolveChainTypes(List.of("form", "form"), true, true))
                .containsExactly(FORM, OAUTH);
    }

    @Test
    void testChainKeepsConfiguredMethodWhenListedTypeIsUnconfigured()
    {
        // oauth is listed but has no manager; form is configured though unlisted. The chain
        // still accepts form so the gateway remains usable.
        assertThat(resolveChainTypes(List.of("oauth"), false, true))
                .containsExactly(FORM);
    }

    @Test
    void testChainThrowsOnUnknownType()
    {
        assertThatThrownBy(() -> resolveChainTypes(List.of("oath"), true, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testChainThrowsWhenNullOrEmpty()
    {
        assertThatThrownBy(() -> resolveChainTypes(null, true, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolveChainTypes(List.of(), true, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testChainThrowsWhenNoManagerConfigured()
    {
        // Nothing is configured, so the chain would be empty and 403 every request: fail fast.
        assertThatThrownBy(() -> resolveChainTypes(List.of("oauth"), false, false))
                .isInstanceOf(IllegalStateException.class);
    }
}
