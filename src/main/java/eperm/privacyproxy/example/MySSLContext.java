package eperm.privacyproxy.example;

import java.io.*;
import java.net.*;
import java.security.KeyStore;
import java.util.concurrent.*;
import javax.net.ssl.*;

public class MySSLContext {
  public static SSLContext get() throws Exception {
/*
  ### create keypair and cert for server:
   $ openssl genrsa -out key.pem 2048
   $ openssl req -new -sha256 -key key.pem -out csr.csr
  ### enter "127.0.0.1" for "Common Name"
   $ openssl req -x509 -sha256 -days 365 -key key.pem -in csr.csr -out certificate.pem
   $ openssl pkcs12 -export -out server-identity.p12 -inkey key.pem -in certificate.pem
  ### this is your `keyPass`

  ### copy your trust store and add add the above cert to it:
   $ cp $JAVA_HOME/lib/security/cacerts mycerts
   $ keytool -importcert -keystore mycerts -file certificate.pem
  ### ^ char[] trustPass (default is 'changeit')

   String keyStorePath = "./server-identity.p12";
   String trustStorePath = "./mycerts";
   ...
 */
    String keyStorePath = "FILL_ME_IN";
    String trustStorePath = "FILL_ME_IN";
    char[] keyPass = "FILL_ME_IN".toCharArray();
    char[] trustPass = "FILL_ME_IN".toCharArray();

    // handle keystore
    FileInputStream keyStoreIn = new FileInputStream(keyStorePath);
    KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    keyStore.load(keyStoreIn, keyPass);
    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    kmf.init(keyStore, keyPass);

    // handle truststore
    FileInputStream trustStoreIn = new FileInputStream(trustStorePath);
    KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
    trustStore.load(trustStoreIn, trustPass);
    TrustManagerFactory tmf =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmf.init(trustStore);

    SSLContext ctx = SSLContext.getInstance("TLSv1.2");
    ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
    return ctx;
  }
}
