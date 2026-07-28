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

import com.google.common.collect.ImmutableList;
import io.airlift.log.Logger;
import io.trino.gateway.ha.config.AuthenticationType;

import java.util.LinkedHashSet;
import java.util.List;

public final class AuthenticationTypeResolver
{
    private static final Logger log = Logger.get(AuthenticationTypeResolver.class);

    private AuthenticationTypeResolver() {}

    /**
     * Resolves the ordered list of authentication types the {@code ChainedAuthFilter}
     * accepts. Membership follows which managers are actually configured, not the
     * {@code authentication.defaultType} list: every configured method is accepted so that
     * listing a subset can never silently drop a credential type (for example, keeping
     * Basic auth for automation while {@code oauth} is the primary browser login).
     *
     * <p>The {@code defaultType} order is honored first — it sets the fallback priority and
     * the login page's primary method — and any configured method that isn't listed is
     * appended afterwards with a warning. Every listed value is validated against
     * {@link AuthenticationType}, so an unknown or misspelled entry throws at startup, and
     * duplicates are collapsed while preserving order. Throws when no manager is configured
     * at all, since an empty chain would reject every request.
     *
     * @param defaultTypes the configured {@code authentication.defaultType} value
     * @param oauthConfigured whether an oauth manager is configured (i.e. an {@code oauth} block exists)
     * @param formConfigured whether a form manager is configured (i.e. a {@code form} block exists)
     * @return the ordered, non-empty list of authentication types the filter chain accepts
     */
    public static List<AuthenticationType> resolveChainTypes(List<String> defaultTypes, boolean oauthConfigured, boolean formConfigured)
    {
        // Listed-and-configured methods first, in configured order (validated + deduped).
        LinkedHashSet<AuthenticationType> chainTypes = new LinkedHashSet<>(resolveConfiguredListedTypes(defaultTypes, oauthConfigured, formConfigured, false));

        // A configured method is always accepted even when defaultType omits it, so the list
        // can only reorder methods, never remove one the gateway can serve. This keeps
        // upgrades safe: a config with both oauth and form keeps accepting both (humans via
        // SSO, automation via form/basic) even if defaultType names only one.
        appendConfiguredButUnlisted(chainTypes, AuthenticationType.OAUTH, oauthConfigured);
        appendConfiguredButUnlisted(chainTypes, AuthenticationType.FORM, formConfigured);

        if (chainTypes.isEmpty()) {
            throw new IllegalStateException("No authentication methods configured; configure an authentication.oauth and/or authentication.form block (authentication.defaultType=%s)".formatted(defaultTypes));
        }
        return ImmutableList.copyOf(chainTypes);
    }

    /**
     * Resolves the ordered list of authentication methods the login page advertises via
     * {@code /loginType}: the {@code authentication.defaultType} entries that also have a
     * configured manager, validated and deduplicated while preserving order. Unlike
     * {@link #resolveChainTypes}, a configured method that isn't listed is not advertised
     * (the API still accepts it). Throws when nothing listed is usable, so the
     * misconfiguration surfaces at startup instead of rendering an empty or dead login page.
     *
     * @param defaultTypes the configured {@code authentication.defaultType} value
     * @param oauthConfigured whether an oauth manager is configured (i.e. an {@code oauth} block exists)
     * @param formConfigured whether a form manager is configured (i.e. a {@code form} block exists)
     * @return the ordered, non-empty list of authentication types shown on the login page
     */
    public static List<AuthenticationType> resolveEffectiveTypes(List<String> defaultTypes, boolean oauthConfigured, boolean formConfigured)
    {
        List<AuthenticationType> resolved = resolveConfiguredListedTypes(defaultTypes, oauthConfigured, formConfigured, true);
        if (resolved.isEmpty()) {
            throw new IllegalStateException("No usable authentication methods configured; authentication.defaultType=%s but none had a matching configuration block".formatted(defaultTypes));
        }
        return resolved;
    }

    private static List<AuthenticationType> resolveConfiguredListedTypes(List<String> defaultTypes, boolean oauthConfigured, boolean formConfigured, boolean warnOnUnconfigured)
    {
        if (defaultTypes == null || defaultTypes.isEmpty()) {
            throw new IllegalArgumentException("authentication.defaultType must list at least one authentication type");
        }

        // LinkedHashSet keeps the configured order (which drives fallback order and the
        // first-shown login method) while dropping duplicate types.
        LinkedHashSet<AuthenticationType> listedTypes = new LinkedHashSet<>();
        for (String rawType : defaultTypes) {
            AuthenticationType authType = AuthenticationType.fromValue(rawType);
            if (isConfigured(authType, oauthConfigured, formConfigured)) {
                listedTypes.add(authType);
            }
            else if (warnOnUnconfigured) {
                log.warn("authentication.defaultType lists \"%s\" but no matching authentication.%s block is configured; skipping it", authType.value(), authType.value());
            }
        }
        return ImmutableList.copyOf(listedTypes);
    }

    private static void appendConfiguredButUnlisted(LinkedHashSet<AuthenticationType> chainTypes, AuthenticationType type, boolean configured)
    {
        if (configured && chainTypes.add(type)) {
            log.warn("authentication.%s is configured but not listed in authentication.defaultType; it is still accepted by the API (after the listed methods) but is not shown on the login page", type.value());
        }
    }

    private static boolean isConfigured(AuthenticationType type, boolean oauthConfigured, boolean formConfigured)
    {
        return switch (type) {
            case OAUTH -> oauthConfigured;
            case FORM -> formConfigured;
        };
    }
}
