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
import javax.naming.ldap.Rdn;

import java.security.cert.X509Certificate;
import java.util.List;
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
        if (identityField.equalsIgnoreCase(SUBJECT_DN_FIELD)) {
            // toString(), not getName(): Trino's CertificateAuthenticator maps this exact rendering
            return Optional.of(certificate.getSubjectX500Principal().toString());
        }

        // LdapName needs the RFC 2253 rendering to parse the DN into RDNs
        String subjectDn = certificate.getSubjectX500Principal().getName();
        try {
            LdapName ldapName = new LdapName(subjectDn);
            // LdapName.getRdns() returns RDNs root-to-leaf, the reverse of the DN string's
            // leaf-to-root order (RFC 4514/2253). Walk it backwards so that on a DN with
            // repeated RDN types (e.g. two CNs), we return the leaf/most-specific one - the
            // RDN of the certificate's own entry - rather than a less-specific ancestor's.
            List<Rdn> rdns = ldapName.getRdns();
            for (int i = rdns.size() - 1; i >= 0; i--) {
                Rdn rdn = rdns.get(i);
                // getValue() is byte[] for a hex-encoded value; stringifying it would yield a per-request "[B@hash"
                if (rdn.getType().equalsIgnoreCase(identityField) && rdn.getValue() instanceof String value) {
                    if (!value.isBlank()) {
                        return Optional.of(value);
                    }
                }
            }
            return Optional.empty();
        }
        catch (InvalidNameException e) {
            throw new IllegalArgumentException("Failed to parse certificate subject DN: " + subjectDn, e);
        }
    }
}
