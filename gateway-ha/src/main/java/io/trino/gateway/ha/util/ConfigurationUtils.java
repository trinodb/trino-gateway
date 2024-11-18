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
package io.trino.gateway.ha.util;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static java.util.regex.Matcher.quoteReplacement;

public final class ConfigurationUtils
{
    private static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{ENV:([a-zA-Z][a-zA-Z0-9_-]*)}");

    private ConfigurationUtils() {}

    public static String replaceEnvironmentVariables(String original)
    {
        return replaceEnvironmentVariables(original, System.getenv());
    }

    public static String replaceEnvironmentVariables(String original, Map<String, String> environment)
    {
        StringBuilder result = new StringBuilder();
        Matcher matcher = ENV_PATTERN.matcher(original);
        while (matcher.find()) {
            String envName = matcher.group(1);
            String envValue = environment.get(envName);
            if (envValue == null) {
                throw new IllegalArgumentException(format("Configuration references unset environment variable '%s'", envName));
            }
            matcher.appendReplacement(result, quoteReplacement(envValue));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
