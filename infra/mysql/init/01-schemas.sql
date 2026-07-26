-- Phase 0.1 — Create per-service schemas so each service can apply Flyway
-- migrations against its own schema in later phases. This is the only place
-- where we do cross-schema work; subsequent migrations must be schema-scoped.
-- Per ADR-101 / Requirement 1.2.

CREATE DATABASE IF NOT EXISTS `identity`         CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `tree_content`     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `binary_storage`   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `sharing`          CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `transfer`         CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `reporting`        CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `audit`            CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Per-service MySQL users. Names and passwords match the defaults declared
-- in each service's src/main/resources/application.yml (the `:${ENV:default}`
-- fallback). When the operator overrides via `.env`, the envsubst entrypoint
-- substitutes these placeholders before mysql executes the file.
CREATE USER IF NOT EXISTS 'identitypw'@'%' IDENTIFIED BY '__MYSQL_SVC_IDENTITY__';
CREATE USER IF NOT EXISTS 'trepw'@'%'      IDENTIFIED BY '__MYSQL_SVC_TREE__';
CREATE USER IF NOT EXISTS 'mempw'@'%'      IDENTIFIED BY '__MYSQL_SVC_MEMBERS__';
CREATE USER IF NOT EXISTS 'relpw'@'%'      IDENTIFIED BY '__MYSQL_SVC_RELATIONS__';
CREATE USER IF NOT EXISTS 'evpw'@'%'       IDENTIFIED BY '__MYSQL_SVC_EVENTS__';
CREATE USER IF NOT EXISTS 'medpw'@'%'      IDENTIFIED BY '__MYSQL_SVC_MEDIA__';
CREATE USER IF NOT EXISTS 'binarypw'@'%'   IDENTIFIED BY '__MYSQL_SVC_BINARY__';
CREATE USER IF NOT EXISTS 'shapw'@'%'      IDENTIFIED BY '__MYSQL_SVC_SHARING__';
CREATE USER IF NOT EXISTS 'trapw'@'%'      IDENTIFIED BY '__MYSQL_SVC_TRANSFER__';
CREATE USER IF NOT EXISTS 'reppw'@'%'      IDENTIFIED BY '__MYSQL_SVC_REPORTING__';
CREATE USER IF NOT EXISTS 'audpw'@'%'      IDENTIFIED BY '__MYSQL_SVC_AUDIT__';

GRANT ALL PRIVILEGES ON `identity`.*       TO 'identitypw'@'%';
GRANT ALL PRIVILEGES ON `tree_content`.*   TO 'trepw'@'%';
GRANT ALL PRIVILEGES ON `tree_content`.*   TO 'mempw'@'%';
GRANT ALL PRIVILEGES ON `tree_content`.*   TO 'relpw'@'%';
GRANT ALL PRIVILEGES ON `tree_content`.*   TO 'evpw'@'%';
GRANT ALL PRIVILEGES ON `tree_content`.*   TO 'medpw'@'%';
GRANT ALL PRIVILEGES ON `binary_storage`.* TO 'binarypw'@'%';
GRANT ALL PRIVILEGES ON `sharing`.*        TO 'shapw'@'%';
GRANT ALL PRIVILEGES ON `transfer`.*       TO 'trapw'@'%';
GRANT ALL PRIVILEGES ON `reporting`.*      TO 'reppw'@'%';
GRANT ALL PRIVILEGES ON `audit`.*          TO 'audpw'@'%';

-- Read-only grants for cross-service projections (per service-common
-- clients: MemberClient, TreeClient, RelationshipClient, EventClient).
-- Per Requirement 1.2 + ADR-101 cross-service constraint.
GRANT SELECT ON `tree_content`.*           TO 'shapw'@'%';
GRANT SELECT ON `tree_content`.*           TO 'reppw'@'%';
GRANT SELECT ON `tree_content`.*           TO 'audpw'@'%';

FLUSH PRIVILEGES;
