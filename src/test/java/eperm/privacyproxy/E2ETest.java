package eperm.privacyproxy;

import static org.junit.Assert.assertEquals;

import eperm.privacyproxy.example.MySSLContext;
import eperm.privacyproxy.server.PrivacyProxyServer;
import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.util.concurrent.*;
import javax.net.ssl.*;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.junit.Test;

// TODO: spin up local server rather than testing across the internet
// TODO: concurrency test

public class E2ETest {
  private static final String proxyHost = "127.0.0.1";
  private static final int proxyPort = 3000;
  private static final String STATUS_LINE_OK = "HTTP/1.1 200 OK";
  private static final String STATUS_LINE_FORBIDDEN = "HTTP/1.1 403 Forbidden";

  @Test
  public void testEndToEndFlow() {
    String dstDomain = "example.com";
    String dstPath = "/";
    int dstPort = 443;
    try {
      PrivacyProxyServer server = defaultServer();
      server.addAllowedEndpoint(dstDomain, dstPort);
      server.startInBackground();

      // endpoint allowed
      CloseableHttpResponse resp = getRequest(dstDomain, dstPort, dstPath);
      assertEquals(STATUS_LINE_OK, resp.getStatusLine().toString());

      // endpoint not allowed
      resp = getRequest("google.com", dstPort, dstPath);
      assertEquals(STATUS_LINE_FORBIDDEN, resp.getStatusLine().toString());
      server.stop();
    } catch (Exception e) {
      assertEquals("no exception expected", e);
    }
  }

  private PrivacyProxyServer defaultServer() throws Exception {
    SSLContext ctx = MySSLContext.get();
    PrivacyProxyServer server = new PrivacyProxyServer(proxyPort, ctx);
    return server;
  }

  CloseableHttpResponse getRequest(String host, int port, String path) {
    try {
      // get out context
      SSLContext ctx = MySSLContext.get();

      // Creating SSLConnectionSocketFactory object
      SSLConnectionSocketFactory sslConSocFactory = new SSLConnectionSocketFactory(ctx);

      // Creating HttpClientBuilder
      HttpClientBuilder clientbuilder = HttpClients.custom();

      // Setting the SSLConnectionSocketFactory
      clientbuilder = clientbuilder.setSSLSocketFactory(sslConSocFactory);

      // Building the CloseableHttpClient
      CloseableHttpClient httpclient = clientbuilder.build();
      try {
        HttpHost target = new HttpHost(host, port, "https");
        HttpHost proxy = new HttpHost(proxyHost, proxyPort, "https");

        RequestConfig config = RequestConfig.custom().setProxy(proxy).build();
        HttpGet request = new HttpGet(path);
        request.setConfig(config);

        CloseableHttpResponse response = httpclient.execute(target, request);
        return response;
      } finally {
        httpclient.close();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }
}
