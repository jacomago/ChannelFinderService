package org.phoebus.channelfinder;

import static java.lang.Math.min;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.phoebus.channelfinder.entity.Channel;
import org.phoebus.channelfinder.entity.Property;
import org.phoebus.channelfinder.entity.SearchResult;
import org.phoebus.channelfinder.entity.Tag;
import org.phoebus.channelfinder.repository.ChannelRepository;
import org.phoebus.channelfinder.repository.PropertyRepository;
import org.phoebus.channelfinder.repository.TagRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

class ChannelRepositorySearchIT extends AbstractElasticsearchIT {
  private static final Logger logger = Logger.getLogger(ChannelRepositorySearchIT.class.getName());

  // Distribution buckets: valBucket[i] channels get tag/property with value valBucket[i]
  // valBucketSize[i] is the count of channels assigned to bucket i (totals to 1000 per cell)
  static final List<Integer> valBucket = List.of(0, 1, 2, 5, 10, 20, 50, 100, 200, 500);
  static final List<Integer> valBucketSize =
      List.of(
          1000 - valBucket.stream().mapToInt(Integer::intValue).sum(),
          1, 2, 5, 10, 20, 50, 100, 200, 500);

  // 10 groups × CELLS × 1000 channels must exceed ELASTIC_LIMIT (10 000) — minimum CELLS is 2
  private final int CELLS = 2;

  @Autowired ChannelRepository channelRepository;
  @Autowired TagRepository tagRepository;
  @Autowired PropertyRepository propertyRepository;

  @Value("${elasticsearch.query.size:10000}")
  int ELASTIC_LIMIT;

  private final List<String> channelNames = new ArrayList<>();

  @BeforeEach
  public void setup() throws InterruptedException {
    cleanup();
    populateTestData();
    Thread.sleep(5000);
  }

  @AfterEach
  public void cleanup() {
    MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
    map.set("~name", "*");
    channelRepository
        .search(map)
        .channels()
        .forEach(c -> channelRepository.deleteById(c.getName()));
    tagRepository.findAll().forEach(t -> tagRepository.deleteById(t.getName()));
    propertyRepository.findAll().forEach(p -> propertyRepository.deleteById(p.getName()));
    channelNames.clear();
  }

  /**
   * Populate test data with self-documenting channel names:
   * - For each group g (0-9) and each bucket value v from valBucket:
   *   - CELLS * valBucketSize[bi] channels named channel_group{g}_tag{v}_{k}
   *   - Each channel carries exactly one property (group{g}=v) and one tag (group{g}_{v})
   * Total channels: 10 * CELLS * 1000
   */
  private void populateTestData() {
    // Create group properties
    List<Property> properties = new ArrayList<>();
    for (int g = 0; g < 10; g++) {
      properties.add(new Property("group" + g, "testOwner"));
    }
    propertyRepository.indexAll(properties);

    // Create group tags — for each group id and each bucket value
    List<Tag> tags = new ArrayList<>();
    for (int g = 0; g < 10; g++) {
      for (int v : valBucket) {
        tags.add(new Tag("group" + g + "_" + v, "testOwner"));
      }
    }
    tagRepository.indexAll(tags);

    // Create channels: name encodes the single group property and tag each channel holds
    for (int g = 0; g < 10; g++) {
      List<Channel> batch = new ArrayList<>();
      for (int bi = 0; bi < valBucket.size(); bi++) {
        int v = valBucket.get(bi);
        int count = CELLS * valBucketSize.get(bi);
        Property prop = new Property("group" + g, "testOwner", String.valueOf(v));
        Tag tag = new Tag("group" + g + "_" + v, "testOwner");
        for (int k = 0; k < count; k++) {
          String name = "channel_group" + g + "_tag" + v + "_" + k;
          Channel c = new Channel(name, "testOwner", List.of(prop), List.of(tag));
          batch.add(c);
          channelNames.add(name);
        }
      }
      channelRepository.indexAll(batch);
    }
  }

