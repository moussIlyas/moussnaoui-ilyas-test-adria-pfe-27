#!/bin/bash
# Runs exactly once, on the first start of an EMPTY MariaDB volume
# (/docker-entrypoint-initdb.d). The mariadb image has already set the root
# password at this point; DB_USERNAME / DB_PASSWORD are injected by
# docker-compose.yml from .env - the same values the services use via
# ${DB_USERNAME} / ${DB_PASSWORD} placeholders in config-repo/*.properties.
set -euo pipefail

: "${MARIADB_ROOT_PASSWORD:?MARIADB_ROOT_PASSWORD must be set}"
DB_USERNAME="${DB_USERNAME:-ebank}"
DB_PASSWORD="${DB_PASSWORD:-ebank}"

# mariadb:11.x images only ship the `mariadb` client (the `mysql` alias is gone)
command -v mariadb >/dev/null 2>&1 || { echo "init-db.sh: 'mariadb' client not found" >&2; exit 1; }

mariadb -uroot -p"${MARIADB_ROOT_PASSWORD}" <<SQL
CREATE DATABASE IF NOT EXISTS \`transactions-db\`;
CREATE DATABASE IF NOT EXISTS \`accounts-db\`;

CREATE USER IF NOT EXISTS '${DB_USERNAME}'@'%' IDENTIFIED BY '${DB_PASSWORD}';
GRANT ALL PRIVILEGES ON \`transactions-db\`.* TO '${DB_USERNAME}'@'%';
GRANT ALL PRIVILEGES ON \`accounts-db\`.* TO '${DB_USERNAME}'@'%';
FLUSH PRIVILEGES;
SQL

echo "init-db.sh: databases transactions-db/accounts-db ready for user '${DB_USERNAME}'"
