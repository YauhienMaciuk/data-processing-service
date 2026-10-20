package com.billogram.dataprocessing.persistence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Conditions of a SQL {@code WHERE} clause, all of which must hold, with the named parameters they reference. */
final class SqlConditions {

    private final List<String> conditions = new ArrayList<>();
    private final Map<String, Object> params = new HashMap<>();

    SqlConditions and(String condition, Map<String, ?> conditionParams) {
        conditions.add(condition);
        params.putAll(conditionParams);
        return this;
    }

    /** The {@code WHERE} clause, or an empty string when there are no conditions. */
    String where() {
        return conditions.isEmpty() ? "" : "WHERE " + String.join(" AND ", conditions);
    }

    Map<String, Object> params() {
        return Collections.unmodifiableMap(params);
    }
}
