package com.example.platform.shared.test;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import javax.sql.DataSource;

/** Only the Supervisor handoff creates the connection authority. No URL or env constructor exists. */
public final class TestDatabaseConnectionProvider {
    private final RunConnectionSpec spec;
    private final String password;
    private final String url;
    private static TestDatabaseConnectionProvider instance;
    private TestDatabaseConnectionProvider(ControlledLaunch.Context context) {
        spec=context.spec; password=context.credential;
        url="jdbc:postgresql://"+spec.endpoint+":"+spec.port+"/"+spec.database
                +"?sslmode=verify-full&sslrootcert="+URLEncoder.encode(spec.caFile,StandardCharsets.UTF_8);
    }
    public static synchronized TestDatabaseConnectionProvider fromControlledLaunch() {
        if (instance==null) instance=new TestDatabaseConnectionProvider(ControlledLaunch.current());
        return instance;
    }
    public String jdbcUrl() { return url; }
    public String username() { return spec.role; }
    public String secret() { return password; }
    public String branchId() { return spec.branchId; }
    /** Namespace-bound local resource, not a host localhost fallback. */
    public boolean isIsolatedLocalResource() { return spec.projectId.startsWith("local_") && spec.branchId.equals("ct_"+spec.projectId.substring(6)); }
    public String runId() { return spec.runId; }
    public String driverClassName() { return "org.postgresql.Driver"; }
    public Properties connectionProperties() {
        Properties p=new Properties();p.setProperty("user",spec.role);p.setProperty("password",password);
        p.setProperty("sslmode","verify-full");p.setProperty("sslrootcert",spec.caFile);return p;
    }
    public String scopedJdbcUrl(String schema) {
        if (schema==null || !schema.matches("[a-z_][a-z0-9_]{0,62}")) throw new IllegalArgumentException("INVALID_SCHEMA");
        return url+"&currentSchema="+schema;
    }
    public Connection openConnection() throws SQLException { return DriverManager.getConnection(url,connectionProperties()); }
    public Connection openConnectionForSchema(String schema) throws SQLException {
        return DriverManager.getConnection(scopedJdbcUrl(schema),connectionProperties());
    }
    public void validateConnection() throws SQLException {
        try (Connection c=openConnection(); var st=c.createStatement(); ResultSet rs=st.executeQuery(
                "select current_setting('server_version_num')::int, current_database(), current_user, "
                +"pg_is_in_recovery(), (select ssl from pg_stat_ssl where pid=pg_backend_pid())")) {
            if (!rs.next()) throw new SQLException("IDENTITY_NO_ROW");
            if (rs.getInt(1)/10000!=18 || !spec.database.equals(rs.getString(2)) || !spec.role.equals(rs.getString(3)))
                throw new SQLException("IDENTITY_MAJOR_DB_ROLE");
            if (rs.getBoolean(4) || !rs.getBoolean(5)) throw new SQLException("IDENTITY_PRIMARY_TLS");
        }
    }
    public DataSource dataSource(int maximum,int idle) {
        return pool(url,maximum,idle);
    }
    public DataSource dataSourceForSchema(String schema,int maximum,int idle) {
        return pool(scopedJdbcUrl(schema),maximum,idle);
    }
    private DataSource pool(String boundUrl,int maximum,int idle) {
        if (maximum<1 || maximum>6 || idle<0 || idle>maximum) throw new IllegalArgumentException("POOL_BOUNDS");
        HikariConfig c=new HikariConfig();c.setJdbcUrl(boundUrl);c.setUsername(spec.role);c.setPassword(password);
        c.setDriverClassName(driverClassName());c.setMaximumPoolSize(maximum);c.setMinimumIdle(idle);
        c.setInitializationFailTimeout(-1);
        c.addDataSourceProperty("sslmode","verify-full");c.addDataSourceProperty("sslrootcert",spec.caFile);
        return new HikariDataSource(c);
    }
}
