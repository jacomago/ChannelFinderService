package org.phoebus.channelfinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Smoke tests that verify HTTP endpoints are accessible and return expected status codes. Uses
 * {@link TestRestTemplate} to make real HTTP requests against the running application.
 */
class HttpEndpointIT extends AbstractElasticsearchIT {

  @Autowired TestRestTemplate restTemplate;

  @Test
  void getTagsReturns200() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/ChannelFinder/resources/tags", String.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
  }

  @Test
  void getPropertiesReturns200() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/ChannelFinder/resources/properties", String.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
  }

  @Test
  void getChannelsReturns200() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/ChannelFinder/resources/channels", String.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
  }

  @Test
  void getScrollReturns200() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/ChannelFinder/resources/scroll", String.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
  }

  @Test
  void putMalformedJsonReturns400() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<String> entity = new HttpEntity<>("{", headers);
    ResponseEntity<String> response =
        restTemplate
            .withBasicAuth("admin", "adminPass")
            .exchange("/ChannelFinder/resources/tags/badTag", HttpMethod.PUT, entity, String.class);
    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
  }

  @Test
  void unauthenticatedPutReturns401() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<String> entity =
        new HttpEntity<>("{\"name\":\"testTag\",\"owner\":\"testOwner\"}", headers);
    ResponseEntity<String> response =
        restTemplate.exchange(
            "/ChannelFinder/resources/tags/testTag", HttpMethod.PUT, entity, String.class);
    assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
  }

  @Test
  void authenticatedPutTagSucceeds() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<String> entity =
        new HttpEntity<>("{\"name\":\"testTag\",\"owner\":\"testOwner\"}", headers);
    ResponseEntity<String> response =
        restTemplate
            .withBasicAuth("admin", "adminPass")
            .exchange(
                "/ChannelFinder/resources/tags/testTag", HttpMethod.PUT, entity, String.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
  }
}
