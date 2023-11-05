package net.taskwolf.access.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.taskwolf.core.distribution.Node;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.http.HttpClient;
import java.util.concurrent.CompletableFuture;

@RestController
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class WebController {
  private final HttpClient httpClient;
  private final Node node;

  @RequestMapping(value = "/**", method = RequestMethod.GET)
  public CompletableFuture<ResponseEntity<byte[]>> processRequest(
    HttpServletRequest request
  ) throws Exception {
    return WebRedirection.create(httpClient, node, request).redirect();
  }
}
