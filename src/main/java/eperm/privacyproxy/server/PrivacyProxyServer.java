package eperm.privacyproxy.server;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.*;
import javax.net.ssl.*;
import tlschannel.NeedsReadException;
import tlschannel.ServerTlsChannel;
import tlschannel.TlsChannel;

public class PrivacyProxyServer {
  private int port;
  private SSLContext sslContext;
  private HashMap<String, Boolean> allowedEndpoints = new HashMap<String, Boolean>();
  private Selector selector;

  public PrivacyProxyServer(int port, SSLContext sslContext) {
    this.port = port;
    this.sslContext = sslContext;
  }

  public void addAllowedEndpoint(String host, int port) {
    allowedEndpoints.put(String.format("%s:%d", host, port), true);
  }

  private boolean endpointAllowed(String host, int port) {
    String endpoint = String.format("%s:%d", host, port);
    return allowedEndpoints.getOrDefault(endpoint, false);
  }

  public void start() {
    try {
      selector = Selector.open();
      ServerSocketChannel serverSocket = ServerSocketChannel.open();
      serverSocket.bind(new InetSocketAddress("localhost", port));
      serverSocket.configureBlocking(false);
      serverSocket.register(selector, SelectionKey.OP_ACCEPT);

      while (selector.isOpen()) {
        selector.select();
        Set<SelectionKey> selectedKeys = selector.selectedKeys();
        Iterator<SelectionKey> iter = selectedKeys.iterator();

        while (iter.hasNext()) {
          SelectionKey key = iter.next();
          try {
            if (key.isAcceptable()) { // new connection
              handleNewConnection(key, selector);
            } else if (key.isReadable()) { // data received from either client or endpoint
              UniProxyChannel proxyChan = (UniProxyChannel) key.attachment();
              switch (proxyChan.getChannelState()) {
                case UNINITIALIZED:
                  handleInitialization(key, selector);
                  break;
                case ACTIVE_CLIENT:
                case ACTIVE_ENDPOINT:
                  handleDataStream(key);
                  break;
                default:
                  // TODO: this should never happen
                  break;
              }
            }
          } catch (NeedsReadException e) {
            key.interestOps(SelectionKey.OP_READ);
          }

          iter.remove();
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      return;
    }
  }

  public void startInBackground() {
    Runnable r =
        new Runnable() {
          public void run() {
            start();
          }
        };
    new Thread(r).start();
  }

  public void stop() {
    try {
      selector.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void handleNewConnection(SelectionKey key, Selector selector) throws IOException {
    ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
    SocketChannel client = serverChannel.accept();
    client.configureBlocking(false);

    // create tls "sidecar" for this key which all data will be piped through
    TlsChannel tlsChannel = ServerTlsChannel.newBuilder(client, sslContext).build();
    SelectionKey newKey = client.register(selector, SelectionKey.OP_READ);

    UniProxyChannel uniChannel = new UniProxyChannel(ChannelState.UNINITIALIZED, tlsChannel, null);
    newKey.attach(uniChannel);
  }

  // initialization may occur with a CONNECT request to an endpoint in our allowlist
  private void handleInitialization(SelectionKey key, Selector selector) throws IOException {
    UniProxyChannel uniChannel = (UniProxyChannel) key.attachment();
    ByteChannel clientTls = uniChannel.getSrcChannel();

    ByteBuffer buffer = ByteBuffer.allocate(1024);
    clientTls.read(buffer);
    try {
      HttpUtils.HttpRequest req = HttpUtils.ParseHttpRequest(buffer);
      switch (req.getMethod()) {
        case CONNECT:
          ProxyDestination dst;
          dst = ProxyDestination.parseUri(req.getUri());
          System.out.printf(
              "received CONNECT request for %s:%d...\n", dst.getDomain(), dst.getPort());
          if (!endpointAllowed(dst.getDomain(), dst.getPort())) {
            System.out.printf(
                "rejected CONNECT request for %s:%d...\n", dst.getDomain(), dst.getPort());
            clientTls.write(HttpUtils.RES_FORBIDDEN);
            return;
          }

          // open connection to requested server
          SocketChannel endpointSocket = SocketChannel.open();
          endpointSocket.connect(new InetSocketAddress(dst.getDomain(), dst.getPort()));
          endpointSocket.configureBlocking(false);

          // register with our selector
          SelectionKey endpointKey = endpointSocket.register(selector, SelectionKey.OP_READ);
          UniProxyChannel endpointUpc =
              new UniProxyChannel(ChannelState.ACTIVE_ENDPOINT, endpointSocket, clientTls);
          endpointKey.attach(endpointUpc);

          UniProxyChannel clientUpc =
              new UniProxyChannel(ChannelState.ACTIVE_CLIENT, clientTls, endpointSocket);
          key.attach(clientUpc);

          clientTls.write(HttpUtils.RES_CONNECTION_ESTABLISHED);
          System.out.printf("initialized connection to %s:%d\n", dst.getDomain(), dst.getPort());
          break;
        default:
          clientTls.write(HttpUtils.RES_METHOD_NOT_ALLOWED);
          throw new IOException("unsupported request");
      }
    } catch (IllegalUriException e) {
      clientTls.write(HttpUtils.RES_METHOD_NOT_ALLOWED);
    }
  }

  private void handleDataStream(SelectionKey key) throws IOException {
    UniProxyChannel uniChannel = (UniProxyChannel) key.attachment();
    try {
      if (uniChannel.pipeAvailableData() == -1) {
        uniChannel.close();
        key.cancel();
      }
    } catch (IOException e) {
      uniChannel.close();
      key.cancel();
    }
  }
}
