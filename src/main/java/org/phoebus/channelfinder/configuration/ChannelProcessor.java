package org.phoebus.channelfinder.configuration;

import java.util.List;
import org.phoebus.channelfinder.entity.Channel;
import org.phoebus.channelfinder.service.model.archiver.ChannelProcessorInfo;
import tools.jackson.core.JacksonException;

public interface ChannelProcessor {

  boolean enabled();

  void setEnabled(boolean enabled);

  ChannelProcessorInfo processorInfo();

  long process(List<Channel> channels) throws JacksonException;

  /** Trigger an immediate refresh of any cached state (e.g., archiver policies). Default: no-op. */
  default void refresh() {}

  /**
   * Set a named runtime-configurable property. Unrecognized keys are silently ignored. An empty
   * value clears the option (treated as an empty list for list-valued properties).
   */
  default void setProperty(String key, String value) {}
}
