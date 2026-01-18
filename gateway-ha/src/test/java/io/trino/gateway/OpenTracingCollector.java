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
package io.trino.gateway;

import io.airlift.units.DataSize;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static io.airlift.units.DataSize.Unit.GIGABYTE;

public class OpenTracingCollector
        implements AutoCloseable
{
    private static final int COLLECTOR_PORT = 4318;
    private static final int HTTP_PORT = 16686;

    private final GenericContainer<?> container;
    private final Path storageDirectory;

    public OpenTracingCollector()
    {
        container = new GenericContainer<>(DockerImageName.parse("jaegertracing/all-in-one:latest"));
        container.setPortBindings(List.of("%1$s:%1$s".formatted(COLLECTOR_PORT), "%1$s:%1$s".formatted(HTTP_PORT)));
        container.addEnv("COLLECTOR_OTLP_ENABLED", "true");
        container.addEnv("SPAN_STORAGE_TYPE", "badger"); // KV that stores spans to the disk
        container.addEnv("GOMAXPROCS", "2"); // limit number of threads used for goroutines
        container.withCommand("--badger.ephemeral=false",
                "--badger.span-store-ttl=15m",
                "--badger.directory-key=/badger/data",
                "--badger.directory-value=/badger/data",
                "--badger.maintenance-interval=30s");
        container.withCreateContainerCmdModifier(command -> command.getHostConfig()
                .withMemory(DataSize.of(1, GIGABYTE).toBytes()));

        try {
            this.storageDirectory = Files.createTempDirectory("tracing-collector");
            container.addFileSystemBind(storageDirectory.toString(), "/badger", BindMode.READ_WRITE);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void start()
    {
        container.start();
    }

    @Override
    public void close()
    {
        container.close();
        try (Stream<File> files = Files.walk(storageDirectory)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)) {
            files.forEach(File::delete);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
