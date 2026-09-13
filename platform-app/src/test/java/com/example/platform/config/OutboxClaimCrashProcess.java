package com.example.platform.config;

import com.example.platform.outbox.app.*;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import java.util.*;

/** Task-private child process. It exits only when the termination test kills this exact Process. */
public final class OutboxClaimCrashProcess {
    @EnableTransactionManagement(proxyTargetClass=true) static class Transactions {}
    public static void main(String[] args) throws Exception {
        if(Runtime.version().feature()!=25)throw new IllegalStateException("JDK25 required");
        var ds=new DriverManagerDataSource(System.getenv("OUTBOX_TEST_JDBC"),System.getenv("OUTBOX_TEST_USER"),System.getenv("OUTBOX_TEST_PASSWORD"));
        var context=new AnnotationConfigApplicationContext();context.register(Transactions.class);
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("claim-test",Map.of("app.outbox.claim-lease-ms",System.getenv("OUTBOX_TEST_LEASE"))));
        context.registerBean("transactionManager",DataSourceTransactionManager.class,()->new DataSourceTransactionManager(ds));
        context.registerBean(DSLContext.class,()->DSL.using(new TransactionAwareDataSourceProxy(ds),SQLDialect.POSTGRES));
        context.registerBean(OutboxEventService.class,()->new OutboxEventService(context.getBean(DSLContext.class),3,new PostgresNotificationService(null),new OutboxEventRouter(List.of())));
        context.refresh();var claim=context.getBean(OutboxEventService.class).claimForProcessing(args[0],"termination-probe").orElseThrow();
        System.out.println("CLAIMED "+claim.token());System.out.flush();
        new java.util.concurrent.CountDownLatch(1).await();
    }
}
