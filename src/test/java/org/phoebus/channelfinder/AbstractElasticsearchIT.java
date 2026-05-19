package org.phoebus.channelfinder;

import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests that need an Elasticsearch instance.
 *
 * <p>Uses Testcontainers to start a single Elasticsearch container that is shared across all test
 * classes extending this base. The container is started once and reused for all tests via the
 * {@code reuse} feature and the static {@code @Container} lifecycle.
 *
 * <p>Subclasses get a fully running Spring Boot application context with {@code RANDOM_PORT} to
 * avoid port conflicts, and the Elasticsearch connection is automatically configured via
 * {@code @DynamicPropertySource}.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * class MyIT extends AbstractElasticsearchIT {
 *
 *     @Autowired
 *     TestRestTemplate restTemplate;
 *
 *     @Test
 *     void myTest() {
 *         // restTemplate is wired to the random port
 *         // Elasticsearch is available and configured
 *     }
 * }
 * }</pre>
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "demo_auth.enabled=true",
      "elasticsearch.create.indices=true",
      "ldap.enabled=false",
      "embedded_ldap.enabled=false"
    })
public abstract class AbstractElasticsearchIT {

  private static final String ELASTICSEARCH_IMAGE =
      "docker.elastic.co/elasticsearch/elasticsearch:8.18.0";

  @Container
  static final ElasticsearchContainer elasticsearch =
      new ElasticsearchContainer(ELASTICSEARCH_IMAGE)
          .withEnv("xpack.security.enabled", "false")
          .withEnv("discovery.type", "single-node")
          .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");

  @DynamicPropertySource
  static void elasticsearchProperties(DynamicPropertyRegistry registry) {
    registry.add("elasticsearch.host_urls", elasticsearch::getHttpHostAddress);
    // Use unique index names per test run to avoid cross-test contamination
    String suffix = String.valueOf(System.nanoTime());
    registry.add("elasticsearch.tag.index", () -> "test_" + suffix + "_cf_tags");
    registry.add("elasticsearch.property.index", () -> "test_" + suffix + "_cf_properties");
    registry.add("elasticsearch.channel.index", () -> "test_" + suffix + "_channelfinder");
  }
}
