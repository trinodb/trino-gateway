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
package io.trino.gateway.ha.config;

import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

/**
 * The authentication methods that may be listed in {@code authentication.defaultType}.
 * Each constant carries the lowercase wire value used in configuration and returned by
 * the {@code /loginType} endpoint, so the raw strings live in exactly one place.
 */
public enum AuthenticationType
{
    OAUTH("oauth"),
    FORM("form");

    private final String value;

    AuthenticationType(String value)
    {
        this.value = value;
    }

    public String value()
    {
        return value;
    }

    /**
     * Resolves the enum constant for a configured {@code authentication.defaultType} value,
     * matching case-insensitively and throwing when the value is unknown so misspelled
     * entries fail fast at startup.
     */
    public static AuthenticationType fromValue(String value)
    {
        requireNonNull(value, "authentication type is null");
        for (AuthenticationType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown authentication type \"%s\" in authentication.defaultType; supported types are %s".formatted(value, supportedValues()));
    }

    public static String supportedValues()
    {
        return Stream.of(values())
                .map(type -> "\"" + type.value + "\"")
                .collect(joining(", "));
    }
}
