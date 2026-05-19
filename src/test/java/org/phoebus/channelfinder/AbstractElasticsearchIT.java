package org.phoebus.channelfinder;

import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

/**
 * Base class for integration tests that need an Elasticsearch instance.
 *
 * <p>Uses Testcontainers to start a single Elasticsearch container that is shared across all test
 * classes extending this base. The container is started eagerly in a static initializer to
 * guarantee it is ready before {@code @DynamicPropertySource} resolves connection properties.
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
 *     ChannelRepository channelRepository;
 *
 *     @Test
 *     void myTest() {
 *         // Elasticsearch is available and configured
 *     }
 * }
 * }</pre>
 */
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

  static final ElasticsearchContainer elasticsearch =
      new ElasticsearchContainer(ELASTICSEARCH_IMAGE)
          .withEnv("xpack.security.enabled", "false")
          .withEnv("discovery.type", "single-node")
          .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");

  static {
    elasticsearch.start();
  }

  @DynamicPropertySource
  static void elasticsearchProperties(DynamicPropertyRegistry registry) {
    registry.add("elasticsearch.host_urls", elasticsearch::getHttpHostAddress);
    String suffix = String.valueOf(System.nanoTime());
    registry.add("elasticsearch.tag.index", () -> "test_" + suffix + "_cf_tags");
    registry.add("elasticsearch.property.index", () -> "test_" + suffix + "_cf_properties");
    registry.add("elasticsearch.channel.index", () -> "test_" + suffix + "_channelfinder");
  }
}
