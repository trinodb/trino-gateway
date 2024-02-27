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

import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.ImageNameSubstitutor;

/**
 * We can use this class to override the images used by test containers, if the image has
 * a different name from dockerhub. This class is used in testcontainers.properties file.
 * TESTCONTAINERS_TRINO_IMAGE_SUBSTITUTE can be set as an environment variable.
 */
public class CustomTrinoImageNameSubstitutor
        extends ImageNameSubstitutor
{
    private static final String TESTCONTAINERS_TRINO_IMAGE_SUBSTITUTE = "TESTCONTAINERS_TRINO_IMAGE_SUBSTITUTE";
    private static final String TRINO_IMAGE = "trinodb/trino";

    @Override
    public DockerImageName apply(DockerImageName dockerImageName)
    {
        String trinoImageSubstitute = System.getenv(TESTCONTAINERS_TRINO_IMAGE_SUBSTITUTE);
        if (dockerImageName.getUnversionedPart().equals(TRINO_IMAGE) && trinoImageSubstitute != null) {
            return DockerImageName.parse(trinoImageSubstitute).asCompatibleSubstituteFor(TRINO_IMAGE);
        }
        return dockerImageName;
    }

    @Override
    protected String getDescription()
    {
        return "custom Trino image name substitutor";
    }
}
