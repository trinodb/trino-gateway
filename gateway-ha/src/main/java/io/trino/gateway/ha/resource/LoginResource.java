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
package io.trino.gateway.ha.resource;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.trino.gateway.ha.config.HaGatewayConfiguration;
import io.trino.gateway.ha.domain.Result;
import io.trino.gateway.ha.domain.request.RestLoginRequest;
import io.trino.gateway.ha.security.LbFormAuthManager;
import io.trino.gateway.ha.security.LbOAuthManager;
import io.trino.gateway.ha.security.LbPrincipal;
import io.trino.gateway.ha.security.OidcCookie;
import jakarta.annotation.Nullable;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.trino.gateway.ha.security.LbOAuthManager.buildUnauthorizedResponse;
import static io.trino.gateway.ha.security.OidcCookie.OIDC_COOKIE;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class LoginResource
{
    private static final Logger log = Logger.get(LoginResource.class);
    public static final String CALLBACK_ENDPOINT = "oidc/callback";

    private final LbOAuthManager oauthManager;
    private final LbFormAuthManager formAuthManager;
    private final HaGatewayConfiguration haGatewayConfiguration;

    @Inject
    public LoginResource(HaGatewayConfiguration haGatewayConfiguration, @Nullable LbOAuthManager oauthManager, @Nullable LbFormAuthManager formAuthManager)
    {
        this.haGatewayConfiguration = haGatewayConfiguration;
        this.oauthManager = oauthManager;
        this.formAuthManager = formAuthManager;
    }

    @GET
    public Response getRoot()
    {
        return Response.seeOther(URI.create("/trino-gateway")).build();
    }

    @POST
    @Path("sso")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response login()
    {
        if (oauthManager == null) {
            throw new WebApplicationException("OAuth configuration is not setup");
        }
        return oauthManager.getAuthorizationCodeResponse();
    }

    @Path(CALLBACK_ENDPOINT)
    @GET
    public Response callback(
            @QueryParam("code") String code,
            @QueryParam("state") String state,
            @CookieParam(OIDC_COOKIE) Cookie cookie)
    {
        if (oauthManager == null) {
            throw new WebApplicationException("OAuth configuration is not setup");
        }
        if (cookie == null) {
            log.error("OIDC cookie doesn't exist");
            return buildUnauthorizedResponse();
        }
        Optional<String> cookieState = OidcCookie.getState(cookie);
        Optional<String> nonce = OidcCookie.getNonce(cookie);
        if (cookieState.isEmpty() || !cookieState.orElseThrow().equals(state) || nonce.isEmpty()) {
            log.error("Invalid OIDC cookie");
            return buildUnauthorizedResponse();
        }
        return oauthManager.exchangeCodeForToken(code, nonce.orElseThrow(), "/");
    }

    @POST
    @Path("login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response processRESTLogin(RestLoginRequest loginForm)
    {
        if (formAuthManager == null) {
            if (oauthManager == null) {
                return Response.ok(Result.ok(Map.of("token", loginForm.username()))).build();
            }
            throw new WebApplicationException("Form authentication is not setup");
        }
        Result<?> r = formAuthManager.processRESTLogin(loginForm);
        return Response.ok(r).build();
    }

    @POST
    @Path("logout")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response processRESTLogin()
    {
        return Response.ok(Result.ok()).build();
    }

    @POST
    @RolesAllowed("USER")
    @Path("userinfo")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response restUserinfo(@Context SecurityContext securityContext)
    {
        LbPrincipal principal = (LbPrincipal) securityContext.getUserPrincipal();
        ImmutableList.Builder<String> rolesBuilder = ImmutableList.builder();
        if (securityContext.isUserInRole("ADMIN")) {
            rolesBuilder.add("ADMIN");
        }
        if (securityContext.isUserInRole("USER")) {
            rolesBuilder.add("USER");
        }
        if (securityContext.isUserInRole("API")) {
            rolesBuilder.add("API");
        }
        List<String> roles = rolesBuilder.build();
        List<String> pagePermissions;
        if (formAuthManager != null) {
            pagePermissions = formAuthManager.processPagePermissions(roles);
        }
        else if (oauthManager != null) {
            pagePermissions = oauthManager.processPagePermissions(roles);
        }
        else {
            pagePermissions = Collections.emptyList();
        }
        Map<String, Object> resMap = Map.of(
                "roles", roles,
                "permissions", pagePermissions,
                "userId", principal.getName(),
                "userName", principal.getName());
        return Response.ok(Result.ok(resMap)).build();
    }

    @POST
    @Path("loginType")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response loginType()
    {
        List<String> loginTypes;
        if (haGatewayConfiguration.getAuthentication() != null) {
            loginTypes = haGatewayConfiguration.getAuthentication().getDefaultTypes();
        }
        else {
            loginTypes = List.of("none");
        }
        return Response.ok(Result.ok("Ok", loginTypes)).build();
    }
}
