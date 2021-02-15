package eperm.privacyproxy.server;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.concurrent.*;
import javax.net.ssl.*;

enum ChannelState {
  UNINITIALIZED,    // connection accepted, but proxy not initialized
  ACTIVE_CLIENT,    // proxy initialized, client ==> endpoint
  ACTIVE_ENDPOINT,  // proxy initialized, client <== endpoint
  CLOSED,           // closed
}

// UniProxyChannel represents a unidirectional proxy channel
public class UniProxyChannel {
  private ChannelState state;
  private ByteChannel srcChannel;
  private ByteChannel dstChannel;
  private ByteBuffer buffer = ByteBuffer.allocate(1024);

  UniProxyChannel() {
    state = ChannelState.UNINITIALIZED;
  }

  public UniProxyChannel(ChannelState state, ByteChannel srcChannel, ByteChannel dstChannel) {
    this.state = state;
    this.srcChannel = srcChannel;
    this.dstChannel = dstChannel;
  }

  public ChannelState getChannelState() {
    return state;
  }

  public void setChannelState(ChannelState state) {
    this.state = state;
  }

  public ByteChannel getSrcChannel() {
    return srcChannel;
  }

  public void setSrcChannel(ByteChannel srcChannel) {
    this.srcChannel = srcChannel;
  }

  public ByteChannel getDstChannel() {
    return dstChannel;
  }

  public void setDstChannel(ByteChannel dstChannel) {
    this.dstChannel = dstChannel;
  }

  public int pipeAvailableData() throws IOException {
    buffer.clear();
    int bytes_piped = srcChannel.read(buffer);
    if (bytes_piped > 0) {
      buffer.flip();
      bytes_piped = dstChannel.write(buffer);
    }

    return bytes_piped;
  }

  // unconditionally try to close all connections
  public void close() {
    try {
      srcChannel.close();
    } catch (IOException e) {
    }
    try {
      dstChannel.close();
    } catch (IOException e) {
    }
    state = ChannelState.CLOSED;
  }
}
