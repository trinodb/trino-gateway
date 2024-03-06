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

import com.google.inject.Inject;
import io.trino.gateway.ha.domain.Result;
import io.trino.gateway.ha.domain.request.RestLoginRequest;
import io.trino.gateway.ha.security.LbFormAuthManager;
import io.trino.gateway.ha.security.LbOAuthManager;
import io.trino.gateway.ha.security.LbPrincipal;
import jakarta.annotation.Nullable;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class LoginResource
{
    private final LbOAuthManager oauthManager;
    private final LbFormAuthManager formAuthManager;

    @Inject
    public LoginResource(@Nullable LbOAuthManager oauthManager, @Nullable LbFormAuthManager formAuthManager)
    {
        this.oauthManager = oauthManager;
        this.formAuthManager = formAuthManager;
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
        String authorizationUrl = oauthManager.getAuthorizationCode();
        return Response.ok(Result.ok("Ok", authorizationUrl)).build();
    }

    @Path("oidc/callback")
    @GET
    public Response callback(@QueryParam("code") String code)
    {
        if (oauthManager == null) {
            throw new WebApplicationException("OAuth configuration is not setup");
        }
        return oauthManager.exchangeCodeForToken(code, "/");
    }

    @POST
    @Path("login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response processRESTLogin(RestLoginRequest loginForm)
    {
        if (formAuthManager == null) {
            if (oauthManager == null) {
                return Response.ok(Result.ok(Map.of("token", loginForm.getUsername()))).build();
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
        List<String> roles = List.of(principal.getMemberOf().orElse("").split("_"));
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
        String loginType;
        if (formAuthManager != null) {
            loginType = "form";
        }
        else if (oauthManager != null) {
            loginType = "oauth";
        }
        else {
            loginType = "none";
        }
        return Response.ok(Result.ok("Ok", loginType)).build();
    }
}
