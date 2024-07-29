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
import io.trino.sql.tree.CallArgument;
import io.trino.sql.tree.Identifier;

import java.util.Objects;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

// CallArgument is final, so this class just replicates it. Location is not preserved, since it would require
// additional complexity and is not meaningful in this context
public class SerializableCallArgument
{
    private final Optional<String> name;
    private final SerializableExpression value;

    @JsonCreator
    public SerializableCallArgument(
            @JsonProperty("name") Optional<String> name,
            @JsonProperty("value") SerializableExpression value)
    {
        this.name = requireNonNull(name, "name is null");
        this.value = requireNonNull(value, "value is null");
    }

    public SerializableCallArgument(CallArgument callArgument)
    {
        this(requireNonNull(callArgument, "callArgument is null").getName().map(Identifier::getValue), new SerializableExpression(callArgument.getValue()));
    }

    @JsonProperty
    public Optional<String> getName()
    {
        return name;
    }

    @JsonProperty
    public SerializableExpression getValue()
    {
        return value;
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
        SerializableCallArgument that = (SerializableCallArgument) o;
        return Objects.equals(name, that.name) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(name, value);
    }
}
