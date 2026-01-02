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

import io.airlift.configuration.validation.FileExists;

import java.io.File;
import java.util.Optional;

public class JwtConfiguration
{
    private String keyFile;
    private String requiredIssuer;
    private String requiredAudience;
    private String principalField = "sub";
    private Optional<String> userMappingPattern = Optional.empty();
    private Optional<File> userMappingFile = Optional.empty();

    public JwtConfiguration() {}

    public String getKeyFile()
    {
        return this.keyFile;
    }

    public void setKeyFile(String keyFile)
    {
        this.keyFile = keyFile;
    }

    public String getRequiredIssuer()
    {
        return this.requiredIssuer;
    }

    public void setRequiredIssuer(String requiredIssuer)
    {
        this.requiredIssuer = requiredIssuer;
    }

    public String getRequiredAudience()
    {
        return this.requiredAudience;
    }

    public void setRequiredAudience(String requiredAudience)
    {
        this.requiredAudience = requiredAudience;
    }

    public String getPrincipalField()
    {
        return this.principalField;
    }

    public void setPrincipalField(String principalField)
    {
        this.principalField = principalField;
    }

    public Optional<String> getUserMappingPattern()
    {
        return this.userMappingPattern;
    }

    public void setUserMappingPattern(String userMappingPattern)
    {
        this.userMappingPattern = Optional.ofNullable(userMappingPattern);
    }

    public Optional<@FileExists File> getUserMappingFile()
    {
        return this.userMappingFile;
    }

    public void setUserMappingFile(File userMappingFile)
    {
        this.userMappingFile = Optional.ofNullable(userMappingFile);
    }
}
