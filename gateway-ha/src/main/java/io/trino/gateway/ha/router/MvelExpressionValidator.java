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

/// Rejects MVEL routing-rule expressions that reach reflection, class loading, or the Java
/// runtime and operating system.
///
/// The MVEL `ParserContext` import allow-list in [MVELRoutingRule] only restricts bare
/// class-name references. It does not restrict method or property access on in-scope objects, so
/// an expression pivots to arbitrary code through reflection, for example
/// `"".getClass().forName("java.lang.Runtime").getMethod("getRuntime")...`. Blocking the
/// identifiers below removes every route to a [Class] object and the usual reflective gadgets.
///
/// MVEL also resolves a fully qualified class name such as `java.io.File` regardless of the
/// imports, and an `import` statement in the expression adds to them. Both are rejected, so an
/// expression can only use the classes imported by [MVELRoutingRule]. A qualified name of an
/// imported class, such as `java.util.Optional`, remains allowed.
///
/// The check runs when a rule is loaded or submitted through the admin API, not when a request is
/// routed. Request routing evaluates the already-compiled expression, so validation adds no
/// per-request cost, and the scan itself is a few linear passes over the expression string.
///
/// This is hardening, not a true sandbox. MVEL expressions are Java-equivalent and MVEL provides
/// no sandbox, so a determined author may find another gadget. Enabling the rules engine grants
/// rule authors host-equivalent trust; gate the ADMIN role accordingly. See
/// `docs/routing-rules.md`.
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
            // import statements, which extend the imports of the parser context
            "import",
            "import_static",
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
            "Thread",
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

    // A package-qualified class name such as java.io.File, or java . io . File with whitespace. A
    // package segment starts with a lowercase letter and the class name with an uppercase letter,
    // so a property chain on a variable such as request.getHeader does not match.
    private static final Pattern QUALIFIED_CLASS_NAME = Pattern.compile(
            "\\b[a-z_][A-Za-z0-9_$]*(\\s*\\.\\s*[a-z_][A-Za-z0-9_$]*)*\\s*\\.\\s*[A-Z][A-Za-z0-9_$]*");

    // Packages and classes imported by MVELRoutingRule, which an expression may also use by their
    // qualified name. Subpackages such as java.util.concurrent are not imported.
    private static final String IMPORTED_PACKAGE = "java.util";
    private static final Set<String> IMPORTED_CLASSES = Set.of(
            "java.lang.Boolean",
            "java.lang.Byte",
            "java.lang.Character",
            "java.lang.Double",
            "java.lang.Enum",
            "java.lang.Exception",
            "java.lang.Float",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Math",
            "java.lang.Short",
            "java.lang.StrictMath",
            "java.lang.String",
            "java.lang.StringBuffer",
            "java.lang.StringBuilder",
            "io.trino.gateway.ha.router.FileBasedRoutingGroupSelector");

    public static void validate(String ruleName, String expression)
    {
        requireNonNull(expression, "expression is null");

        Matcher bracketMatcher = BRACKET_PROPERTY.matcher(expression);
        while (bracketMatcher.find()) {
            checkConstruct(ruleName, bracketMatcher.group(1), !FORBIDDEN_IDENTIFIERS.contains(bracketMatcher.group(1)));
        }

        String withoutLiterals = LITERAL.matcher(expression).replaceAll(" ");
        Matcher identifierMatcher = IDENTIFIER.matcher(withoutLiterals);
        while (identifierMatcher.find()) {
            checkConstruct(ruleName, identifierMatcher.group(), !FORBIDDEN_IDENTIFIERS.contains(identifierMatcher.group()));
        }

        Matcher qualifiedClassNameMatcher = QUALIFIED_CLASS_NAME.matcher(withoutLiterals);
        while (qualifiedClassNameMatcher.find()) {
            String qualifiedClassName = qualifiedClassNameMatcher.group().replaceAll("\\s", "");
            checkConstruct(ruleName, qualifiedClassName, isImported(qualifiedClassName));
        }
    }

    private static boolean isImported(String qualifiedClassName)
    {
        String packageName = qualifiedClassName.substring(0, qualifiedClassName.lastIndexOf('.'));
        return packageName.equals(IMPORTED_PACKAGE) || IMPORTED_CLASSES.contains(qualifiedClassName);
    }

    private static void checkConstruct(String ruleName, String construct, boolean allowed)
    {
        checkArgument(
                allowed,
                "Routing rule '%s' uses forbidden expression construct '%s'. The routing rules engine rejects reflection, class loading, qualified class names, imports, and process or runtime access.",
                requireNonNullElse(ruleName, ""),
                construct);
    }
}
