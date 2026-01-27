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

/**
 * Configuration for Valkey distributed cache.
 */
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
    private int timeoutMs = 2000;
    private long cacheTtlSeconds = 1800; // 30 minutes default
    private long healthCheckIntervalMs = 30000; // 30 seconds default

    public ValkeyConfiguration() {}

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
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535, got: " + port);
        }
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
        if (maxTotal < 1) {
            throw new IllegalArgumentException("maxTotal must be at least 1, got: " + maxTotal);
        }
        this.maxTotal = maxTotal;
    }

    public int getMaxIdle()
    {
        return maxIdle;
    }

    public void setMaxIdle(int maxIdle)
    {
        if (maxIdle < 0) {
            throw new IllegalArgumentException("maxIdle cannot be negative, got: " + maxIdle);
        }
        this.maxIdle = maxIdle;
    }

    public int getMinIdle()
    {
        return minIdle;
    }

    public void setMinIdle(int minIdle)
    {
        if (minIdle < 0) {
            throw new IllegalArgumentException("minIdle cannot be negative, got: " + minIdle);
        }
        this.minIdle = minIdle;
    }

    public int getTimeoutMs()
    {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs)
    {
        if (timeoutMs < 0) {
            throw new IllegalArgumentException("timeoutMs cannot be negative, got: " + timeoutMs);
        }
        this.timeoutMs = timeoutMs;
    }

    public long getCacheTtlSeconds()
    {
        return cacheTtlSeconds;
    }

    public void setCacheTtlSeconds(long cacheTtlSeconds)
    {
        if (cacheTtlSeconds < 0) {
            throw new IllegalArgumentException("cacheTtlSeconds cannot be negative, got: " + cacheTtlSeconds);
        }
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    public long getHealthCheckIntervalMs()
    {
        return healthCheckIntervalMs;
    }

    public void setHealthCheckIntervalMs(long healthCheckIntervalMs)
    {
        if (healthCheckIntervalMs < 1000) {
            throw new IllegalArgumentException("healthCheckIntervalMs must be at least 1000ms, got: " + healthCheckIntervalMs);
        }
        this.healthCheckIntervalMs = healthCheckIntervalMs;
    }
}
