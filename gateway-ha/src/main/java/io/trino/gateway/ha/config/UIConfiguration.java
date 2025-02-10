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

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class UIConfiguration
{
    private List<String> disablePages;

    @JsonProperty
    public List<String> getDisablePages()
    {
        return disablePages;
    }

    public void setDisablePages(List<String> disablePages)
    {
        this.disablePages = disablePages;
    }
}
