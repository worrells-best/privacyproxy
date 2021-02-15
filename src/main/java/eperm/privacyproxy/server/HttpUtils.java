package eperm.privacyproxy.server;

import java.io.*;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import javax.net.ssl.*;

public class HttpUtils {
  static final String SP = " ";
  static final char METHOD_POS = 0;
  static final char URI_POS = 1;
  static final char VERSION_POS = 2;
  static final int NUM_HEADER_PARTS = 3;
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
      String[] firstLineParts = line.split(SP);
      if (firstLineParts.length < NUM_HEADER_PARTS) {
        throw new IOException("invalid request");
      }
      Method method;
      method = Method.valueOf(firstLineParts[METHOD_POS]);
      return new HttpRequest(method, firstLineParts[URI_POS], firstLineParts[VERSION_POS]);
    } catch (IllegalArgumentException e) {
      throw new IOException(e);
    }
  }
}