  @Test
  void searchTest() {
    MultiValueMap<String, String> searchParameters = new LinkedMultiValueMap<>();

    // Search for a single unique channel by exact name
    searchParameters.add("~name", channelNames.get(0));
    SearchResult result = channelRepository.search(searchParameters);
    long countResult = channelRepository.count(searchParameters);
    Assertions.assertEquals(1, result.count());
    Assertions.assertEquals(1, result.channels().size());
    Assertions.assertEquals(1, countResult);
    Assertions.assertEquals(result.channels().getFirst().getName(), channelNames.getFirst());

    // ? wildcard across groups: valBucketSize[1]=1 and CELLS=2 gives k=0,1; suffix "_0" picks k=0
    logger.log(Level.INFO, "Search with ? wildcard across groups");
    searchName(10, 10, "channel_group?_tag1_0");

    // ? wildcard within one group/tag: matches k=0..CELLS-1 (assumes CELLS < 10)
    logger.log(Level.INFO, "Search with ? wildcard within a group/tag");
    searchName(CELLS, CELLS, "channel_group0_tag1_?");

    // * wildcard: all channels across all groups
    long totalChannels = 10L * CELLS * 1000;
    long elasticDefaultCount = min(totalChannels, ELASTIC_LIMIT);
    logger.log(Level.INFO, "Search for all {0} channels via *", totalChannels);
    searchName((int) elasticDefaultCount, (int) totalChannels, "channel_*");

    // OR search: two groups via | and , produce the same results
    logger.log(Level.INFO, "Search for two groups via | and ,");
    long twoGroupCount = 2L * CELLS * 1000;
    long twoGroupElastic = min(twoGroupCount, ELASTIC_LIMIT);
    searchParameters.clear();
    searchParameters.add("~name", "channel_group0_*|channel_group1_*");
    assertSearchCount(twoGroupElastic, (int) twoGroupElastic, twoGroupCount, searchParameters);

    searchParameters.clear();
    searchParameters.add("~name", "channel_group0_*,channel_group1_*");
    assertSearchCount(twoGroupElastic, (int) twoGroupElastic, twoGroupCount, searchParameters);

    // track_total_hits: verify exact count beyond ELASTIC_LIMIT
    logger.log(Level.INFO, "Search with track_total_hits for accurate total count");
    searchParameters.clear();
    searchParameters.add("~name", "channel_*");
    searchParameters.add("~track_total_hits", "true");
    assertSearchCount(totalChannels, (int) elasticDefaultCount, totalChannels, searchParameters);

    searchParameters.clear();
    searchParameters.add("~name", "channel_group0_*,channel_group1_*");
    searchParameters.add("~track_total_hits", "true");
    assertSearchCount(twoGroupCount, (int) twoGroupElastic, twoGroupCount, searchParameters);

    // Property and tag searches: verify counts per group and bucket
    logger.log(Level.INFO, "Search for channels based on a tag or property");
    for (int id = 1; id < valBucket.size(); id++) {
      for (int bucketIndex = 0; bucketIndex < valBucket.size(); bucketIndex++) {
        checkGroup(
            valBucketSize.get(bucketIndex),
            "~tag",
            "group" + id + "_" + valBucket.get(bucketIndex));
        checkGroup(
            valBucketSize.get(bucketIndex),
            "group" + id,
            String.valueOf(valBucket.get(bucketIndex)));
      }
    }
  }

  private void searchName(int expectedChannels, int expectedQueryCount, String name) {
    MultiValueMap<String, String> searchParameters = new LinkedMultiValueMap<>();
    searchParameters.add("~name", name);
    assertSearchCount(expectedChannels, expectedChannels, expectedQueryCount, searchParameters);
  }

  private void assertSearchCount(
      long expectedResultCount,
      int expectedChannelsCount,
      long expectedQueryCount,
      MultiValueMap<String, String> searchParameters) {
    logger.log(
        Level.INFO,
        "Search for {0} expected {1} results {2} channels {3} queries",
        new Object[] {searchParameters, expectedResultCount, expectedChannelsCount, expectedQueryCount});
    // Act
    SearchResult result = channelRepository.search(searchParameters);

    // Assert
    Assertions.assertEquals(expectedResultCount, result.count());
    Assertions.assertEquals(expectedChannelsCount, result.channels().size());
    Assertions.assertEquals(expectedQueryCount, channelRepository.count(searchParameters));
  }

  private void checkGroup(int bucket, String key, String value) {
    MultiValueMap<String, String> searchParameters = new LinkedMultiValueMap<>();
    searchParameters.add(key, value);

    SearchResult result = channelRepository.search(searchParameters);
    Integer expectedCount = CELLS * bucket;
    logger.log(
        Level.INFO,
        "Search for {0} expected {1} results",
        new Object[] {maptoString(searchParameters), expectedCount});
    Assertions.assertEquals(
        min(expectedCount, ELASTIC_LIMIT),
        Integer.valueOf(result.channels().size()),
        "Search: " + maptoString(searchParameters));
    Assertions.assertEquals(
        expectedCount, Integer.valueOf((int) channelRepository.count(searchParameters)));
  }

  private String maptoString(MultiValueMap<String, String> searchParameters) {
    StringBuffer sb = new StringBuffer();
    searchParameters.forEach((key, value) -> sb.append(key).append(" ").append(value));
    return sb.toString();
  }
}
