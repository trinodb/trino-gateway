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
package io.trino.gateway.ha.domain.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.trino.gateway.ha.router.ResourceGroupsManager;

/**
 * Parameters for adding, modifying, and deleting Selectors.
 *
 * @param useSchema Optional, defaults to the Schema of the configuration file.
 * @param data This field is used for adding, modifying, and deleting.
 * @param oldData This field is only used for modification.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SelectorsRequest(
        String useSchema,
        ResourceGroupsManager.SelectorsDetail data,
        ResourceGroupsManager.SelectorsDetail oldData)
{
}
