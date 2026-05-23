package com.example.platform.identity.infrastructure;

import java.time.OffsetDateTime;
import org.jooq.Record;

/** H2 integration tests use uppercase labels; unit tests use lowercase. */
public final class JooqRecords {

    private JooqRecords() {
    }

    public static String string(Record record, String column) {
        if (record.field(column) != null) {
            return record.get(column, String.class);
        }
        return record.get(column.toUpperCase(), String.class);
    }

    public static OffsetDateTime offsetDateTime(Record record, String column) {
        if (record.field(column) != null) {
            return record.get(column, OffsetDateTime.class);
        }
        String upper = column.toUpperCase();
        if (record.field(upper) != null) {
            return record.get(upper, OffsetDateTime.class);
        }
        return null;
    }
}
