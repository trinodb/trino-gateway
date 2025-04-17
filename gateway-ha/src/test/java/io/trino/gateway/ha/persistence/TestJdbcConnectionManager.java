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
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

final class TestJdbcConnectionManager
{
    JdbcConnectionManager connectionManager;
    DataStoreConfiguration db = new DataStoreConfiguration("", "sa", "sa", "", 4, true);

    static Stream<Arguments> provideJdbcUrlAndDatabase()
    {
        return Stream.of(
            Arguments.of("jdbc:h2:/mydb", null, "jdbc:h2:/mydb"),
            Arguments.of("jdbc:h2:/mydb", "newdb", "jdbc:h2:/newdb"),
            Arguments.of("jdbc:mysql://localhost:3306/mydb", null, "jdbc:mysql://localhost:3306/mydb"),
            Arguments.of("jdbc:mysql://localhost:3306/mydb", "newdb", "jdbc:mysql://localhost:3306/newdb"),
            Arguments.of("jdbc:mysql://localhost:3306/mydb?useSSL=false&serverTimezone=Asia/Seoul", "newdb", "jdbc:mysql://localhost:3306/newdb?useSSL=false&serverTimezone=Asia/Seoul"),
            Arguments.of("jdbc:postgresql://localhost:5432/mydb", null, "jdbc:postgresql://localhost:5432/mydb"),
            Arguments.of("jdbc:postgresql://localhost:5432/mydb", "newdb", "jdbc:postgresql://localhost:5432/newdb"),
            Arguments.of("jdbc:postgresql://localhost:5432/mydb?ssl=false&serverTimezone=Asia/Seoul", "newdb", "jdbc:postgresql://localhost:5432/newdb?ssl=false&serverTimezone=Asia/Seoul"),
            Arguments.of("jdbc:oracle:thin:@//localhost:1521/mydb", null, "jdbc:oracle:thin:@//localhost:1521/mydb"),
            Arguments.of("jdbc:oracle:thin:@//localhost:1521/mydb", "newdb", "jdbc:oracle:thin:@//localhost:1521/newdb"),
            Arguments.of("jdbc:oracle:thin:@//localhost:1521/mydb?sessionTimeZone=Asia/Seoul", "newdb", "jdbc:oracle:thin:@//localhost:1521/newdb?sessionTimeZone=Asia/Seoul"));
    }

    @ParameterizedTest
    @MethodSource("provideJdbcUrlAndDatabase")
    public void testBuildJdbcUrl(String inputJdbcUrl, String database, String expectedJdbcUrl)
    {
        db.setJdbcUrl(inputJdbcUrl);
        connectionManager = new JdbcConnectionManager(Jdbi.create(inputJdbcUrl, "sa", "sa"), db);
        String resultJdbcUrl = connectionManager.buildJdbcUrl(database);
        assertThat(resultJdbcUrl).isEqualTo(expectedJdbcUrl);
    }
}
