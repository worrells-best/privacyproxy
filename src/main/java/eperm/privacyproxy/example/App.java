package eperm.privacyproxy.example;

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
import org.apache.http.util.EntityUtils;

public class App {
  // proxy config
  private static final String proxyHost = "127.0.0.1";
  private static final int proxyPort = 3000;

  public static void main(String args[]) {
    String endpointHost = "api.giphy.com";
    int endpointPort = 443;
    String endpointProtocol = "https";
    String endpointQuery = "/v1/gifs/search?q=whoa&api_key=dc6zaTOxFJmzC";

    try {
      SSLContext ctx = MySSLContext.get();
      PrivacyProxyServer server = new PrivacyProxyServer(proxyPort, ctx);
      server.addAllowedEndpoint(endpointHost, endpointPort);
      server.startInBackground();

      makeClientRequest(endpointHost, endpointPort, endpointProtocol, endpointQuery);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void makeClientRequest(
      String endpointHost, int endpointPort, String endpointProtocol, String endpointQuery) {
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
        HttpHost target = new HttpHost(endpointHost, endpointPort, endpointProtocol);
        HttpHost proxy = new HttpHost(proxyHost, proxyPort, "https");

        RequestConfig config = RequestConfig.custom().setProxy(proxy).build();
        HttpGet request = new HttpGet(endpointQuery);
        request.setConfig(config);

        System.out.println(
            "Executing request " + request.getRequestLine() + " to " + target + " via " + proxy);

        CloseableHttpResponse response = httpclient.execute(target, request);
        try {
          System.out.println("----------------------------------------");
          System.out.println(response.getStatusLine());
          System.out.println(EntityUtils.toString(response.getEntity()));
        } finally {
          response.close();
        }
      } finally {
        httpclient.close();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
