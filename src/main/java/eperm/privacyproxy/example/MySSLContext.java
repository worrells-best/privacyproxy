package eperm.privacyproxy.example;

import java.io.*;
import java.net.*;
import java.security.KeyStore;
import java.util.concurrent.*;
import javax.net.ssl.*;

public class MySSLContext {
  public static SSLContext get() throws Exception {
    String keyStorePath = "FILL_ME_IN";
    String trustStorePath = "FILL_ME_IN";
    char[] keyPass = "FILL_ME_IN".toCharArray();
    char[] trustPass = "FILL_ME_IN".toCharArray();
    String alias = "FILL_ME_IN";

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
