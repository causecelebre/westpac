-- Creates the two roles and the schema. The postgres image runs this once, on an empty volume.
-- The migration grants westpac_app its privileges on the tables.
\set ON_ERROR_STOP on

\getenv owner_password DB_OWNER_PASSWORD
\getenv app_password DB_APP_PASSWORD

CREATE ROLE westpac_owner LOGIN PASSWORD :'owner_password';
CREATE ROLE westpac_app LOGIN PASSWORD :'app_password';

CREATE SCHEMA westpac AUTHORIZATION westpac_owner;

ALTER ROLE westpac_owner SET search_path = westpac;
ALTER ROLE westpac_app SET search_path = westpac;
