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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.trino.sql.tree.Expression;
import io.trino.sql.tree.StringLiteral;

import java.util.Objects;

import static java.lang.Class.forName;
import static java.util.Objects.requireNonNull;

public class SerializableExpression
{
    private final String value;
    private final Class<?> originalClass;

    @JsonCreator
    public SerializableExpression(@JsonProperty("value") String value, @JsonProperty("originalClass") String originalClass)
    {
        this.value = requireNonNull(value);
        try {
            this.originalClass = forName(requireNonNull(originalClass));
        }
        catch (ClassNotFoundException e) {
            throw new RuntimeException("Original class name provided by SerializableExpression not found", e);
        }
    }

    public SerializableExpression(Expression expression)
    {
        originalClass = expression.getClass();
        if (expression instanceof StringLiteral stringLiteral) {
            // special handling for this common case so that quotes do not need to be stripped
            value = stringLiteral.getValue();
        }
        else {
            value = expression.toString();
        }
    }

    @JsonProperty
    public String getValue()
    {
        return value;
    }

    @JsonProperty
    public Class<?> getOriginalClass()
    {
        return originalClass;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SerializableExpression that = (SerializableExpression) o;
        return Objects.equals(value, that.value) && Objects.equals(originalClass, that.originalClass);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(value, originalClass);
    }
}
