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

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

public enum UserMappingCase
{
    KEEP,
    LOWER,
    UPPER;

    @JsonCreator
    public static UserMappingCase fromJson(String value)
    {
        return valueOf(value.trim().toUpperCase(Locale.ENGLISH));
    }

    public String transform(String value)
    {
        return switch (this) {
            case KEEP -> value;
            case LOWER -> value.toLowerCase(Locale.ENGLISH);
            case UPPER -> value.toUpperCase(Locale.ENGLISH);
        };
    }
}
