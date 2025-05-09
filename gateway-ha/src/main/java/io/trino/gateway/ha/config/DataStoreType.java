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

import java.sql.Driver;

public enum DataStoreType {
    ORACLE(oracle.jdbc.driver.OracleDriver.class),
    MYSQL(com.mysql.cj.jdbc.Driver.class),
    POSTGRES(org.postgresql.Driver.class),
    H2(org.h2.Driver.class);

    private final Class<? extends Driver> driverClass;

    DataStoreType(Class<? extends Driver> driverClass)
    {
        this.driverClass = driverClass;
    }

    /**
     * Returns the JDBC Driver class associated with this data store type.
     */
    public Class<? extends Driver> getDriverClass()
    {
        return driverClass;
    }
}
