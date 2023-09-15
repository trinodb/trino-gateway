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

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.IdName;
import org.javalite.activejdbc.annotations.Table;

@IdName("cookie")
@Table("cookie_backend_lookup")
public class CookieBackend
        extends Model
{
    public static final String cookie = "cookie";
    public static final String backend = "backend";
    public static final String createdTimestamp = "created_timestamp";

    public static void create(
            CookieBackend model,
            String cookie,
            String backend)
    {
        model.set(CookieBackend.cookie, cookie);
        model.set(CookieBackend.backend, backend);
        model.set(CookieBackend.createdTimestamp, System.currentTimeMillis());
        model.insert();
    }
}
