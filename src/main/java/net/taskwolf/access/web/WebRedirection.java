package net.taskwolf.access.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import net.taskwolf.core.distribution.Node;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor(staticName = "create")
public class WebRedirection {
  public static WebRedirection create(
    HttpClient httpClient, Node self, HttpServletRequest request
  ) {
    return WebRedirection.create(httpClient, self.hostname(), self.webPort(),
      request);
  }

  private final HttpClient httpClient;
  private final String hostname;
  private final int webPort;
  private final HttpServletRequest request;

  public CompletableFuture<ResponseEntity<byte[]>> redirect() throws Exception {
    var uri = createUri();
    var requestBuilder = HttpRequest.newBuilder().uri(uri).GET();
    applyHeaders(requestBuilder);
    var httpRequest = requestBuilder.build();
    return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray())
      .thenApply(this::createResponseEntity);
  }

  private ResponseEntity<byte[]> createResponseEntity(HttpResponse<byte[]> httpResponse) {
    var headers = new HttpHeaders();
    for (var header : httpResponse.headers().map().entrySet()) {
      headers.add(header.getKey(), header.getValue().get(0));
    }
    return ResponseEntity.ok()
      .headers(headers)
      .body(httpResponse.body());
  }

  private void applyHeaders(HttpRequest.Builder requestBuilder) {
    var headerNames = request.getHeaderNames();
    while (headerNames.hasMoreElements()) {
      var headerName = headerNames.nextElement();
      requestBuilder.setHeader(headerName, request.getHeader(headerName));
    }
  }

  private URI createUri() throws Exception {
    var uri = new URI("http", null, hostname, webPort, null, null, null);
    return UriComponentsBuilder.fromUri(uri)
      .path(request.getRequestURI())
      .query(request.getQueryString())
      .build(true).toUri();
  }
}
