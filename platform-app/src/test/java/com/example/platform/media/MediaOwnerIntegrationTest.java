package com.example.platform.media;

import com.example.platform.media.api.*;
import com.example.platform.media.app.*;
import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.probe.*;
import com.example.platform.media.infrastructure.persistence.*;
import com.example.platform.media.infrastructure.probe.FfprobeMediaProbeNormalizer;
import com.example.platform.identity.api.authorization.*;
import com.example.platform.shared.authorization.*;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.web.TenantContext;
import org.flywaydb.core.Flyway;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaOwnerIntegrationTest extends PostgresTestContainerSupport {
    static final String SCHEMA = isolatedSchemaName();
    static DataSource admin;
    static AnnotationConfigApplicationContext context;
    static JdbcTemplate jdbc;
    static boolean denied;
    static JooqMediaStreamRepository streams;
    @EnableTransactionManagement(proxyTargetClass=true) static class Transactions {}
    @BeforeAll static void setup() {
        admin=createDataSource(); new JdbcTemplate(admin).execute("create schema "+SCHEMA);
        Flyway.configure().dataSource(jdbcUrl(),username(),password()).locations("classpath:db/migration")
                .schemas(SCHEMA).defaultSchema(SCHEMA).load().migrate();
        var ds=new DriverManagerDataSource(jdbcUrl()+(jdbcUrl().contains("?")?"&":"?")+"currentSchema="+SCHEMA,username(),password());
        jdbc=new JdbcTemplate(ds);
        context=new AnnotationConfigApplicationContext(); context.register(Transactions.class);
        context.registerBean("transactionManager",DataSourceTransactionManager.class,()->new DataSourceTransactionManager(ds));
        context.registerBean(DSLContext.class,()->DSL.using(new TransactionAwareDataSourceProxy(ds),SQLDialect.POSTGRES,new org.jooq.conf.Settings().withRenderSchema(false)));
        context.registerBean(CanonicalActorResolver.class,()->()->Optional.of(CanonicalActor.user("actor","tenant",Set.of("EDITOR"),"fixture")));
        context.registerBean(AuthorizationDecisionPort.class,()->request->{
            assertEquals("tenant",request.resource().tenantId());
            if(denied || !"project".equals(request.resource().projectId())) throw new SecurityException("denied");
            return AuthorizationDecision.allow("fixture");
        });
        context.registerBean(MediaAuthorization.class); context.registerBean(JooqMediaAssetRepository.class);
        context.registerBean(MediaAssetService.class); context.registerBean(MediaProbeService.class);
        context.registerBean(JooqMediaStreamRepository.class,()->{streams=spy(new JooqMediaStreamRepository(context.getBean(DSLContext.class)));return streams;});
        context.registerBean(JooqMediaProbeObservationRepository.class);
        context.registerBean(FfprobeMediaProbeNormalizer.class);
        context.registerBean(MediaProbePort.class,()->uri->new MediaProbeObservation("ffprobe-fixture", """
                {"format":{"duration":"1.25"},"streams":[{"codec_type":"video","codec_name":"h264",
                "width":16,"height":16,"r_frame_rate":"30000/1001","avg_frame_rate":"30000/1001","time_base":"1/30000"}]}
                """,true,true,false,List.of(),null));
        context.refresh();
    }
    @BeforeEach void before(){TenantContext.set("tenant");denied=false;reset(streams);}
    @AfterEach void after(){TenantContext.clear();}
    @AfterAll static void close(){if(context!=null)context.close();if(admin!=null){new JdbcTemplate(admin).execute("drop schema "+SCHEMA+" cascade");closeDataSource(admin);}}
    static MediaAssets assets(){return context.getBean(MediaAssets.class);}
    static Asset register(){return assets().register("tenant","project","tenant/project/input.mp4","VIDEO","input.mp4",12L,"digest-projection");}
    @Test void registrationProbeAndScopedReadThroughOwner() {
        var asset=register(); var id=MediaAssetId.of(asset.id());
        var probe=context.getBean(MediaProbes.class).probeAndPersist(id,"tenant","project","fixture:input");
        assertEquals(id,probe.mediaAssetId());
        assertEquals("v1",assets().findById("tenant",asset.id()).orElseThrow().assetVersion());
        assertEquals(1,jdbc.queryForObject("select count(*) from media_stream where media_asset_id=?",Integer.class,asset.id()));
        assertEquals(30000L,streams.findByMediaAssetId(id).getFirst().nominalFrameRate().numerator().longValueExact());
        assertEquals(1001L,streams.findByMediaAssetId(id).getFirst().nominalFrameRate().denominator());
        assertTrue(context.getBean(MediaProbes.class).latestNormalized("tenant",id).isPresent());
        assertThrows(IllegalArgumentException.class,()->context.getBean(MediaProbes.class).probeAndPersist(id,"tenant","other","fixture:input"));
        assertThrows(RuntimeException.class,()->context.getBean(MediaProbes.class).latestNormalized("foreign",id));
    }
    @Test void deniedAndWrongTenantCannotRegisterOrMutate() {
        var asset=register();denied=true;
        assertThrows(SecurityException.class,MediaOwnerIntegrationTest::register);
        assertThrows(SecurityException.class,()->assets().updatePublishStatus("tenant","project",asset.id(),"DRAFT","PUBLISHED"));
        denied=false;TenantContext.set("foreign");assertThrows(RuntimeException.class,MediaOwnerIntegrationTest::register);
        assertEquals("DRAFT",jdbc.queryForObject("select publish_status from media_asset where id=?",String.class,asset.id()));
    }
    @Test void publicationCompareAndSetAndVersionedDeletionRejectStaleOrForeignScope() {
        var asset=register();
        assertThrows(SecurityException.class,()->assets().delete("tenant","other",asset.id(),"v1"));
        assertFalse(assets().delete("tenant","project",asset.id(),"v0"));
        assets().updatePublishStatus("tenant","project",asset.id(),"DRAFT","PUBLISHED");
        assertThrows(IllegalStateException.class,()->assets().updatePublishStatus("tenant","project",asset.id(),"DRAFT","ARCHIVED"));
        assertEquals("PUBLISHED",assets().findById("tenant",asset.id()).orElseThrow().publishStatus());
        assertTrue(assets().delete("tenant","project",asset.id(),"v1"));
    }
    @Test void ownerRegistrationJoinsCallerTransactionAndRollsBack() {
        long before=jdbc.queryForObject("select count(*) from media_asset",Long.class);
        var tx=new TransactionTemplate(context.getBean(DataSourceTransactionManager.class));
        assertThrows(IllegalStateException.class,()->tx.execute(status->{register();throw new IllegalStateException("caller failed");}));
        assertEquals(before,jdbc.queryForObject("select count(*) from media_asset",Long.class));
    }
    @Test void realProbeInsertThenFailureRollsBackObservationAndStreams() {
        var asset=register();var id=MediaAssetId.of(asset.id());var probes=context.getBean(MediaProbes.class);
        probes.probeAndPersist(id,"tenant","project","fixture:input");
        long before=jdbc.queryForObject("select count(*) from media_probe_observation where media_asset_id=?",Long.class,asset.id());
        doAnswer(call->{call.callRealMethod();assertEquals(1,streams.findByMediaAssetId(id).size());throw new IllegalStateException("after real stream insert");}).when(streams).saveAll(eq(id),anyList());
        assertThrows(IllegalStateException.class,()->probes.probeAndPersist(id,"tenant","project","fixture:input"));
        assertEquals(before,jdbc.queryForObject("select count(*) from media_probe_observation where media_asset_id=?",Long.class,asset.id()));
        assertEquals(1,streams.findByMediaAssetId(id).size());
    }
}
