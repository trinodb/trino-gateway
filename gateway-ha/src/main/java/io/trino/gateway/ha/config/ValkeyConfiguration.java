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

import io.airlift.units.Duration;

import java.util.concurrent.TimeUnit;

public class ValkeyConfiguration
{
    private boolean enabled;
    private String host = "localhost";
    private int port = 6379;
    private String password;
    private int database;
    private int maxTotal = 20;
    private int maxIdle = 10;
    private int minIdle = 5;
    private Duration timeout = new Duration(2, TimeUnit.SECONDS);
    private Duration cacheTtl = new Duration(30, TimeUnit.MINUTES);

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public String getHost()
    {
        return host;
    }

    public void setHost(String host)
    {
        this.host = host;
    }

    public int getPort()
    {
        return port;
    }

    public void setPort(int port)
    {
        this.port = port;
    }

    public String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    public int getDatabase()
    {
        return database;
    }

    public void setDatabase(int database)
    {
        this.database = database;
    }

    public int getMaxTotal()
    {
        return maxTotal;
    }

    public void setMaxTotal(int maxTotal)
    {
        this.maxTotal = maxTotal;
    }

    public int getMaxIdle()
    {
        return maxIdle;
    }

    public void setMaxIdle(int maxIdle)
    {
        this.maxIdle = maxIdle;
    }

    public int getMinIdle()
    {
        return minIdle;
    }

    public void setMinIdle(int minIdle)
    {
        this.minIdle = minIdle;
    }

    public Duration getTimeout()
    {
        return timeout;
    }

    public void setTimeout(Duration timeout)
    {
        this.timeout = timeout;
    }

    public Duration getCacheTtl()
    {
        return cacheTtl;
    }

    public void setCacheTtl(Duration cacheTtl)
    {
        this.cacheTtl = cacheTtl;
    }
}
