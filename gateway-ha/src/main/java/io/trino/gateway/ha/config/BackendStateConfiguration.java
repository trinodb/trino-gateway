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

public class BackendStateConfiguration
{
    private String username;
    private String password = "";
    private Boolean ssl = false;

    public BackendStateConfiguration() {}

    public String getUsername()
    {return this.username;}

    public void setUsername(String username)
    {this.username = username;}

    public String getPassword()
    {return this.password;}

    public void setPassword(String password)
    {this.password = password;}

    public Boolean getSsl()
    {return this.ssl;}

    public void setSsl(Boolean ssl)
    {this.ssl = ssl;}

    public boolean equals(final Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof BackendStateConfiguration)) {
            return false;
        }
        final BackendStateConfiguration other = (BackendStateConfiguration) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$username = this.getUsername();
        final Object other$username = other.getUsername();
        if (this$username == null ? other$username != null : !this$username.equals(other$username)) {
            return false;
        }
        final Object this$password = this.getPassword();
        final Object other$password = other.getPassword();
        if (this$password == null ? other$password != null : !this$password.equals(other$password)) {
            return false;
        }
        final Object this$ssl = this.getSsl();
        final Object other$ssl = other.getSsl();
        if (this$ssl == null ? other$ssl != null : !this$ssl.equals(other$ssl)) {
            return false;
        }
        return true;
    }

    protected boolean canEqual(final Object other)
    {return other instanceof BackendStateConfiguration;}

    public int hashCode()
    {
        final int PRIME = 59;
        int result = 1;
        final Object $username = this.getUsername();
        result = result * PRIME + ($username == null ? 43 : $username.hashCode());
        final Object $password = this.getPassword();
        result = result * PRIME + ($password == null ? 43 : $password.hashCode());
        final Object $ssl = this.getSsl();
        result = result * PRIME + ($ssl == null ? 43 : $ssl.hashCode());
        return result;
    }

    @Override
    public String toString()
    {
        return "BackendStateConfiguration{" +
                "username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", ssl=" + ssl +
                '}';
    }
}
