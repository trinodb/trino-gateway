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
package io.trino.gateway.ha.log;

import io.airlift.log.Logger;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Response;

public class GatewayRequestLog
        implements RequestLog
{
    private static final Logger log = Logger.get(GatewayRequestLog.class);

    @Override
    public void log(Request request, Response response)
    {
        // Logging without filter as both request and response don't contain sensitive information
        log.debug("Request: %s, response: %s", request, response);
    }
}
