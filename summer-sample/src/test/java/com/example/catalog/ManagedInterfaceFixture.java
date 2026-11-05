package com.example.catalog;

import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
public class ManagedInterfaceFixture {
  @GetMapping(
      value = "/api/v1/platform/interface-fixture/{id}",
      params = "kind=a",
      headers = "X-Route=one",
      produces = "application/json")
  public Map<String, String> first(@PathVariable String id) {
    return Map.of("id", id, "kind", "a");
  }

  @GetMapping(
      value = "/api/v1/platform/interface-fixture/{id}",
      params = "kind=b",
      headers = "X-Route=one",
      produces = "application/json")
  public Map<String, String> second(@PathVariable String id) {
    return Map.of("id", id, "kind", "b");
  }

  @GetMapping("/api/v1/platform/interface-overlap")
  public Map<String, Boolean> shadowedMvc() {
    return Map.of("mvc", true);
  }

  public Map<String, String> replacementA() {
    return Map.of("handler", "A");
  }

  public Map<String, String> replacementB() {
    return Map.of("handler", "B");
  }
}
