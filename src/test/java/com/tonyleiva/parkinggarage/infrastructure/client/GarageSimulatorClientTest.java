package com.tonyleiva.parkinggarage.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class GarageSimulatorClientTest {
  private HttpServer server;

  @AfterEach
  void stopServer() {
    if (server != null) server.stop(0);
  }

  @Test
  void shouldMapSnakeCasePayloadWithoutRealSimulator() throws Exception {
    String json =
        """
{"garage":[{"sector":"A","base_price":40.5000,"max_capacity":1,"open_hour":"00:00","close_hour":"23:59","duration_limit_minutes":1440}],
 "spots":[{"id":1,"sector":"A","lat":-23.56168400,"lng":-46.65598100,"occupied":false}]}
""";
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/garage",
        exchange -> {
          byte[] body = json.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    RestClient restClient =
        RestClient.builder()
            .baseUrl("http://localhost:" + server.getAddress().getPort())
            .requestFactory(new JdkClientHttpRequestFactory())
            .build();

    var response = new GarageSimulatorClient(restClient).fetchConfiguration();

    assertThat(response.garage().getFirst().basePrice()).isEqualByComparingTo("40.5000");
    assertThat(response.garage().getFirst().durationLimitMinutes()).isEqualTo(1440);
    assertThat(response.spots().getFirst().lat()).isEqualByComparingTo("-23.56168400");
  }
}
