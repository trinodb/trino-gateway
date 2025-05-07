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
package io.trino.gateway.ha.persistence;

import io.trino.gateway.ha.config.DataStoreConfiguration;

import java.util.Properties;

/**
 * Default JDBC properties provider used as a fallback when no database-specific
 * {@link JdbcPropertiesProvider} supports the given {@link DataStoreConfiguration}.
 *
 * <p>This provider simply sets the basic "user" and "password" properties
 * and should always be the last provider in the list of available providers.
 *
 * <p>If a more specific provider (e.g., for MySQL, Oracle, etc.) supports the configuration,
 * it should be preferred over this basic fallback.
 */
public class DefaultJdbcPropertiesProvider
        implements JdbcPropertiesProvider
{
    @Override
    public boolean supports(DataStoreConfiguration configuration)
    {
        return true;
    }

    @Override
    public Properties getProperties(DataStoreConfiguration configuration)
    {
        Properties properties = new Properties();
        properties.setProperty("user", configuration.getUser());
        properties.setProperty("password", configuration.getPassword());
        return properties;
    }
}
