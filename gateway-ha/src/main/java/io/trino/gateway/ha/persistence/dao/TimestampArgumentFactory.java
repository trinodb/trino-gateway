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
package io.trino.gateway.ha.persistence.dao;

import org.jdbi.v3.core.argument.AbstractArgumentFactory;
import org.jdbi.v3.core.argument.Argument;
import org.jdbi.v3.core.config.ConfigRegistry;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Argument factory for converting String timestamps to SQL Timestamps.
 */
public class TimestampArgumentFactory
        extends AbstractArgumentFactory<String>
{
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public TimestampArgumentFactory()
    {
        super(Types.TIMESTAMP);
    }

    @Override
    protected Argument build(String value, ConfigRegistry config)
    {
        if (value == null) {
            return (position, statement, ctx) -> statement.setNull(position, Types.TIMESTAMP);
        }

        LocalDateTime dateTime = LocalDateTime.parse(value, FORMATTER);
        Timestamp timestamp = Timestamp.valueOf(dateTime);

        return (position, statement, ctx) -> statement.setTimestamp(position, timestamp);
    }
}
