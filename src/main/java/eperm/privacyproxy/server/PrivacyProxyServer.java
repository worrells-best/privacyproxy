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
    } catch (IOException e) {
      e.printStackTrace();
      return;
    }

    while (selector.isOpen()) {
      try {
        selector.select();
      } catch (IOException e) {
        e.printStackTrace();
        continue;
      }

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
        } catch (IOException e) {
          // TODO: something went wrong. consider key.cancel()
          e.printStackTrace();
        }

        iter.remove();
      }
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
    HttpUtils.HttpRequest req = HttpUtils.ParseHttpRequest(buffer);
    switch (req.getMethod()) {
      case CONNECT:
        try {
          if (handleConnectRequest(req.getUri(), key, clientTls, selector) == 0) {
            clientTls.write(HttpUtils.RES_CONNECTION_ESTABLISHED);
          } else {
            clientTls.write(HttpUtils.RES_FORBIDDEN);
          }
        } catch (ConnectRequestException e) {
          // TODO: internal error
          clientTls.write(HttpUtils.RES_METHOD_NOT_ALLOWED);
          e.printStackTrace();
        }
        break;
      default:
        clientTls.write(HttpUtils.RES_METHOD_NOT_ALLOWED);
        System.out.println("received unsupported request");
    }
  }

  // returns 0 on success, -1 on forbidden destination
  private int handleConnectRequest(
      String uri, SelectionKey key, ByteChannel clientChannel, Selector selector)
      throws ConnectRequestException {
    try {
      HttpUtils.Uri dst = HttpUtils.parseUri(uri);
      System.out.printf("received CONNECT request for %s:%d...\n", dst.getDomain(), dst.getPort());
      if (!endpointAllowed(dst.getDomain(), dst.getPort())) {
        System.out.printf(
            "rejected CONNECT request for %s:%d...\n", dst.getDomain(), dst.getPort());
        return -1;
      }

      // open connection to requested server
      SocketChannel endpointChannel = SocketChannel.open();
      endpointChannel.connect(new InetSocketAddress(dst.getDomain(), dst.getPort()));
      endpointChannel.configureBlocking(false);

      // register unidirectional proxy (client <== server)
      SelectionKey endpointKey = endpointChannel.register(selector, SelectionKey.OP_READ);
      UniProxyChannel endpointUpc =
          new UniProxyChannel(ChannelState.ACTIVE_ENDPOINT, endpointChannel, clientChannel);
      endpointKey.attach(endpointUpc);

      // update current channel as unidirectional proxy (client ==> server)
      UniProxyChannel clientUpc =
          new UniProxyChannel(ChannelState.ACTIVE_CLIENT, clientChannel, endpointChannel);
      key.attach(clientUpc);

      System.out.printf("initialized connection to %s:%d\n", dst.getDomain(), dst.getPort());

      return 0;
    } catch (IOException | IllegalUriException e) {
      throw new ConnectRequestException(e);
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
