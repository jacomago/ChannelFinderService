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

  // Need at least 10 000 channels to test Elastic search beyond the 10 000 default result limit
  // So needs to be a minimum of 7
  private final int CELLS = 100;

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
   * Populate test data matching the pattern from the old PopulateDBConfiguration:
   * - Per cell: 1000 SR channels + 500 BR channels
   * - 10 property groups (group0-group9) with values from valBucket
   * - 10 tag groups with tags named group{id}_{value} for each valBucket value
   * - Distribution: valBucketSize[i] channels get assigned to bucket valBucket[i]
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

    // Build a token list: tokens1000[i] = the bucket value assigned to channel index i
    List<Integer> tokens1000 = new ArrayList<>(1000);
    for (int bi = 0; bi < valBucketSize.size(); bi++) {
      for (int j = 0; j < valBucketSize.get(bi); j++) {
        tokens1000.add(valBucket.get(bi));
      }
    }

    // Create channels for each cell
    List<Channel> batch = new ArrayList<>();
    for (int cell = 1; cell <= CELLS; cell++) {
      String cellStr = String.format("%03d", cell);

      // 1000 SR channels
      for (int ch = 0; ch < 1000; ch++) {
        String name = "SR:C" + cellStr + "-BI:" + ch + "{BLA}Pos:" + (ch % 2) + "-RB";
        int bucketVal = tokens1000.get(ch);

        List<Property> chProps = new ArrayList<>();
        List<Tag> chTags = new ArrayList<>();
        for (int g = 0; g < 10; g++) {
          chProps.add(new Property("group" + g, "testOwner", String.valueOf(bucketVal)));
          chTags.add(new Tag("group" + g + "_" + bucketVal, "testOwner"));
        }

        Channel c = new Channel(name, "testOwner", chProps, chTags);
        batch.add(c);
        channelNames.add(name);
      }

      // 500 BR channels
      for (int ch = 0; ch < 500; ch++) {
        String name = "BR:C" + cellStr + "-BI:" + ch + "{BLA}Pos:" + (ch % 2) + "-RB";
        Channel c = new Channel(name, "testOwner");
        batch.add(c);
        channelNames.add(name);
      }

      // Index in batches to avoid excessive memory use
      if (cell % 25 == 0 || cell == CELLS) {
        channelRepository.indexAll(batch);
        batch.clear();
      }
    }
  }

  @Test
  void searchTest() {
    MultiValueMap<String, String> searchParameters = new LinkedMultiValueMap<>();

    // Search for a single unique channel
    searchParameters.add("~name", channelNames.get(0));
    SearchResult result = channelRepository.search(searchParameters);
    long countResult = channelRepository.count(searchParameters);
    Assertions.assertEquals(1, result.count());
    Assertions.assertEquals(1, result.channels().size());
    Assertions.assertEquals(1, countResult);
    Assertions.assertEquals(result.channels().get(0).getName(), channelNames.get(0));

    logger.log(Level.INFO, "Search for all channels via wildcards");
    searchName(2, 2, "BR:C001-BI:2{BLA}Pos:?-RB");

    searchName(4, 4, "BR:C001-BI:?{BLA}Pos:*");

    logger.log(Level.INFO, "Search for all 1000 channels");
    searchName(min(1000 * CELLS, ELASTIC_LIMIT), 1000 * CELLS, "SR*");

    logger.log(Level.INFO, "Search for all 1000 SR channels and all 500 booster channels");
    long allCount = 1500L * CELLS;
    long elasticDefaultCount = min(allCount, ELASTIC_LIMIT);
    searchParameters.clear();
    searchParameters.add("~name", "SR*|BR*");
    assertSearchCount(elasticDefaultCount, (int) elasticDefaultCount, allCount, searchParameters);

    searchParameters.clear();
    searchParameters.add("~name", "SR*,BR*");
    assertSearchCount(elasticDefaultCount, (int) elasticDefaultCount, allCount, searchParameters);

    logger.log(Level.INFO, "Search for all 1000 SR channels and all 500 booster channels");
    searchParameters.clear();
    searchParameters.add("~name", "SR*|BR*");
    searchParameters.add("~track_total_hits", "true");
    assertSearchCount(allCount, (int) elasticDefaultCount, allCount, searchParameters);

    searchParameters.clear();
    searchParameters.add("~name", "SR*,BR*");
    searchParameters.add("~track_total_hits", "true");
    assertSearchCount(allCount, (int) elasticDefaultCount, allCount, searchParameters);

    logger.log(Level.INFO, "Search for channels based on a tag");
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
        "Search for "
            + searchParameters
            + " expected "
            + expectedResultCount
            + " results "
            + expectedChannelsCount
            + " channels "
            + expectedQueryCount
            + " queries");
    // Act
    SearchResult result = channelRepository.search(searchParameters);

    // Assert
    Assertions.assertEquals(expectedResultCount, result.count());
    Assertions.assertEquals(expectedChannelsCount, result.channels().size());
    Assertions.assertEquals(expectedQueryCount, channelRepository.count(searchParameters));
  }

  private void checkGroup(int bucket, String key, String value) {
    MultiValueMap<String, String> searchParameters = new LinkedMultiValueMap<>();

    searchParameters.add("~name", "SR*");
    searchParameters.add(key, value);

    SearchResult result = channelRepository.search(searchParameters);
    Integer expectedCount = CELLS * bucket;
    logger.log(
        Level.INFO,
        "Search for " + maptoString(searchParameters) + " expected " + expectedCount + " results");
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
