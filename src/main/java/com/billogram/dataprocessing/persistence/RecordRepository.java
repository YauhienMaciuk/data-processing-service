package com.billogram.dataprocessing.persistence;

import com.billogram.dataprocessing.domain.DataRecord;
import com.billogram.dataprocessing.domain.RecordFilter;
import com.billogram.dataprocessing.domain.RecordQuery;
import com.billogram.dataprocessing.domain.RecordType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Repository
public class RecordRepository {

    private static final RowMapper<DataRecord> RECORD_MAPPER = (rs, rowNum) -> new DataRecord(
            rs.getString("record_id"),
            rs.getObject("time", OffsetDateTime.class),
            rs.getString("source_id"),
            rs.getString("destination_id"),
            RecordType.fromValue(rs.getString("type")),
            rs.getBigDecimal("value"),
            rs.getString("unit"),
            rs.getString("reference"));

    private final JdbcClient jdbc;

    public RecordRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts the record unless one with the same {@code recordId} already exists.
     * The duplicate check and the insert are one atomic statement.
     *
     * @return {@code true} if inserted, {@code false} if the record was a duplicate
     */
    public boolean insertIfAbsent(DataRecord record) {
        return jdbc.sql("""
                        INSERT INTO records (record_id, time, source_id, destination_id, type, value, unit, reference)
                        VALUES (:recordId, :time, :sourceId, :destinationId, :type, :value, :unit, :reference)
                        ON CONFLICT (record_id) DO NOTHING""")
                .paramSource(params(record))
                .update() == 1;
    }

    /**
     * Finds the records matching the query's filter that come after its cursor, at most {@code query.limit()}.
     * They are ordered by the keyset (destination id, time, record id); record id is unique, so the keyset of a page's
     * last record pins down exactly where the next page starts.
     */
    public List<DataRecord> find(RecordQuery query) {
        SqlConditions conditions = matching(query.filter());
        if (query.after() != null) {
            // Row comparison is lexicographic, like the ORDER BY, and can use the index on the keyset columns
            conditions.and("(destination_id, time, record_id) > (:afterDestinationId, :afterTime, :afterRecordId)",
                    Map.of("afterDestinationId", query.after().destinationId(),
                            "afterTime", query.after().time(),
                            "afterRecordId", query.after().recordId()));
        }
        return jdbc.sql("""
                        SELECT record_id, time, source_id, destination_id, type, value, unit, reference
                        FROM records
                        %s
                        ORDER BY destination_id, time, record_id
                        LIMIT :limit""".formatted(conditions.where()))
                .params(conditions.params())
                .param("limit", query.limit())
                .query(RECORD_MAPPER)
                .list();
    }

    /**
     * Totals the values of the records matching the filter, for the given destinations only.
     *
     * @return signed total per unit (positive records add, negative subtract), by destination id;
     *         destinations without matching records are absent
     */
    public Map<String, Map<String, BigDecimal>> totalsByDestination(RecordFilter filter, Collection<String> destinationIds) {
        if (destinationIds.isEmpty()) {
            return Map.of();   // "IN ()" is not valid SQL
        }
        SqlConditions conditions = matching(filter)
                .and("destination_id IN (:destinationIds)", Map.of("destinationIds", destinationIds));

        Map<String, Map<String, BigDecimal>> totals = new HashMap<>();
        jdbc.sql("""
                        SELECT destination_id, unit, type, sum(value) AS total
                        FROM records
                        %s
                        GROUP BY destination_id, unit, type""".formatted(conditions.where()))
                .params(conditions.params())
                .query(row -> {
                    RecordType type = RecordType.fromValue(row.getString("type"));
                    totals.computeIfAbsent(row.getString("destination_id"), destinationId -> new TreeMap<>())
                            .merge(row.getString("unit"), type.signed(row.getBigDecimal("total")), BigDecimal::add);
                });
        return totals;
    }

    private static SqlConditions matching(RecordFilter filter) {
        SqlConditions conditions = new SqlConditions();
        if (filter.startTime() != null) {
            conditions.and("time >= :start", Map.of("start", filter.startTime()));
        }
        if (filter.endTime() != null) {
            conditions.and("time < :end", Map.of("end", filter.endTime()));
        }
        if (filter.type() != null) {
            conditions.and("type = :type", Map.of("type", filter.type().value()));
        }
        return conditions;
    }

    private static SqlParameterSource params(DataRecord record) {
        return new MapSqlParameterSource()
                .addValue("recordId", record.recordId())
                .addValue("time", record.time())
                .addValue("sourceId", record.sourceId())
                .addValue("destinationId", record.destinationId())
                .addValue("type", record.type().value())
                .addValue("value", record.value())
                .addValue("unit", record.unit())
                .addValue("reference", record.reference());
    }
}
