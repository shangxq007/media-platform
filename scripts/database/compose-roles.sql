-- Applied only on a fresh local Compose cluster by the PostgreSQL image entry point.
\getenv migrator_password SPRING_FLYWAY_PASSWORD
\getenv api_password SPRING_DATASOURCE_PASSWORD
\getenv worker_password WORKER_DATASOURCE_PASSWORD
\getenv combined_password COMBINED_DATASOURCE_PASSWORD
CREATE ROLE mp_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD :'migrator_password';
CREATE ROLE mp_api LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD :'api_password';
CREATE ROLE mp_worker LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD :'worker_password';
CREATE ROLE mp_combined NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
SELECT length(:'combined_password') > 0 AS combined_enabled \gset
\if :combined_enabled
ALTER ROLE mp_combined LOGIN PASSWORD :'combined_password';
\endif
GRANT mp_api, mp_worker TO mp_combined WITH INHERIT TRUE, SET FALSE;
CREATE ROLE mp_readonly NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
ALTER DATABASE :"DBNAME" OWNER TO mp_migrator;
REVOKE ALL ON DATABASE :"DBNAME" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"DBNAME" TO mp_api, mp_worker, mp_combined, mp_readonly;
ALTER ROLE mp_migrator IN DATABASE :"DBNAME" SET search_path = pg_catalog;
ALTER ROLE mp_api IN DATABASE :"DBNAME" SET search_path = pg_catalog;
ALTER ROLE mp_worker IN DATABASE :"DBNAME" SET search_path = pg_catalog;
ALTER ROLE mp_combined IN DATABASE :"DBNAME" SET search_path = pg_catalog;
ALTER ROLE mp_readonly IN DATABASE :"DBNAME" SET search_path = pg_catalog;
