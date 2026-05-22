package org.phoebus.channelfinder.service.model.processor.aa;

import java.util.Set;
import org.phoebus.channelfinder.service.model.processor.ProcessorInfo;

public record AAProcessorInfo(
    String name,
    boolean enabled,
    String archiveProperty,
    String archiverProperty,
    Set<String> archivers,
    long policyRefreshIntervalSeconds,
    AAConfig config,
    AAStatus status)
    implements ProcessorInfo {}
