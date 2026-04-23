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

import com.auth0.jwt.algorithms.Algorithm;
import io.trino.gateway.ha.config.SelfSignKeyPairConfiguration;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import static java.nio.charset.StandardCharsets.UTF_8;

public class LbKeyProvider
{
    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final Algorithm signingAlgorithm;
    private final String jwtAlgorithmName;

    public LbKeyProvider(SelfSignKeyPairConfiguration keypairConfig)
    {
        try {
            this.publicKey = readPublicKey(keypairConfig.getPublicKey());
            this.privateKey = readPrivateKey(keypairConfig.getPrivateKey());
            this.signingAlgorithm = createSigningAlgorithm(publicKey, privateKey);
            this.jwtAlgorithmName = getJwtAlgorithmName(publicKey, privateKey);
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public PrivateKey privateKey()
    {
        return privateKey;
    }

    public PublicKey publicKey()
    {
        return publicKey;
    }

    public Algorithm signingAlgorithm()
    {
        return signingAlgorithm;
    }

    public String jwtAlgorithmName()
    {
        return jwtAlgorithmName;
    }

    private static String getJwtAlgorithmName(PublicKey publicKey, PrivateKey privateKey)
    {
        if (publicKey instanceof RSAPublicKey && privateKey instanceof RSAPrivateKey) {
            return "RS256";
        }
        if (publicKey instanceof ECPublicKey ecPublicKey && privateKey instanceof ECPrivateKey) {
            return switch (ecFieldSize(ecPublicKey)) {
                case 256 -> "ES256";
                case 384 -> "ES384";
                case 521 -> "ES512";
                default -> throw unsupportedKeyType("Unsupported EC curve size for JWT signing");
            };
        }
        throw unsupportedKeyType("Unsupported key type for JWT signing");
    }

    static Algorithm verificationAlgorithm(PublicKey publicKey)
    {
        if (publicKey instanceof RSAPublicKey rsaPublicKey) {
            return Algorithm.RSA256(rsaPublicKey, null);
        }
        if (publicKey instanceof ECPublicKey ecPublicKey) {
            return switch (ecFieldSize(ecPublicKey)) {
                case 256 -> Algorithm.ECDSA256(ecPublicKey, null);
                case 384 -> Algorithm.ECDSA384(ecPublicKey, null);
                case 521 -> Algorithm.ECDSA512(ecPublicKey, null);
                default -> throw unsupportedKeyType("Unsupported EC curve size for JWT verification");
            };
        }
        throw unsupportedKeyType("Unsupported key type for JWT verification");
    }

    private static Algorithm createSigningAlgorithm(PublicKey publicKey, PrivateKey privateKey)
    {
        if (publicKey instanceof RSAPublicKey rsaPublicKey && privateKey instanceof RSAPrivateKey rsaPrivateKey) {
            return Algorithm.RSA256(rsaPublicKey, rsaPrivateKey);
        }
        if (publicKey instanceof ECPublicKey ecPublicKey && privateKey instanceof ECPrivateKey ecPrivateKey) {
            return switch (ecFieldSize(ecPublicKey)) {
                case 256 -> Algorithm.ECDSA256(ecPublicKey, ecPrivateKey);
                case 384 -> Algorithm.ECDSA384(ecPublicKey, ecPrivateKey);
                case 521 -> Algorithm.ECDSA512(ecPublicKey, ecPrivateKey);
                default -> throw unsupportedKeyType("Unsupported EC curve size for JWT signing");
            };
        }
        throw unsupportedKeyType("Unsupported key type for JWT signing");
    }

    private static int ecFieldSize(ECPublicKey ecPublicKey)
    {
        return ecPublicKey.getParams().getCurve().getField().getFieldSize();
    }

    private static PublicKey readPublicKey(String path)
            throws IOException
    {
        Object pemObject = readPemObject(path);
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME);
        if (pemObject instanceof SubjectPublicKeyInfo publicKeyInfo) {
            return converter.getPublicKey(publicKeyInfo);
        }
        if (pemObject instanceof PEMKeyPair keyPair) {
            return converter.getKeyPair(keyPair).getPublic();
        }
        throw new IllegalArgumentException("Unsupported public key PEM: " + path);
    }

    private static PrivateKey readPrivateKey(String path)
            throws IOException
    {
        Object pemObject = readPemObject(path);
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME);
        if (pemObject instanceof PrivateKeyInfo privateKeyInfo) {
            return converter.getPrivateKey(privateKeyInfo);
        }
        if (pemObject instanceof PEMKeyPair keyPair) {
            return converter.getKeyPair(keyPair).getPrivate();
        }
        throw new IllegalArgumentException("Unsupported private key PEM: " + path);
    }

    private static Object readPemObject(String path)
            throws IOException
    {
        try (BufferedReader keyReader = Files.newBufferedReader(Path.of(path), UTF_8);
                PEMParser pemParser = new PEMParser(keyReader)) {
            Object pemObject = pemParser.readObject();
            if (pemObject == null) {
                throw new IllegalArgumentException("Invalid PEM file: " + path);
            }
            return pemObject;
        }
    }

    private static IllegalArgumentException unsupportedKeyType(String message)
    {
        return new IllegalArgumentException(message + ". Supported types: RSA and EC.");
    }
}
