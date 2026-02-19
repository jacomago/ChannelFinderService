package org.phoebus.channelfinder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.phoebus.channelfinder.entity.Channel;
import org.phoebus.channelfinder.entity.Property;
import org.phoebus.channelfinder.entity.Scroll;
import org.phoebus.channelfinder.entity.Tag;
import org.phoebus.channelfinder.repository.ChannelRepository;
import org.phoebus.channelfinder.repository.PropertyRepository;
import org.phoebus.channelfinder.repository.TagRepository;
import org.phoebus.channelfinder.web.v0.api.IChannelScroll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

class ChannelScrollControllerIT extends AbstractElasticsearchIT {

  @Autowired IChannelScroll channelScroll;

  @Autowired ChannelRepository channelRepository;

  @Autowired TagRepository tagRepository;

  @Autowired PropertyRepository propertyRepository;

  final List<Integer> val_bucket = Arrays.asList(1, 2, 5, 10, 20, 50, 100, 200, 500);

  private final List<String> channelNames = new ArrayList<>();

  @BeforeEach
  public void setup() throws InterruptedException {
    populateTestData();
    Thread.sleep(10000);
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
   * Create 1 cell of test data: 1000 SR channels + 500 BR channels with tags and properties
   * matching the valBucket distribution.
   */
  private void populateTestData() {
    // valBucketSize[i] = count of channels with tag/property value val_bucket[i]
    // Total must be 1000 for SR channels
    List<Integer> valBucketSize = Arrays.asList(1, 2, 5, 10, 20, 50, 100, 200, 500);

    // Create group properties
    List<Property> properties = new ArrayList<>();
    for (int g = 0; g < 10; g++) {
      properties.add(new Property("group" + g, "testOwner"));
    }
    propertyRepository.indexAll(properties);

    // Create group tags
    List<Tag> tags = new ArrayList<>();
    for (int g = 0; g < 10; g++) {
      for (int v : val_bucket) {
        tags.add(new Tag("group" + g + "_" + v, "testOwner"));
      }
    }
    tagRepository.indexAll(tags);

    // Build token list mapping channel index -> bucket value
    List<Integer> tokens1000 = new ArrayList<>(1000);
    // First, pad with 0-bucket channels to reach 1000
    int nonZeroSum = val_bucket.stream().mapToInt(Integer::intValue).sum();
    for (int i = 0; i < 1000 - nonZeroSum; i++) {
      tokens1000.add(0);
    }
    for (int bi = 0; bi < valBucketSize.size(); bi++) {
      for (int j = 0; j < valBucketSize.get(bi); j++) {
        tokens1000.add(val_bucket.get(bi));
      }
    }

    String cellStr = "001";
    List<Channel> batch = new ArrayList<>();

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

      batch.add(new Channel(name, "testOwner", chProps, chTags));
      channelNames.add(name);
    }

    // 500 BR channels
    for (int ch = 0; ch < 500; ch++) {
      String name = "BR:C" + cellStr + "-BI:" + ch + "{BLA}Pos:" + (ch % 2) + "-RB";
      batch.add(new Channel(name, "testOwner"));
      channelNames.add(name);
    }

    channelRepository.indexAll(batch);
  }

