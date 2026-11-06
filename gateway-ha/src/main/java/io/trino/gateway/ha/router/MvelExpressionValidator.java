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

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

/**
 * Rejects MVEL routing-rule expressions that reach reflection, class loading, or the Java
 * runtime and operating system.
 * <p>
 * The MVEL {@code ParserContext} import allow-list in {@link MVELRoutingRule} only restricts
 * bare class-name references. It does not restrict method or property access on in-scope
 * objects, so an expression pivots to arbitrary code through reflection, for example
 * {@code "".getClass().forName("java.lang.Runtime").getMethod("getRuntime")...}. Blocking the
 * identifiers below removes every route to a {@link Class} object and the usual reflective
 * gadgets.
 * <p>
 * The check runs when a rule is loaded or submitted through the admin API, not when a request is
 * routed. Request routing evaluates the already-compiled expression, so validation adds no
 * per-request cost, and the scan itself is two linear passes over the expression string.
 * <p>
 * This is hardening, not a true sandbox. MVEL expressions are Java-equivalent and MVEL provides
 * no sandbox, so a determined author may find another gadget. Enabling the rules engine grants
 * rule authors host-equivalent trust; gate the ADMIN role accordingly. See
 * {@code docs/routing-rules.md}.
 */
public final class MvelExpressionValidator
{
    private MvelExpressionValidator() {}

    private static final Set<String> FORBIDDEN_IDENTIFIERS = Set.of(
            // every route to a java.lang.Class object (method, keyword-property, and static forms)
            "class",
            "getClass",
            "forName",
            "getClassLoader",
            "loadClass",
            "defineClass",
            // reflective member lookup and invocation
            "getMethod",
            "getMethods",
            "getDeclaredMethod",
            "getDeclaredMethods",
            "getConstructor",
            "getConstructors",
            "getDeclaredConstructor",
            "getDeclaredConstructors",
            "getField",
            "getFields",
            "getDeclaredField",
            "getDeclaredFields",
            "newInstance",
            "invoke",
            "setAccessible",
            // types that embody reflection, class loading, or runtime and operating-system access
            "Class",
            "ClassLoader",
            "Method",
            "Constructor",
            "Field",
            "Runtime",
            "Process",
            "ProcessBuilder",
            "System",
            "Unsafe",
            "MethodHandle",
            "MethodHandles",
            "Lookup",
            "Proxy");

    // String and character literals, stripped before the identifier scan so a forbidden word that
    // only appears as inert literal data (such as a header value) does not trip the check.
    private static final Pattern LITERAL = Pattern.compile("\"(\\\\.|[^\"\\\\])*\"|'(\\\\.|[^'\\\\])*'");

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

    // A forbidden word used as a bracket property key, for example request["class"], which the
    // literal strip above would otherwise hide. Method-call string arguments use parentheses, not
    // brackets, so this does not flag getHeader("class").
    private static final Pattern BRACKET_PROPERTY = Pattern.compile("\\[\\s*[\"']([A-Za-z_$][A-Za-z0-9_$]*)[\"']\\s*]");

    public static void validate(String ruleName, String expression)
    {
        requireNonNull(expression, "expression is null");

        Matcher bracketMatcher = BRACKET_PROPERTY.matcher(expression);
        while (bracketMatcher.find()) {
            reject(ruleName, bracketMatcher.group(1));
        }

        String withoutLiterals = LITERAL.matcher(expression).replaceAll(" ");
        Matcher identifierMatcher = IDENTIFIER.matcher(withoutLiterals);
        while (identifierMatcher.find()) {
            reject(ruleName, identifierMatcher.group());
        }
    }

    private static void reject(String ruleName, String identifier)
    {
        checkArgument(
                !FORBIDDEN_IDENTIFIERS.contains(identifier),
                "Routing rule '%s' uses forbidden expression construct '%s'. The routing rules engine rejects reflection, class loading, and process or runtime access.",
                requireNonNullElse(ruleName, ""),
                identifier);
    }
}
