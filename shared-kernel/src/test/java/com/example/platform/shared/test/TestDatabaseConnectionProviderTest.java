package com.example.platform.shared.test;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.jooq.SQLDialect;
import org.jooq.conf.MappedSchema;
import org.jooq.conf.RenderMapping;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;

/** No sockets: capture DriverManager calls and inspect the actual lazy Hikari factory configuration. */
class TestDatabaseConnectionProviderTest {
    static final List<String> trace=new java.util.concurrent.CopyOnWriteArrayList<>();
    static Object[] row;
    static int connectionCloses,statementCloses,resultCloses;
    static TestDatabaseConnectionProvider p;
    static boolean interrupt;
    static boolean sqlFailure;
    @BeforeAll static void register() throws SQLException {
        // Force service discovery, then remove network drivers from this isolated JVM.
        for (Driver driver:Collections.list(DriverManager.getDrivers())) DriverManager.deregisterDriver(driver);
        DriverManager.registerDriver(new Driver() {
            public boolean acceptsURL(String u) { return u.startsWith("jdbc:postgresql:"); }
            public Connection connect(String u,Properties properties) throws SQLException {
                if (!acceptsURL(u)) return null;
                trace.add("CONNECT "+u+" user="+properties.getProperty("user")+" ssl="+properties.getProperty("sslmode")+" ca="+properties.getProperty("sslrootcert"));
                if (interrupt) throw new SQLException("SYNTHETIC_INTERRUPTION");
                return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(obj,m,args)-> switch(m.getName()) {
                    case "createStatement" -> statement();
                    case "close" -> {connectionCloses++;trace.add("CLOSE_CONNECTION");yield null;}
                    case "isClosed" -> false;
                    case "isValid", "getAutoCommit" -> true;
                    case "getTransactionIsolation" -> Connection.TRANSACTION_READ_COMMITTED;
                    case "unwrap" -> obj;
                    default -> primitive(m.getReturnType());
                });
            }
            public DriverPropertyInfo[] getPropertyInfo(String u,Properties v) {return new DriverPropertyInfo[0];}
            public int getMajorVersion(){return 1;} public int getMinorVersion(){return 0;}
            public boolean jdbcCompliant(){return false;}
            public java.util.logging.Logger getParentLogger(){return java.util.logging.Logger.getGlobal();}
        });
        p=TestDatabaseConnectionProvider.fromControlledLaunch();
    }
    static Object primitive(Class<?> c) {
        if(c==boolean.class)return false;if(c==int.class)return 0;if(c==long.class)return 0L;return null;
    }
    static Statement statement() {
        return (Statement)Proxy.newProxyInstance(TestDatabaseConnectionProviderTest.class.getClassLoader(),new Class<?>[]{Statement.class},(obj,m,args)->switch(m.getName()) {
            case "executeQuery" -> {trace.add("QUERY "+args[0]);yield result();}
            case "execute" -> {trace.add("EXECUTE "+args[0]);if(sqlFailure)throw new SQLException("SYNTHETIC_SQL_FAILURE");yield false;}
            case "close" -> {statementCloses++;trace.add("CLOSE_STATEMENT");yield null;}
            default -> primitive(m.getReturnType());
        });
    }
    static ResultSet result() {
        boolean[] first={true};
        return (ResultSet)Proxy.newProxyInstance(TestDatabaseConnectionProviderTest.class.getClassLoader(),new Class<?>[]{ResultSet.class},(obj,m,args)->switch(m.getName()) {
            case "next" -> {boolean value=first[0];first[0]=false;yield value;}
            case "getInt", "getString", "getBoolean" -> row[(int)args[0]-1];
            case "close" -> {resultCloses++;trace.add("CLOSE_RESULT");yield null;}
            default -> primitive(m.getReturnType());
        });
    }
    @BeforeEach void reset() {
        trace.clear();connectionCloses=statementCloses=resultCloses=0;interrupt=sqlFailure=false;
        row=new Object[]{180000,ControlledLaunch.current().spec.database,p.username(),false,true};
    }
    @AfterEach void evidence(TestInfo info) {System.out.println("JAVA_CASE "+info.getTestMethod().orElseThrow().getName()+" TRACE "+trace);}
    @Test void localSpecCannotLeaveBoundNamespaceAndResource() throws Exception {
        var old=ControlledLaunch.current().spec;
        String cid="a".repeat(64);
        String namespace=java.nio.file.Files.readSymbolicLink(java.nio.file.Path.of("/proc/self/ns/net")).toString().replace("net:[","net_").replace("]","");
        String local=spec().replace("version=2","version=3")
            .replace("project_id="+old.projectId,"project_id=local_"+cid)
            .replace("parent_branch_id="+old.parentBranchId,"parent_branch_id=local_fresh")
            .replace("branch_id="+old.branchId+"\n","branch_id=ct_"+cid+"\n")
            .replace("endpoint_id="+old.endpointId,"endpoint_id="+namespace)
            .replace("endpoint="+old.endpoint,"endpoint=127.0.0.1")
            .replace("database="+old.database,"database=mp_f8_"+old.runId+"_"+old.attemptId)
            .replace("role="+old.role,"role=mp_test_"+old.runId+"_"+old.attemptId);
        assertEquals("127.0.0.1",RunConnectionSpec.parse(local,job()).endpoint);
        for(String tampered:List.of(local.replace(namespace,"net_0"),local.replace("ct_"+cid,"ct_"+"b".repeat(64)),
            local.replace("endpoint=127.0.0.1","endpoint=other.test"),local.replace("port=5432","port=6543"),
            local.replace("database=mp_f8_","database=other_"),local.replace("role=mp_test_","role=other_")))
            assertThrows(IllegalArgumentException.class,()->RunConnectionSpec.parse(tampered,job()));
        assertTrue(trace.isEmpty(),"parser rejections precede all JDBC calls");
    }
    @Test void classSchemasUseBoundDatabaseAndCloseOnErrors() throws Exception {
        try(var one=TestDatabaseSchema.open(p);var two=TestDatabaseSchema.open(p)) {
            assertNotEquals(one.name(),two.name());
            assertTrue(one.name().matches("ts_[a-f0-9]{32}"));
            assertTrue(two.name().matches("ts_[a-f0-9]{32}"));
            assertEquals("\""+one.name()+"\".\"media_asset\"",one.dsl().render(DSL.table(DSL.name("public","media_asset"))));
            assertEquals("\""+two.name()+"\".\"media_asset\"",two.dsl().render(DSL.table(DSL.name("public","media_asset"))));
            one.executeScript("select 1");
            assertThrows(IllegalArgumentException.class,()->one.executeScript(""));
            sqlFailure=true;
            assertThrows(SQLException.class,()->two.executeScript("select 2"));
            assertEquals(6,trace.stream().filter(s->s.startsWith("CONNECT ")).count());
            assertTrue(trace.stream().filter(s->s.startsWith("CONNECT ")).allMatch(s->s.contains(p.username())&&s.contains("ssl=verify-full")));
            assertTrue(trace.stream().anyMatch(s->s.contains("currentSchema="+one.name())));
            assertTrue(trace.stream().anyMatch(s->s.contains("currentSchema="+two.name())));
        }
        assertEquals(6,connectionCloses);
        assertEquals(6,statementCloses);
        assertEquals(2,resultCloses);
    }
    static String spec() {
        var s=ControlledLaunch.current().spec;
        return "version=2\nrun_id="+s.runId+"\nattempt_id="+s.attemptId+"\nproject_id="+s.projectId
            +"\nparent_branch_id="+s.parentBranchId+"\nbranch_id="+s.branchId+"\nendpoint_id="+s.endpointId
            +"\nendpoint="+s.endpoint+"\nport=5432\ndatabase="+s.database+"\nrole="+s.role
            +"\nexpected_major=18\nlaunch_id="+s.launchId+"\nca_file="+s.caFile+"\nEND\n";
    }
    static ControlledLaunch.JobContext job(String run,String attempt,String launch) {
        return new ControlledLaunch.JobContext(run,attempt,launch,"0".repeat(64));
    }
    static ControlledLaunch.JobContext job() {var s=ControlledLaunch.current().spec;return job(s.runId,s.attemptId,s.launchId);}
    static void rejected(String text,String expected) {
        var e=assertThrows(IllegalArgumentException.class,()->RunConnectionSpec.parse(text,job()));
        assertEquals(expected,e.getMessage());assertTrue(trace.isEmpty(),"no connection before rejection");
    }
    @Test void controlledContextSharedWithoutReread() {assertSame(p,TestDatabaseConnectionProvider.fromControlledLaunch());assertSame(p,TestDatabaseConnectionProvider.fromControlledLaunch());assertFalse(java.io.FileDescriptor.in.valid());assertTrue(java.io.FileDescriptor.err.valid());assertTrue(ControlledLaunch.channelsClosed());}
    @Test void missingSpecRejected() {rejected(null,"SPEC_TRUNCATED");}
    @Test void truncatedSpecRejected() {rejected(spec().substring(0,spec().length()-2),"SPEC_TRUNCATED");}
    @Test void duplicateFieldRejected() {rejected(spec().replace("\nEND\n","\nport=5432\nEND\n"),"SPEC_DUPLICATE_FIELD");}
    @Test void extraFieldRejected() {rejected(spec().replace("\nEND\n","\ntrusted=true\nEND\n"),"SPEC_UNKNOWN_FIELD");}
    @Test void wrongVersionRejected() {rejected(spec().replace("version=2","version=1"),"SPEC_VERSION");}
    @Test void wrongRunRejectedAtBinding() {var s=ControlledLaunch.current().spec;var e=assertThrows(IllegalArgumentException.class,()->RunConnectionSpec.parse(spec(),job("different",s.attemptId,s.launchId)));assertEquals("JOB_RUN_ATTEMPT_MISMATCH",e.getMessage());assertTrue(trace.isEmpty());}
    @Test void wrongAttemptRejectedAtBinding() {var s=ControlledLaunch.current().spec;var e=assertThrows(IllegalArgumentException.class,()->RunConnectionSpec.parse(spec(),job(s.runId,"different",s.launchId)));assertEquals("JOB_RUN_ATTEMPT_MISMATCH",e.getMessage());assertTrue(trace.isEmpty());}
    @Test void oldLaunchRejectedAtBinding() {var s=ControlledLaunch.current().spec;var e=assertThrows(IllegalArgumentException.class,()->RunConnectionSpec.parse(spec(),job(s.runId,s.attemptId,"0".repeat(32))));assertEquals("JOB_LAUNCH_MISMATCH",e.getMessage());assertTrue(trace.isEmpty());}
    @Test void loopbackAndUrlInjectionRejected() {var s=ControlledLaunch.current().spec;for(String bad:List.of("127.0.0.1","localhost","other.test:6543/other?sslmode=disable","other.test?SSLmode=disable"))rejected(spec().replace(s.endpoint,bad),"SPEC_ENDPOINT");}
    @Test void schemaCannotOverrideAnyConnectionField() throws Exception {
        for(String bad:List.of("jdbc:postgresql://other.test:6543/other?sslmode=disable","a&SSLmode=disable","a?sslmode=verify-full&sslmode=disable","a/other","a;user=other","a&sslrootcert=/other"))assertThrows(IllegalArgumentException.class,()->p.openConnectionForSchema(bad));
        assertTrue(trace.isEmpty());
        try(Connection c=p.openConnectionForSchema("schema_one")) {assertNotNull(c);}
        assertEquals(1,trace.stream().filter(x->x.startsWith("CONNECT")).count());assertTrue(trace.getFirst().contains(p.jdbcUrl()+"&currentSchema=schema_one"));
    }
    @Test void allJdbcAndHikariFactoriesUseOneTlsTuple() throws Exception {
        try(Connection c=p.openConnection()){assertNotNull(c);}
        try(Connection c=p.openConnectionForSchema("schema_two")){assertNotNull(c);}
        try(HikariDataSource ds=(HikariDataSource)p.dataSource(1,0)) {
            assertEquals(p.jdbcUrl(),ds.getJdbcUrl());assertEquals(p.username(),ds.getUsername());
            assertEquals("verify-full",ds.getDataSourceProperties().getProperty("sslmode"));
            assertEquals(ControlledLaunch.current().spec.caFile,ds.getDataSourceProperties().getProperty("sslrootcert"));
            trace.add("HIKARI_FACTORY "+ds.getJdbcUrl()+" user="+ds.getUsername()+" ssl="+ds.getDataSourceProperties().getProperty("sslmode")+" ca="+ds.getDataSourceProperties().getProperty("sslrootcert"));
        }
        var inputs=trace.stream().filter(x->x.startsWith("CONNECT")).toList();assertEquals(2,inputs.size());
        for(String input:inputs){assertTrue(input.contains("sslmode=verify-full"));assertTrue(input.contains("user="+p.username()));assertFalse(input.contains("password"));}
    }
    @Test void postgresIdentityRejectsEveryMismatchAndClosesAllHandles() throws Exception {
        var s=ControlledLaunch.current().spec;
        Object[][] bad={{170000,s.database,s.role,false,true},{180000,"other",s.role,false,true},{180000,s.database,"other",false,true},{180000,s.database,s.role,true,true},{180000,s.database,s.role,false,false}};
        for(int i=0;i<bad.length;i++) {
            row=bad[i];var e=assertThrows(SQLException.class,p::validateConnection);
            assertEquals(i<3?"IDENTITY_MAJOR_DB_ROLE":"IDENTITY_PRIMARY_TLS",e.getMessage());
            assertEquals(i+1,connectionCloses);assertEquals(i+1,statementCloses);assertEquals(i+1,resultCloses);
        }
        assertEquals(5,trace.stream().filter(x->x.startsWith("QUERY select current_setting('server_version_num')::int, current_database(), current_user, pg_is_in_recovery(), (select ssl from pg_stat_ssl where pid=pg_backend_pid())")).count());
    }
    @Test void postgresIdentitySuccessStillClosesAllHandles() throws Exception {p.validateConnection();assertEquals(1,connectionCloses);assertEquals(1,statementCloses);assertEquals(1,resultCloses);}
    @Test void interruptionNeverFallsBack() {interrupt=true;var e=assertThrows(SQLException.class,p::openConnection);assertEquals("SYNTHETIC_INTERRUPTION",e.getMessage());assertEquals(1,trace.size());assertTrue(trace.getFirst().startsWith("CONNECT "+p.jdbcUrl()));}
    @Test void classSchemaRegistrationAndJooqRenderingStayInOwnedDatabase() throws Exception {
        // Initializing the actual support class performs its identity preflight using this fake Driver.
        for(String schema:List.of("class_one","class_two")) {
            Map<String,java.util.function.Supplier<Object>> properties=new HashMap<>();
            DynamicPropertyRegistry registry=(name,supplier)->properties.put(name,supplier);
            PostgresTestContainerSupport.registerIsolatedSchemaProperties(registry,schema);
            String expected=p.scopedJdbcUrl(schema);
            try(Connection c=PostgresTestContainerSupport.openConnection(schema)){assertNotNull(c);}
            assertTrue(trace.stream().anyMatch(x -> x.startsWith("CONNECT "+expected)));
            assertEquals(expected,properties.get("spring.datasource.url").get());assertEquals(expected,properties.get("spring.flyway.url").get());
            assertEquals(p.username(),properties.get("spring.flyway.user").get());assertEquals(schema,properties.get("spring.flyway.schemas").get());
            Settings settings=new Settings().withRenderMapping(new RenderMapping().withSchemata(new MappedSchema().withInput("public").withOutput(schema)));
            String sql=DSL.using(SQLDialect.POSTGRES,settings).render(DSL.table(DSL.name("public","media_asset")));
            assertEquals("\""+schema+"\".\"media_asset\"",sql);trace.add("RENDER "+sql+" JDBC_FLYWAY_URL "+expected);
        }
    }
}
