package eperm.privacyproxy.server;

import java.io.*;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import javax.net.ssl.*;

public class HttpUtils {
  // For parsing http request
  static final String HTTP_SP = " ";
  static final char HTTP_METHOD_POS = 0;
  static final char HTTP_URI_POS = 1;
  static final char HTTP_VERSION_POS = 2;
  static final int HTTP_NUM_HEADER_PARTS = 3;
  // for parsing URIs
  static final String URI_SP = ":";
  static final int URI_DOMAIN_POS = 0;
  static final int URI_PORT_POS = 1;
  static final int URI_NUM_PARTS = 2;

  public static final ByteBuffer RES_CONNECTION_ESTABLISHED =
      ByteBuffer.wrap("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes());
  public static final ByteBuffer RES_FORBIDDEN =
      ByteBuffer.wrap("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\n\r\n".getBytes());
  public static final ByteBuffer RES_METHOD_NOT_ALLOWED =
      ByteBuffer.wrap("HTTP/1.1 405 Method Not Allowed\r\nContent-Length: 0\r\n\r\n".getBytes());

  enum Method {
    GET,
    POST,
    PUT,
    DELETE,
    CONNECT,
  }

  static class HttpRequest {
    private Method method;
    private String uri;
    private String version;

    public Method getMethod() {
      return this.method;
    }

    public String getUri() {
      return this.uri;
    }

    public HttpRequest(Method method, String uri, String version) {
      this.method = method;
      this.uri = uri;
      this.version = version;
    }
  }

  public static HttpRequest ParseHttpRequest(ByteBuffer in) throws IOException {
    ByteArrayInputStream is = new ByteArrayInputStream(in.array());
    BufferedReader br = new BufferedReader(new InputStreamReader(is));
    return ParseHttpRequest(br);
  }

  public static HttpRequest ParseHttpRequest(BufferedReader in) throws IOException {
    String line;
    try {
      line = in.readLine();
      String[] firstLineParts = line.split(HTTP_SP);
      if (firstLineParts.length < HTTP_NUM_HEADER_PARTS) {
        throw new IOException("invalid request");
      }
      Method method;
      method = Method.valueOf(firstLineParts[HTTP_METHOD_POS]);
      return new HttpRequest(
          method, firstLineParts[HTTP_URI_POS], firstLineParts[HTTP_VERSION_POS]);
    } catch (IllegalArgumentException e) {
      throw new IOException(e);
    }
  }

  static class Uri {
    private String domain;
    private int port;

    public String getDomain() {
      return this.domain;
    }

    public int getPort() {
      return this.port;
    }
  }

  public static Uri parseUri(String uri) throws IllegalUriException {

    String[] parts = uri.split(URI_SP);
    if (parts.length != URI_NUM_PARTS) {
      throw new IllegalUriException("expected URI format 'host:port' not received");
    }

    Uri dst = new Uri();
    dst.domain = parts[URI_DOMAIN_POS];
    dst.port = Integer.parseInt(parts[URI_PORT_POS]);
    return dst;
  }
}
