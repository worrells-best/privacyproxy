package eperm.privacyproxy.server;

import java.io.*;
import java.net.*;
import javax.net.ssl.*;

class ProxyDestination {
  static final String SP = ":";
  static final int DOMAIN_POS = 0;
  static final int PORT_POS = 1;
  static final int NUM_URI_PARTS = 2;
  private String domain;
  private int port;

  public String getDomain() {
    return this.domain;
  }

  public int getPort() {
    return this.port;
  }

  public static ProxyDestination parseUri(String uri) throws IllegalUriException {
    String[] parts = uri.split(SP);
    if (parts.length != NUM_URI_PARTS) {
      throw new IllegalUriException("expected URI format 'host:port' not received");
    }

    ProxyDestination dst = new ProxyDestination();
    dst.domain = parts[DOMAIN_POS];
    dst.port = Integer.parseInt(parts[PORT_POS]);
    return dst;
  }
}
