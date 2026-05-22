package org.phoebus.channelfinder.service.model.processor;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import org.phoebus.channelfinder.service.model.processor.aa.AAProcessorInfo;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({@JsonSubTypes.Type(value = AAProcessorInfo.class, name = "AAChannelProcessor")})
public interface ProcessorInfo {
  String name();

  boolean enabled();
}
