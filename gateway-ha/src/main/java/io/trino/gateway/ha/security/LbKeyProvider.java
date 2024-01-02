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

import io.trino.gateway.ha.config.SelfSignKeyPairConfiguration;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

import java.io.FileReader;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import static java.nio.charset.StandardCharsets.UTF_8;

public class LbKeyProvider
{
    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public LbKeyProvider(SelfSignKeyPairConfiguration keypairConfig)
    {
        KeyFactory factory = null;
        try {
            java.security.Security.addProvider(
                    new org.bouncycastle.jce.provider.BouncyCastleProvider());
            factory = KeyFactory.getInstance("RSA");
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        try {
            String publicKeyRsa = keypairConfig.publicKeyRsa();
            try (FileReader keyReader = new FileReader(publicKeyRsa, UTF_8);
                    PemReader pemReader = new PemReader(keyReader)) {
                PemObject pemObject = pemReader.readPemObject();
                byte[] content = pemObject.getContent();
                X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(content);
                this.publicKey = factory.generatePublic(pubKeySpec);
            }

            String privateKeyRsa = keypairConfig.privateKeyRsa();
            try (FileReader keyReader = new FileReader(privateKeyRsa, UTF_8);
                    PemReader pemReader = new PemReader(keyReader)) {
                PemObject pemObject = pemReader.readPemObject();
                byte[] content = pemObject.getContent();
                PKCS8EncodedKeySpec privKeySpec = new PKCS8EncodedKeySpec(content);
                this.privateKey = factory.generatePrivate(privKeySpec);
            }
        }
        catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    RSAPrivateKey getRsaPrivateKey()
    {
        return (this.privateKey instanceof RSAPrivateKey)
                ? (RSAPrivateKey) this.privateKey : null;
    }

    RSAPublicKey getRsaPublicKey()
    {
        return (this.publicKey instanceof RSAPublicKey)
                ? (RSAPublicKey) this.publicKey : null;
    }
}
