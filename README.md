# PrivacyProxy 

### What's this?
A single-threaded privacy-preserving proxy. It's an https server that supports only the CONNECT method (and the proxying that should follow). An allowlist is used to identify supported proxy endpoints (in the format "host:port").

### In what ways is privacy preserved?
When used appropriately:
1. Client's IP address is hidden from the end-server.
2. Data sent between the client and the end-server is hidden from this proxy.

### How do I connect to this proxy?
1. Establish a TLS connection to the proxy.
2. Issue a CONNECT request identifying the desired destination input.
3. Negotiate TLS with the now-proxied endpoint 
4. Communicate with the end-server. At this point you have TLS under TLS, so the data will be double encrypted. One layer is peeled off by the proxy, the other by the final end-server.
 
See App.java for an example or setup a keystore/truststore and run `$ ./gradlew run`.

### Quickstart
```java
  SSLContext ctx = ...
  PrivacyProxyServer server = new PrivacyProxyServer(3000, ctx);
  server.addAllowedEndpoint("this.is.allowed.com", 443);
  server.addAllowedEndpoint("also.allowed.com", 443);
  server.start();
``` 

### Other considerations
This server does not guarantee privacy on its own. The client must carry some responsibility. Most importantly, only communicating with this proxy and the end-server after establishing TLS. This proxy does not inspect proxied traffic to ensure a TLS handshake occurs.


### TODOs 
- various code cleanup including proper logging & exception handling
- authorization to proxy
- offload heavier crypto tasks to other threads
- more robust testing to ensure these privacy claims are met
- enable rate limiting so allowlist cannot be enumerated

### Further reading:
- https://signal.org/blog/giphy-experiment/ 

>>>>>>> update readme
