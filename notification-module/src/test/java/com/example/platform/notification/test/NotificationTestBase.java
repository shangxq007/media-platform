package com.example.platform.notification.test;

import com.example.platform.notification.testsupport.NotificationTestSchemaFixture;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

/**
 * Shared test base for notification module tests.
 * Creates all notification tables using production schema.
 */
public abstract class NotificationTestBase extends PostgresTestContainerSupport {

    protected static DataSource dataSource;
    protected static DSLContext dsl;

    @BeforeAll
    static void initNotificationDatabase() {
        dataSource = createDataSource();
        dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
        NotificationTestSchemaFixture.createSchema(dsl);
    }

    @AfterAll
    static void closeNotificationDataSource() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void cleanNotificationTables() {
        NotificationTestSchemaFixture.truncate(dsl);
    }
}
