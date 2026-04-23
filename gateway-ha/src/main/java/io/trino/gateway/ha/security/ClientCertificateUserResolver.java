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

import io.trino.gateway.ha.config.RequestAnalyzerConfig;
import jakarta.servlet.http.HttpServletRequest;

import java.security.cert.X509Certificate;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public final class ClientCertificateUserResolver
{
    public static final String MAPPED_USER_ATTRIBUTE = ClientCertificateUserResolver.class.getName() + ".mappedUser";

    private final String identityField;
    private final UserMapping userMapping;

    public ClientCertificateUserResolver(RequestAnalyzerConfig config)
    {
        requireNonNull(config, "config is null");
        this.identityField = requireNonNull(config.getClientCertificateIdentityField(), "clientCertificateIdentityField is null");
        this.userMapping = UserMapping.createUserMapping(
                optionalNonBlank(config.getClientCertificateUserMappingPattern()),
                optionalNonBlank(config.getClientCertificateUserMappingFile()));
    }

    public Optional<String> resolveMappedUser(HttpServletRequest request)
            throws UserMappingException
    {
        if (request == null) {
            return Optional.empty();
        }

        Optional<String> cachedMappedUser = findCachedMappedUser(request);
        if (cachedMappedUser.isPresent()) {
            return cachedMappedUser;
        }

        Optional<X509Certificate> certificate = ClientCertificateIdentityExtractor.findClientCertificate(request);
        if (certificate.isEmpty()) {
            return Optional.empty();
        }

        String rawIdentity = ClientCertificateIdentityExtractor.extractIdentity(certificate.orElseThrow(), identityField)
                .orElseThrow(() -> new UserMappingException("Client certificate does not contain required identity field '%s'".formatted(identityField)));
        String mappedUser = userMapping.mapUser(rawIdentity);
        request.setAttribute(MAPPED_USER_ATTRIBUTE, mappedUser);
        return Optional.of(mappedUser);
    }

    public Optional<String> findCachedMappedUser(HttpServletRequest request)
    {
        Object mappedUser = request.getAttribute(MAPPED_USER_ATTRIBUTE);
        if (mappedUser instanceof String value && !value.isBlank()) {
            return Optional.of(value);
        }
        return Optional.empty();
    }

    private static Optional<String> optionalNonBlank(String value)
    {
        return Optional.ofNullable(value).filter(v -> !v.isBlank());
    }
}
