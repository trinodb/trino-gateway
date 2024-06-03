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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.hash.Hashing;
import io.airlift.json.JsonCodec;
import io.airlift.log.Logger;
import io.airlift.units.Duration;
import io.trino.gateway.ha.config.GatewayCookieConfigurationPropertiesProvider;
import jakarta.servlet.http.Cookie;
import jakarta.ws.rs.core.NewCookie;

import java.util.Base64;
import java.util.List;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

@JsonPropertyOrder(alphabetic = true)
public class GatewayCookie
        implements Comparable<GatewayCookie>
{
    private static final Logger log = Logger.get(GatewayCookie.class);
    private String signature;
    private final UnsignedGatewayCookie unsignedGatewayCookie;
    private final GatewayCookieConfigurationPropertiesProvider gatewayCookieConfigurationPropertiesProvider = GatewayCookieConfigurationPropertiesProvider.getInstance();

    public static final String PREFIX = "TG.";

    public static final JsonCodec<GatewayCookie> CODEC = JsonCodec.jsonCodec(GatewayCookie.class);

    @JsonCreator
    public GatewayCookie(
            @JsonProperty("ts") Long ts,
            @JsonProperty("name") String name,
            @JsonProperty("payload") String payload,
            @JsonProperty("routingPaths") List<String> routingPaths,
            @JsonProperty("deletePaths") List<String> deletePaths,
            @JsonProperty("backend") String backend,
            @JsonProperty("priority") Integer priority,
            @JsonProperty("ttl") Duration ttl,
            @JsonProperty("signature") String signature)
    {
        this.unsignedGatewayCookie = new UnsignedGatewayCookie(
                requireNonNull(ts),
                requireNonNull(name),
                payload,
                backend,
                requireNonNull(routingPaths),
                requireNonNull(deletePaths),
                requireNonNull(ttl),
                priority);
        this.signature = signature;
    }

    public GatewayCookie(String name, String payload, String backend, List<String> routingPaths, List<String> deletePaths, Duration ttl, int priority)
    {
        this.unsignedGatewayCookie = new UnsignedGatewayCookie(
                System.currentTimeMillis(),
                requireNonNull(name.startsWith(PREFIX) ? name : PREFIX + name),
                payload,
                backend,
                requireNonNull(routingPaths),
                requireNonNull(deletePaths),
                requireNonNull(ttl),
                priority);
        signature = computeSignature();
    }

    @JsonProperty
    public Long getTs()
    {
        return unsignedGatewayCookie.getTs();
    }

    @JsonProperty
    public String getName()
    {
        return unsignedGatewayCookie.getName();
    }

    @JsonProperty
    public String getPayload()
    {
        return unsignedGatewayCookie.getPayload();
    }

    @JsonProperty
    public String getBackend()
    {
        return unsignedGatewayCookie.getBackend();
    }

    @JsonProperty
    public int getPriority()
    {
        return unsignedGatewayCookie.getPriority();
    }

    @JsonProperty
    public List<String> getRoutingPaths()
    {
        return unsignedGatewayCookie.getRoutingPaths();
    }

    @JsonProperty
    public List<String> getDeletePaths()
    {
        return unsignedGatewayCookie.getDeletePaths();
    }

    @JsonProperty
    public Duration getTtl()
    {
        return unsignedGatewayCookie.getTtl();
    }

    @JsonProperty
    public String getSignature()
    {
        return signature;
    }

    public void setTs(Long ts)
    {
        unsignedGatewayCookie.setTs(ts);
    }

    private String computeSignature()
    {
        return Hashing.hmacSha256(gatewayCookieConfigurationPropertiesProvider.getCookieSigningKey())
                .hashString(UnsignedGatewayCookie.CODEC.toJson(unsignedGatewayCookie), UTF_8)
                .toString();
    }

    @Override
    public int compareTo(GatewayCookie o)
    {
        int priorityDelta = unsignedGatewayCookie.getPriority() - o.getPriority();
        return priorityDelta != 0 ? priorityDelta : (int) (unsignedGatewayCookie.getTs() - o.getTs());
    }

    public Cookie toCookie()
    {
        Cookie cookie = new Cookie(unsignedGatewayCookie.getName(), Base64.getUrlEncoder().encodeToString(CODEC.toJson(this).getBytes(UTF_8)));
        cookie.setMaxAge((int) unsignedGatewayCookie.getTtl().toMillis() / 1000);
        return cookie;
    }

    public NewCookie toNewCookie()
    {
        return new NewCookie.Builder(unsignedGatewayCookie.getName())
                .value(Base64.getUrlEncoder().encodeToString(CODEC.toJson(this).getBytes(UTF_8)))
                .maxAge((int) unsignedGatewayCookie.getTtl().toMillis() / 1000)
                .build();
    }

    public static GatewayCookie fromCookie(Cookie cookie)
    {
        return GatewayCookie.CODEC.fromJson(Base64.getUrlDecoder().decode(cookie.getValue()));
    }

    public boolean matchesRoutingPath(String path)
    {
        if (matchesDeletePath(path)) {
            return false;
        }

        return unsignedGatewayCookie.getRoutingPaths().stream().anyMatch(path::startsWith);
    }

    public boolean matchesDeletePath(String path)
    {
        return unsignedGatewayCookie.getDeletePaths().contains(path);
    }

    public boolean isValid()
    {
        if (System.currentTimeMillis() > unsignedGatewayCookie.getTs() + unsignedGatewayCookie.getTtl().toMillis()) {
            return false;
        }

        if (isNullOrEmpty(signature) || !signature.equals(computeSignature())) {
            log.error("Invalid cookie: %s", CODEC.toJson(this));
            throw new IllegalArgumentException("Invalid cookie signature");
        }

        return true;
    }

    @JsonPropertyOrder(alphabetic = true)
    public static class UnsignedGatewayCookie
    {
        public static final JsonCodec<UnsignedGatewayCookie> CODEC = JsonCodec.jsonCodec(UnsignedGatewayCookie.class);
        private Long ts; // timestamp. The shortened name saves 8 bytes of cookie size
        private final String name;
        private final String payload;
        private final List<String> routingPaths;

        private final List<String> deletePaths;

        private final int priority;
        private final Duration ttl;
        private final String backend;

        public UnsignedGatewayCookie(GatewayCookie gatewayCookie)
        {
            this.ts = gatewayCookie.getTs();
            this.name = gatewayCookie.getName();
            this.payload = gatewayCookie.getPayload();
            this.routingPaths = gatewayCookie.getRoutingPaths();
            this.deletePaths = gatewayCookie.getDeletePaths();
            this.priority = gatewayCookie.getPriority();
            this.ttl = gatewayCookie.getTtl();
            this.backend = gatewayCookie.getBackend();
        }

        public UnsignedGatewayCookie(Long ts, String name, String payload, String backend, List<String> routingPaths, List<String> deletePaths, Duration ttl, int priority)
        {
            this.name = name.startsWith(PREFIX) ? name : PREFIX + name;
            this.payload = payload;
            this.backend = backend;
            this.routingPaths = routingPaths;
            this.deletePaths = deletePaths;
            this.ttl = ttl;
            this.ts = ts;
            this.priority = priority;
        }

        @JsonProperty
        public Long getTs()
        {
            return ts;
        }

        public void setTs(Long ts)
        {
            this.ts = ts;
        }

        @JsonProperty
        public String getName()
        {
            return name;
        }

        @JsonProperty
        public String getPayload()
        {
            return payload;
        }

        @JsonProperty
        public String getBackend()
        {
            return backend;
        }

        @JsonProperty
        public int getPriority()
        {
            return priority;
        }

        @JsonProperty
        public List<String> getRoutingPaths()
        {
            return routingPaths;
        }

        @JsonProperty
        public List<String> getDeletePaths()
        {
            return deletePaths;
        }

        @JsonProperty
        public Duration getTtl()
        {
            return ttl;
        }
    }
}
