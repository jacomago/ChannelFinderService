package org.phoebus.channelfinder.service.model.processor.aa;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.time.Instant;
import java.util.Map;

public record AAStatus(
    @JsonInclude(Include.NON_NULL) Instant lastPolicyRefresh,
    Map<String, Integer> cachedPoliciesPerArchiver) {}
