package com.billogram.dataprocessing.api;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Settings under the {@code records.*} prefix. */
@ConfigurationProperties("records")
public record RecordProperties(@DefaultValue Query query) {

    /**
     * @param defaultLimit records per page of {@code GET /records} when the request has no {@code limit}
     * @param maxLimit     largest {@code limit} a request may ask for
     */
    public record Query(@DefaultValue("100") int defaultLimit, @DefaultValue("1000") int maxLimit) {
    }
}
