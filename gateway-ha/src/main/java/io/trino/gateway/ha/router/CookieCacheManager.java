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
package io.trino.gateway.ha.router;

import io.trino.gateway.ha.persistence.JdbcConnectionManager;
import io.trino.gateway.ha.persistence.dao.CookieBackend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class CookieCacheManager
{
    private static final Logger log = LoggerFactory.getLogger(CookieCacheManager.class);
    private JdbcConnectionManager connectionManager;

    public CookieCacheManager(JdbcConnectionManager connectionManager)
    {
        this.connectionManager = connectionManager;
    }

    public void submitCookieBackend(String cookie, String backend)
    {
        try {
            connectionManager.open();
            CookieBackend dao = new CookieBackend();
            CookieBackend.create(dao, cookie, backend);
        }
        catch (Exception e) {
            log.warn(String.format("Error saving cookie %s for backend %s: %s", cookie, backend, e));
        }
        finally {
            connectionManager.close();
        }
    }

    public Optional<String> getBackendForCookie(String cookie)
    {
        try {
            connectionManager.open();
            CookieBackend cookieBackend = CookieBackend.findById(cookie);
            if (cookieBackend != null) {
                return Optional.of((String) cookieBackend.get(CookieBackend.backend));
            }
            return Optional.empty();
        }
        finally {
            connectionManager.close();
        }
    }

    public boolean removeCookie(String cookie)
    {
        try {
            connectionManager.open();
            CookieBackend cookieBackend = CookieBackend.findById(cookie);
            return cookieBackend.delete();
        }
        finally {
            connectionManager.close();
        }
    }
}
