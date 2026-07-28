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

import java.util.List;

public final class AuthenticationTypeResolver
{
    public static final String OAUTH_TYPE = "oauth";
    public static final String FORM_TYPE = "form";

    private static final Logger log = Logger.get(AuthenticationTypeResolver.class);

    private AuthenticationTypeResolver() {}

    /**
     * Resolves the ordered list of authentication types that are both requested in
     * {@code authentication.defaultTypes} and actually backed by a configured manager.
     *
     * <p>Unknown types, and types listed without a matching configuration block, are
     * logged and skipped rather than silently ignored. If nothing usable remains, an
     * exception is thrown so the misconfiguration surfaces at startup instead of
     * causing every request to fail later with an opaque 403.
     *
     * @param defaultTypes the configured {@code authentication.defaultTypes} list
     * @param oauthConfigured whether an oauth manager is configured (i.e. an {@code oauth} block exists)
     * @param formConfigured whether a form manager is configured (i.e. a {@code form} block exists)
     * @return the ordered, non-empty list of usable authentication types
     */
    public static List<String> resolveEffectiveTypes(List<String> defaultTypes, boolean oauthConfigured, boolean formConfigured)
    {
        if (defaultTypes == null || defaultTypes.isEmpty()) {
            throw new IllegalArgumentException("authentication.defaultTypes must list at least one authentication type");
        }

        ImmutableList.Builder<String> effectiveTypes = ImmutableList.builder();
        for (String authType : defaultTypes) {
            switch (authType) {
                case OAUTH_TYPE -> {
                    if (oauthConfigured) {
                        effectiveTypes.add(OAUTH_TYPE);
                    }
                    else {
                        log.warn("authentication.defaultTypes lists \"oauth\" but no authentication.oauth block is configured; skipping it");
                    }
                }
                case FORM_TYPE -> {
                    if (formConfigured) {
                        effectiveTypes.add(FORM_TYPE);
                    }
                    else {
                        log.warn("authentication.defaultTypes lists \"form\" but no authentication.form block is configured; skipping it");
                    }
                }
                default -> log.warn("Ignoring unknown authentication type \"%s\" in authentication.defaultTypes; supported types are \"oauth\" and \"form\"", authType);
            }
        }

        List<String> resolved = effectiveTypes.build();
        if (resolved.isEmpty()) {
            throw new IllegalStateException("No usable authentication methods configured; authentication.defaultTypes=%s but none had a matching configuration block".formatted(defaultTypes));
        }
        return resolved;
    }
}
