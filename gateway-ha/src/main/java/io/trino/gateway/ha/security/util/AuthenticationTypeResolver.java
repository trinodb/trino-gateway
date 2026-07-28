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

import java.util.List;

public final class AuthenticationTypeResolver
{
    private static final Logger log = Logger.get(AuthenticationTypeResolver.class);

    private AuthenticationTypeResolver() {}

    /**
     * Resolves the ordered list of authentication types that are both requested in
     * {@code authentication.defaultTypes} and actually backed by a configured manager.
     *
     * <p>Every configured value is validated against {@link AuthenticationType}: an
     * unknown or misspelled entry throws immediately, so the misconfiguration surfaces
     * at startup instead of being silently ignored. Known types that are listed without
     * a matching configuration block are logged and skipped. If nothing usable remains,
     * an exception is thrown so an empty chain doesn't 403 every later request.
     *
     * @param defaultTypes the configured {@code authentication.defaultTypes} list
     * @param oauthConfigured whether an oauth manager is configured (i.e. an {@code oauth} block exists)
     * @param formConfigured whether a form manager is configured (i.e. a {@code form} block exists)
     * @return the ordered, non-empty list of usable authentication types
     */
    public static List<AuthenticationType> resolveEffectiveTypes(List<String> defaultTypes, boolean oauthConfigured, boolean formConfigured)
    {
        if (defaultTypes == null || defaultTypes.isEmpty()) {
            throw new IllegalArgumentException("authentication.defaultTypes must list at least one authentication type");
        }

        ImmutableList.Builder<AuthenticationType> effectiveTypes = ImmutableList.builder();
        for (String rawType : defaultTypes) {
            AuthenticationType authType = AuthenticationType.fromValue(rawType);
            boolean configured = switch (authType) {
                case OAUTH -> oauthConfigured;
                case FORM -> formConfigured;
            };
            if (configured) {
                effectiveTypes.add(authType);
            }
            else {
                log.warn("authentication.defaultTypes lists \"%s\" but no matching authentication.%s block is configured; skipping it", authType.value(), authType.value());
            }
        }

        List<AuthenticationType> resolved = effectiveTypes.build();
        if (resolved.isEmpty()) {
            throw new IllegalStateException("No usable authentication methods configured; authentication.defaultTypes=%s but none had a matching configuration block".formatted(defaultTypes));
        }
        return resolved;
    }
}
