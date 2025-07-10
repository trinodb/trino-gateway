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

import java.security.Principal;
import java.util.Objects;

public class LbPrincipal
        implements Principal
{
    private final String name;
    private final String privileges;

    public LbPrincipal(String name, String privileges)
    {
        this.name = name;
        this.privileges = privileges;
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
        LbPrincipal that = (LbPrincipal) o;
        return name.equals(that.name) && privileges.equals(that.privileges);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(name, privileges);
    }

    @Override
    public String getName()
    {
        return name;
    }

    public String getPrivileges()
    {
        return this.privileges;
    }
}
