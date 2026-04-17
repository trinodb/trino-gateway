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
package io.trino.gateway.ha.testing;

import javax.security.auth.x500.X500Principal;

import java.math.BigInteger;
import java.security.Principal;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Set;

public class TestingX509Certificate
        extends X509Certificate
{
    private final X500Principal principal;

    public TestingX509Certificate(String distinguishedName)
    {
        this.principal = new X500Principal(distinguishedName);
    }

    @Override
    public X500Principal getSubjectX500Principal()
    {
        return principal;
    }

    @Override
    public Principal getSubjectDN()
    {
        return principal;
    }

    @Override
    public void checkValidity()
            throws CertificateExpiredException, CertificateNotYetValidException
    {
    }

    @Override
    public void checkValidity(Date date)
            throws CertificateExpiredException, CertificateNotYetValidException
    {
    }

    @Override
    public int getVersion()
    {
        return 3;
    }

    @Override
    public BigInteger getSerialNumber()
    {
        return BigInteger.ONE;
    }

    @Override
    public Principal getIssuerDN()
    {
        return principal;
    }

    @Override
    public Date getNotBefore()
    {
        return new Date(0);
    }

    @Override
    public Date getNotAfter()
    {
        return new Date(Long.MAX_VALUE);
    }

    @Override
    public byte[] getTBSCertificate()
            throws CertificateEncodingException
    {
        return new byte[0];
    }

    @Override
    public byte[] getSignature()
    {
        return new byte[0];
    }

    @Override
    public String getSigAlgName()
    {
        return "NONE";
    }

    @Override
    public String getSigAlgOID()
    {
        return "0.0";
    }

    @Override
    public byte[] getSigAlgParams()
    {
        return new byte[0];
    }

    @Override
    public boolean[] getIssuerUniqueID()
    {
        return null;
    }

    @Override
    public boolean[] getSubjectUniqueID()
    {
        return null;
    }

    @Override
    public boolean[] getKeyUsage()
    {
        return null;
    }

    @Override
    public int getBasicConstraints()
    {
        return -1;
    }

    @Override
    public byte[] getEncoded()
            throws CertificateEncodingException
    {
        return new byte[0];
    }

    @Override
    public void verify(PublicKey key)
            throws CertificateException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void verify(PublicKey key, String sigProvider)
            throws CertificateException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString()
    {
        return principal.getName();
    }

    @Override
    public PublicKey getPublicKey()
    {
        return null;
    }

    @Override
    public boolean hasUnsupportedCriticalExtension()
    {
        return false;
    }

    @Override
    public Set<String> getCriticalExtensionOIDs()
    {
        return null;
    }

    @Override
    public Set<String> getNonCriticalExtensionOIDs()
    {
        return null;
    }

    @Override
    public byte[] getExtensionValue(String oid)
    {
        return null;
    }
}