  /**
   * Test searching for channels based on name
   *
   * @throws InterruptedException
   */
  @Test
  void searchNameTest() throws InterruptedException {
    MultiValueMap<String, String> searchParameters = new LinkedMultiValueMap<String, String>();
    // Search for a single unique channel
    searchParameters.add("~name", channelNames.get(0));
    Scroll scrollResult = channelScroll.query(searchParameters);
    List<Channel> result = new ArrayList<>(scrollResult.getChannels());
    while (scrollResult.getChannels().size() == 100) {
      scrollResult = channelScroll.query(scrollResult.getId(), searchParameters);
      result.addAll(scrollResult.getChannels());
    }
    Assertions.assertTrue(
        result.size() == 1 && result.get(0).getName().equals(channelNames.get(0)));

    // Search for all channels via wildcards
    searchParameters.clear();
    searchParameters.add("~name", "BR:C001-BI:2{BLA}Pos:?-RB");
    scrollResult = channelScroll.query(searchParameters);
    result = new ArrayList<>(scrollResult.getChannels());
    while (scrollResult.getChannels().size() == 100) {
      scrollResult = channelScroll.query(scrollResult.getId(), searchParameters);
      result.addAll(scrollResult.getChannels());
    }
    Assertions.assertSame(2, result.size(), "Expected 2 but got " + result.size());

    searchParameters.clear();
    searchParameters.add("~name", "BR:C001-BI:?{BLA}Pos:*");
    scrollResult = channelScroll.query(searchParameters);
    result = new ArrayList<>(scrollResult.getChannels());
    while (scrollResult.getChannels().size() == 100) {
      scrollResult = channelScroll.query(scrollResult.getId(), searchParameters);
      result.addAll(scrollResult.getChannels());
    }
    Assertions.assertSame(4, result.size(), "Expected 4 but got " + result.size());

    // Search for all 1000 channels
    searchParameters.clear();
    searchParameters.add("~name", "SR*");
    scrollResult = channelScroll.query(searchParameters);
    result = new ArrayList<>(scrollResult.getChannels());
    while (scrollResult.getChannels().size() == 100) {
      scrollResult = channelScroll.query(scrollResult.getId(), searchParameters);
      result.addAll(scrollResult.getChannels());
    }
    Assertions.assertEquals(1000, result.size(), "Expected 1000 but got " + result.size());

    // Search for all 1000 SR channels and all 500 booster channels
    searchParameters.clear();
    searchParameters.add("~name", "SR*|BR*");
    scrollResult = channelScroll.query(searchParameters);
    result = new ArrayList<>(scrollResult.getChannels());
    while (scrollResult.getChannels().size() == 100) {
      scrollResult = channelScroll.query(scrollResult.getId(), searchParameters);
      result.addAll(scrollResult.getChannels());
    }
    Assertions.assertEquals(1500, result.size(), "Expected 1500 but got " + result.size());

    searchParameters.clear();
    searchParameters.add("~name", "SR*,BR*");
    scrollResult = channelScroll.query(searchParameters);
    result = new ArrayList<>(scrollResult.getChannels());
    while (scrollResult.getChannels().size() == 100) {
      scrollResult = channelScroll.query(scrollResult.getId(), searchParameters);
      result.addAll(scrollResult.getChannels());
    }
    Assertions.assertEquals(1500, result.size(), "Expected 1500 but got " + result.size());

    // search for channels based on a tag
    for (int i = 0; i < 5; i++) {

      long id = new Random().nextInt(10);
      int index = new Random().nextInt(9);
      searchParameters.clear();
      searchParameters.add("~name", "SR*");
      searchParameters.add("~tag", "group" + id + "_" + val_bucket.get(index));

      scrollResult = channelScroll.query(searchParameters);
      result = new ArrayList<>(scrollResult.getChannels());
      while (scrollResult.getChannels().size() == 100) {
        scrollResult = channelScroll.query(scrollResult.getId(), searchParameters);
        result.addAll(scrollResult.getChannels());
      }
      Assertions.assertEquals(
          val_bucket.get(index),
          Integer.valueOf(result.size()),
          "Search: "
              + maptoString(searchParameters)
              + " Failed Expected "
              + val_bucket.get(index)
              + " but got "
              + result.size());
    }

    // search for channels based on a tag
    for (int i = 0; i < 5; i++) {

      long id = new Random().nextInt(10);
      int index = new Random().nextInt(9);
      searchParameters.clear();
      searchParameters.add("~name", "SR*");
      searchParameters.add("group" + id, String.valueOf(val_bucket.get(index)));

      scrollResult = channelScroll.query(searchParameters);
      result = new ArrayList<>(scrollResult.getChannels());
      while (scrollResult.getChannels().size() == 100) {
        scrollResult = channelScroll.query(scrollResult.getId(), searchParameters);
        result.addAll(scrollResult.getChannels());
      }
      Assertions.assertEquals(
          val_bucket.get(index),
          Integer.valueOf(result.size()),
          "Search: "
              + maptoString(searchParameters)
              + " Failed Expected "
              + val_bucket.get(index)
              + " but got "
              + result.size());
    }
  }

  private String maptoString(MultiValueMap<String, String> searchParameters) {
    StringBuffer sb = new StringBuffer();
    searchParameters.forEach((key, value) -> sb.append(key).append(" ").append(value));
    return sb.toString();
  }
}
