package com.lyft.data.gateway.ha.security;

import com.lyft.data.gateway.ha.config.SelfSignKeyPairConfiguration;
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
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

public class LbKeyProvider {
  private PrivateKey privateKey;
  private PublicKey publicKey;

  public LbKeyProvider(SelfSignKeyPairConfiguration keypairConfig) {
    KeyFactory factory = null;
    try {
      java.security.Security.addProvider(
          new org.bouncycastle.jce.provider.BouncyCastleProvider()
      );
      factory = KeyFactory.getInstance("RSA");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }

    try {
      String publicKeyRsa = keypairConfig.getPublicKeyRsa();
      try (FileReader keyReader = new FileReader(publicKeyRsa);
           PemReader pemReader = new PemReader(keyReader)) {

        PemObject pemObject = pemReader.readPemObject();
        byte[] content = pemObject.getContent();
        X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(content);
        this.publicKey = factory.generatePublic(pubKeySpec);
      }

      String privateKeyRsa = keypairConfig.getPrivateKeyRsa();
      try (FileReader keyReader = new FileReader(privateKeyRsa);
           PemReader pemReader = new PemReader(keyReader)) {

        PemObject pemObject = pemReader.readPemObject();
        byte[] content = pemObject.getContent();
        PKCS8EncodedKeySpec privKeySpec = new PKCS8EncodedKeySpec(content);
        this.privateKey = factory.generatePrivate(privKeySpec);
      }
    } catch (InvalidKeySpecException e) {
      throw new RuntimeException(e);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }

  }

  RSAPrivateKey getRsaPrivateKey() {
    return (this.privateKey instanceof RSAPrivateKey)
        ? (RSAPrivateKey) this.privateKey : null;
  }

  RSAPublicKey getRsaPublicKey() {
    return (this.publicKey instanceof RSAPublicKey)
        ? (RSAPublicKey) this.publicKey : null;
  }

}
