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

import jakarta.servlet.http.HttpServletRequest;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;

import java.security.cert.X509Certificate;
import java.util.Optional;

public final class ClientCertificateIdentityExtractor
{
    public static final String JAKARTA_X509_CERTIFICATE_ATTRIBUTE = "jakarta.servlet.request.X509Certificate";
    public static final String SUBJECT_DN_FIELD = "SUBJECT_DN";

    private ClientCertificateIdentityExtractor() {}

    public static Optional<X509Certificate> findClientCertificate(HttpServletRequest request)
    {
        return findClientCertificate(request.getAttribute(JAKARTA_X509_CERTIFICATE_ATTRIBUTE));
    }

    public static Optional<X509Certificate> findClientCertificate(Object certificateAttribute)
    {
        if (certificateAttribute instanceof X509Certificate[] certificates && certificates.length > 0) {
            return Optional.of(certificates[0]);
        }
        return Optional.empty();
    }

    public static Optional<String> extractIdentity(X509Certificate certificate, String identityField)
    {
        String subjectDn = certificate.getSubjectX500Principal().getName();
        if (identityField.equals(SUBJECT_DN_FIELD)) {
            return Optional.of(subjectDn);
        }

        try {
            LdapName ldapName = new LdapName(subjectDn);
            return ldapName.getRdns().stream()
                    .filter(rdn -> rdn.getType().equals(identityField))
                    .map(rdn -> String.valueOf(rdn.getValue()))
                    .filter(value -> !value.isBlank())
                    .findFirst();
        }
        catch (InvalidNameException e) {
            throw new IllegalArgumentException("Failed to parse certificate subject DN: " + subjectDn, e);
        }
    }
}
