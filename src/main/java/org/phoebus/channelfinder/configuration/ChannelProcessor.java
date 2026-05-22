package org.phoebus.channelfinder.configuration;

import java.util.List;
import org.phoebus.channelfinder.entity.Channel;
import org.phoebus.channelfinder.service.model.processor.ProcessorInfo;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

public interface ChannelProcessor {

  boolean enabled();

  void setEnabled(boolean enabled);

  ProcessorInfo processorInfo();

  long process(List<Channel> channels) throws JacksonException;

  /** Trigger an immediate refresh of any cached state (e.g., archiver policies). Default: no-op. */
  default void refresh() {}

  /**
   * Apply a partial config update. Fields present in {@code config} are applied; absent fields are
   * unchanged. Unrecognised keys are logged at WARNING and ignored. Default: no-op.
   */
  default void applyConfig(JsonNode config) {}
}
