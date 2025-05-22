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
package io.trino.gateway.proxyserver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import io.airlift.http.client.Request;
import io.airlift.http.client.StaticBodyGenerator;
import io.airlift.log.Logger;
import io.trino.gateway.ha.handler.schema.RoutingDestination;
import io.trino.gateway.ha.router.QueryHistoryManager;
import io.trino.gateway.ha.router.RoutingManager;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;

import static io.trino.gateway.ha.handler.ProxyUtils.QUERY_TEXT_LENGTH_FOR_HISTORY;
import static io.trino.gateway.ha.handler.ProxyUtils.SOURCE_HEADER;
import static io.trino.gateway.ha.resource.EntityEditorResource.OBJECT_MAPPER;
import static jakarta.ws.rs.core.Response.Status.OK;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public class ProxyResponseTransformer
{
    private static final Logger log = Logger.get(ProxyResponseTransformer.class);

    private final QueryHistoryManager queryHistoryManager;
    private final RoutingManager routingManager;

    @Inject
    public ProxyResponseTransformer(
            QueryHistoryManager queryHistoryManager,
            RoutingManager routingManager)
    {
        this.queryHistoryManager = requireNonNull(queryHistoryManager, "queryHistoryManager is null");
        this.routingManager = requireNonNull(routingManager, "routingManager is null");
    }

    public ProxyResponse transform(
            Request request,
            ProxyResponse response,
            @Nullable String username,
            RoutingDestination routingDestination)
    {
        return recordBackendForQueryId(request, response, username, routingDestination);
    }

    private ProxyResponse recordBackendForQueryId(
            Request request,
            ProxyResponse response,
            @Nullable String username,
            RoutingDestination routingDestination)
    {
        log.debug("For Request [%s] got Response [%s]", request.getUri(), response.body());

        QueryHistoryManager.QueryDetail queryDetail = getQueryDetailsFromRequest(request, username);

        log.debug("Extracting proxy destination : [%s] for request : [%s]", queryDetail.getBackendUrl(), request.getUri());

        if (response.statusCode() == OK.getStatusCode()) {
            try {
                HashMap<String, Object> results = OBJECT_MAPPER.readValue(response.body(), new TypeReference<>() {});
                String queryId = (String) results.get("id"); // Extract queryId
                queryDetail.setQueryId(queryId);
                routingManager.setBackendForQueryId(queryDetail.getQueryId(), queryDetail.getBackendUrl());
                routingManager.setRoutingGroupForQueryId(queryDetail.getQueryId(), routingDestination.routingGroup());
                log.debug("QueryId [%s] mapped with proxy [%s]", queryDetail.getQueryId(), queryDetail.getBackendUrl());
            }
            catch (IOException e) {
                log.error(e, "Failed to get QueryId from response [%s] , Status code [%s]", response.body(), response.statusCode());
            }
        }
        else {
            log.error("Non OK HTTP Status code with response [%s] , Status code [%s]", response.body(), response.statusCode());
        }
        queryDetail.setRoutingGroup(routingDestination.routingGroup());
        queryHistoryManager.submitQueryDetail(queryDetail);
        return response;
    }

    public static QueryHistoryManager.QueryDetail getQueryDetailsFromRequest(Request request, @Nullable String username)
    {
        QueryHistoryManager.QueryDetail queryDetail = new QueryHistoryManager.QueryDetail();
        queryDetail.setBackendUrl(getRemoteTarget(request.getUri()));
        queryDetail.setCaptureTime(System.currentTimeMillis());
        if (!Strings.isNullOrEmpty(username)) {
            queryDetail.setUser(username);
        }
        queryDetail.setSource(request.getHeader(SOURCE_HEADER));

        String queryText = new String(((StaticBodyGenerator) request.getBodyGenerator()).getBody(), UTF_8);
        queryDetail.setQueryText(
                queryText.length() > QUERY_TEXT_LENGTH_FOR_HISTORY
                        ? queryText.substring(0, QUERY_TEXT_LENGTH_FOR_HISTORY) + "..."
                        : queryText);
        return queryDetail;
    }

    public static String getRemoteTarget(URI remoteUri)
    {
        return format("%s://%s", remoteUri.getScheme(), remoteUri.getAuthority());
    }
}
