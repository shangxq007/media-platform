package com.example.platform.composition;

import com.example.platform.composition.app.*;
import com.example.platform.composition.app.CompositionRepository.Kind;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompositionRepositoryTest extends PostgresTestContainerSupport {
    static DataSource source;
    static JdbcTemplate db;
    static DataSourceTransactionManager manager;
    static CompositionAccess access;
    CompositionRepository repository;
    @BeforeAll static void setup() throws Exception {
        source=createDataSource(); db=new JdbcTemplate(source); manager=new DataSourceTransactionManager(source);
        db.execute(Files.readString(Path.of("../platform-app/src/main/resources/db/migration/V11__composition_foundation.sql")));
        db.execute(Files.readString(Path.of("../platform-app/src/main/resources/db/migration/V12__composition_versions.sql")));
        access=mock(CompositionAccess.class);
        when(access.require("t","w")).thenReturn(new CompositionAccess.Scope("t","w","u"));
        when(access.require("t","forged")).thenThrow(new CompositionService.ScopeViolationException());
    }
    @BeforeEach void repository(){repository=new CompositionRepository(db,manager,access);}
    @AfterAll static void close(){closeDataSource(source);}
    @Test void reconstructionRetainsDraftAndSnapshot() {
        String id=UUID.randomUUID().toString();
        repository.save(Kind.WORKFLOW,"t","w",id,"1.0",0,Map.of("name","saved"));
        var reconstructed=new CompositionRepository(db,manager,access);
        assertEquals("saved",reconstructed.draft(Kind.WORKFLOW,"t","w",id,Map.class).orElseThrow().get("name"));
        assertTrue(reconstructed.draft(Kind.WORKFLOW,"other","w",id,Map.class).isEmpty());
        assertThrows(CompositionService.ScopeViolationException.class,()->reconstructed.draft(Kind.WORKFLOW,"t","forged",id,Map.class));
    }
    @Test void staleFutureAndDuplicateRevisionsFail() {
        String id=UUID.randomUUID().toString(); repository.save(Kind.WORKFLOW,"t","w",id,"1.0",0,Map.of());
        assertThrows(CompositionService.OptimisticConcurrencyException.class,()->repository.save(Kind.WORKFLOW,"t","w",id,"1.0",0,Map.of()));
        assertThrows(CompositionService.OptimisticConcurrencyException.class,()->repository.save(Kind.WORKFLOW,"t","w",id,"1.0",10,Map.of()));
        repository.save(Kind.WORKFLOW,"t","w",id,"1.0",1,Map.of("updated",true));
        assertThrows(CompositionService.OptimisticConcurrencyException.class,()->repository.save(Kind.WORKFLOW,"t","w",id,"1.0",1,Map.of()));
    }
    @Test void publicationIsImmutableAndCannotBecomeDraft() {
        String id=UUID.randomUUID().toString(); repository.save(Kind.WORKFLOW,"t","w",id,"1.0",0,Map.of());
        repository.publish(Kind.WORKFLOW,"t","w",id,"1.0",1,Map.of("published",true));
        assertEquals(true,repository.version(Kind.WORKFLOW,"t","w",id,"1.0",Map.class).orElseThrow().get("published"));
        assertThrows(CompositionService.OptimisticConcurrencyException.class,()->repository.save(Kind.WORKFLOW,"t","w",id,"1.0",2,Map.of()));
        assertThrows(RuntimeException.class,()->db.update("UPDATE composition_version SET definition='{}'::jsonb WHERE composition_id=?",id));
    }
    @Test void outerRollbackLeavesNoDraft() {
        String id=UUID.randomUUID().toString();
        new TransactionTemplate(manager).executeWithoutResult(tx->{repository.save(Kind.APPLICATION,"t","w",id,"1.0",0,Map.of());tx.setRollbackOnly();});
        assertTrue(repository.draft(Kind.APPLICATION,"t","w",id,Map.class).isEmpty());
    }
    @Test void concurrentPublishHasExactlyOneWinner() throws Exception {
        String id=UUID.randomUUID().toString(); repository.save(Kind.WORKFLOW,"t","w",id,"1.0",0,Map.of());
        try(var executor=Executors.newFixedThreadPool(2)) {
            var barrier=new CyclicBarrier(2);
            Callable<Boolean> publish=()->{barrier.await(10,TimeUnit.SECONDS);try{repository.publish(Kind.WORKFLOW,"t","w",id,"1.0",1,Map.of());return true;}catch(CompositionService.OptimisticConcurrencyException e){return false;}};
            var a=executor.submit(publish);var b=executor.submit(publish);
            assertNotEquals(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
        }
    }
}
