package com.billogram.dataprocessing.persistence;

import com.billogram.dataprocessing.domain.RecordSummary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Running summaries of the records processed so far: one per destination id and reference, with records without a
 * reference sharing one per destination. A summary is read and updated in the same transaction as the record it adds.
 */
@Repository
public class RecordSummaryRepository {

    private static final TypeReference<Map<String, BigDecimal>> TOTALS = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;

    public RecordSummaryRepository(JdbcClient jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Returns the summary of the destination and reference, empty if there is none yet, and locks it until the
     * transaction ends: a concurrent transaction locking the same summary waits until this one commits, then gets
     * the updated summary. Must be called within a transaction, followed by {@link #update}.
     */
    public RecordSummary lock(String destinationId, String reference) {
        Map<String, Object> params = keyParams(destinationId, reference);
        jdbc.sql("""
                        INSERT INTO record_summaries (destination_id, reference, record_count, totals)
                        VALUES (:destinationId, :reference, 0, '{}')
                        ON CONFLICT DO NOTHING""")
                .params(params)
                .update();
        return jdbc.sql("""
                        SELECT record_count, totals, first_time, last_time
                        FROM record_summaries
                        WHERE destination_id = :destinationId AND %s
                        FOR UPDATE""".formatted(referenceCondition(reference)))
                .params(params)
                .query(this::toSummary)
                .single();
    }

    /** Replaces the summary of the destination and reference, which this transaction must have {@link #lock locked}. */
    public void update(String destinationId, String reference, RecordSummary summary) {
        Map<String, Object> params = keyParams(destinationId, reference);
        params.put("recordCount", summary.count());
        params.put("totals", jsonMapper.writeValueAsString(summary.totals()));
        params.put("firstTime", summary.firstTime());
        params.put("lastTime", summary.lastTime());
        jdbc.sql("""
                        UPDATE record_summaries
                        SET record_count = :recordCount, totals = CAST(:totals AS jsonb),
                            first_time = :firstTime, last_time = :lastTime
                        WHERE destination_id = :destinationId AND %s""".formatted(referenceCondition(reference)))
                .params(params)
                .update();
    }

    private static Map<String, Object> keyParams(String destinationId, String reference) {
        Map<String, Object> params = new HashMap<>();   // not Map.of: the reference may be null
        params.put("destinationId", destinationId);
        params.put("reference", reference);
        return params;
    }

    /** "= NULL" never matches and IS NOT DISTINCT FROM can't use the unique index, so the condition is picked per case. */
    private static String referenceCondition(String reference) {
        return reference == null ? "reference IS NULL" : "reference = :reference";
    }

    private RecordSummary toSummary(ResultSet row, int rowNum) throws SQLException {
        return new RecordSummary(
                row.getLong("record_count"),
                jsonMapper.readValue(row.getString("totals"), TOTALS),
                row.getObject("first_time", OffsetDateTime.class),
                row.getObject("last_time", OffsetDateTime.class));
    }
}
