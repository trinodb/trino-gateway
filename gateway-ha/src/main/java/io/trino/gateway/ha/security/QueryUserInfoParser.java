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

package io.trino.gateway.ha.security;

import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.config.RequestAnalyzerConfig;
import io.trino.gateway.ha.router.PathFilter;
import io.trino.gateway.ha.router.TrinoRequestUser;
import io.trino.gateway.ha.security.util.GatewayFilterPriorities;
import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;

import java.io.IOException;

import static io.trino.gateway.ha.handler.HttpUtils.TRINO_REQUEST_USER;

/**
 * A filter which parses and extracts Trino user identity from incoming request headers
 * and stores it in the request context property TRINO_REQUEST_USER
 * for downstream filters and handlers to use.
 */

@PreMatching
@Priority(GatewayFilterPriorities.PRE_AUTHENTICATION)
public class QueryUserInfoParser
        implements ContainerRequestFilter
{
    private static final Logger log = Logger.get(QueryUserInfoParser.class);
    private final String tokenUserField;
    private final String oauthTokenInfoUrl;
    private final PathFilter pathFilter;

    @Inject
    public QueryUserInfoParser(HaGatewayConfiguration config, PathFilter pathFilter)
    {
        RequestAnalyzerConfig requestAnalyzerConfig = config.getRequestAnalyzerConfig();
        this.tokenUserField = requestAnalyzerConfig.getTokenUserField();
        this.oauthTokenInfoUrl = requestAnalyzerConfig.getOauthTokenInfoUrl();
        this.pathFilter = pathFilter;
    }

    @Override
    public void filter(ContainerRequestContext requestContext)
            throws IOException
    {
        String path = requestContext.getUriInfo().getRequestUri().getPath();
        if (!pathFilter.isPathWhiteListed(path)) {
            return;
        }

        RequestAnalyzerConfig requestAnalyzerConfig = new RequestAnalyzerConfig();
        requestAnalyzerConfig.setTokenUserField(tokenUserField);
        requestAnalyzerConfig.setOauthTokenInfoUrl(oauthTokenInfoUrl);

        TrinoRequestUser user = new TrinoRequestUser.TrinoRequestUserProvider(requestAnalyzerConfig).getInstance(requestContext);
        requestContext.setProperty(TRINO_REQUEST_USER, user);
        log.debug("Parsed user %s", user.getUser().orElse("None"));
    }
}
