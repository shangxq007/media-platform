package com.example.platform.testinfra;
import com.example.platform.DslContextConfiguration;
import com.example.platform.shared.test.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;

/** Uses the production jOOQ/Spring wiring against real PG18; inner owner commits cannot release caller locks. */
class FirstRenderTransactionPostgresTest {
 @ParameterizedTest @ValueSource(booleans={false,true}) void nestedJooqCommitIsRolledBackWithOuterAcceptance(boolean worker) throws Exception {
  var provider=TestDatabaseConnectionProvider.fromControlledLaunch();
  try(var schema=TestDatabaseSchema.open(provider)) {
   var source=new SingleConnectionDataSource(schema.connection(),true);var manager=new DataSourceTransactionManager(source);
   var db=worker ? new com.example.platform.datasource.DataSourceConfiguration().dslContext(source,manager)
       : new DslContextConfiguration().dslContext(source,manager);
   db.execute("create table transaction_probe(id int primary key)");
   assertThrows(IllegalStateException.class,()->new TransactionTemplate(manager).execute(tx->{
    db.execute("insert into transaction_probe values(1)");
    db.transaction(inner->org.jooq.impl.DSL.using(inner).execute("insert into transaction_probe values(2)"));
    throw new IllegalStateException("outer interrupted after nested owner commit");
   }));
   assertEquals(0,db.fetchCount(org.jooq.impl.DSL.table("transaction_probe")));
  }
 }
 @ParameterizedTest @ValueSource(booleans={false,true}) void nestedCommitKeepsJobLockUntilOuterTransactionEnds(boolean worker) throws Exception {
  var provider=TestDatabaseConnectionProvider.fromControlledLaunch();
  try(var schema=TestDatabaseSchema.open(provider);var contender=provider.openConnectionForSchema(schema.name())) {
   var source=new SingleConnectionDataSource(schema.connection(),true);var manager=new DataSourceTransactionManager(source);
   var db=worker ? new com.example.platform.datasource.DataSourceConfiguration().dslContext(source,manager)
       : new DslContextConfiguration().dslContext(source,manager);
   db.execute("create table lock_probe(id int primary key)");db.execute("insert into lock_probe values(1)");
   new TransactionTemplate(manager).execute(tx->{
    db.fetch("select * from lock_probe for update");
    db.transaction(inner->org.jooq.impl.DSL.using(inner).execute("select 1"));
    try(var stmt=contender.createStatement()) {
     stmt.execute("set lock_timeout='100ms'");
     var failure=assertThrows(java.sql.SQLException.class,()->stmt.execute("select * from lock_probe for update"));
     assertEquals("55P03",failure.getSQLState());
    } catch(java.sql.SQLException ex) {throw new IllegalStateException(ex);}
    return null;
   });
   try(var stmt=contender.createStatement()){assertTrue(stmt.execute("select * from lock_probe for update"));}
  }
 }
}
