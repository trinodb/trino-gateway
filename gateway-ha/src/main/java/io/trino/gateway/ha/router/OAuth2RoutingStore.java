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

import java.util.Optional;

/**
 * Shared, cross-pod store of Trino OAuth2 token-exchange pins ({@code authId}/{@code authIdHash} →
 * minting coordinator). A pin is recorded by whichever gateway pod proxies the {@code 401}
 * challenge, but the matching poll/initiate request may land on a different pod, so the pin must be
 * visible to every pod. See {@link OAuth2RoutingUtils}.
 */
public interface OAuth2RoutingStore
{
    /**
     * Pins an OAuth2 identifier to the coordinator that minted it.
     */
    void setBackend(String oauthId, String backend);

    /**
     * The pinned backend for an OAuth2 identifier, or empty if none is recorded.
     */
    Optional<String> findBackend(String oauthId);

    /**
     * Drops a pin (e.g. once its backend is gone and the client must re-authenticate).
     */
    void removeBackend(String oauthId);
}
