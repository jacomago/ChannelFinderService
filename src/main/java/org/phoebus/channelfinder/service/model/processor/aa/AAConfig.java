package org.phoebus.channelfinder.service.model.processor.aa;

import java.util.List;

public record AAConfig(
    List<String> autoPauseOn, List<String> defaultArchivers, List<String> postSupportArchivers) {}
