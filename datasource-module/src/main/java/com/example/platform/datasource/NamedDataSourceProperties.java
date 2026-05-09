package com.example.platform.datasource;

public class NamedDataSourceProperties {
    private String kind = "jdbc";
    private String dialect = "postgres";
    private boolean primary;
    private String url;
    private String username;
    private String password;

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getDialect() { return dialect; }
    public void setDialect(String dialect) { this.dialect = dialect; }
    public boolean isPrimary() { return primary; }
    public void setPrimary(boolean primary) { this.primary = primary; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
