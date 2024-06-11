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
package io.trino.gateway.baseapp;

import jakarta.servlet.ServletContext;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;

@Path("/trino-gateway")
public class WebUIStaticResource
{
    @GET
    @Path("logo.svg")
    public Response getLogo(@Context ServletContext servletContext)
            throws IOException
    {
        String fullPath = "/static/logo.svg";
        URL resource = getClass().getResource(fullPath);
        if (resource == null) {
            return Response.status(NOT_FOUND).build();
        }
        return Response.ok(resource.openStream(), servletContext.getMimeType(resource.toString())).build();
    }

    @GET
    @Path("assets/{path: .*}")
    public Response getAssetsFile(@PathParam("path") String path, @Context ServletContext servletContext)
            throws IOException
    {
        String fullPath = "/static/assets/" + path;
        if (!isCanonical(fullPath)) {
            // consider redirecting to the absolute path
            return Response.status(NOT_FOUND).build();
        }

        URL resource = getClass().getResource(fullPath);
        if (resource == null) {
            return Response.status(NOT_FOUND).build();
        }

        return Response.ok(resource.openStream(), servletContext.getMimeType(resource.toString())).build();
    }

    private static boolean isCanonical(String fullPath)
    {
        try {
            return new URI(fullPath).normalize().getPath().equals(fullPath);
        }
        catch (URISyntaxException e) {
            return false;
        }
    }
}
