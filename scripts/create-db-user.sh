#!/usr/bin/env bash
set -e

# Usage: ./scripts/create-db-user.sh <db_user> <db_pass> [pg_container]
# If no args provided, uses values from .env or defaults (postgres/admin)

# load .env if present
if [ -f .env ]; then
  set -o allexport
  source .env
  set +o allexport
fi

DB_USER=${1:-${LOCAL_DB_USER:-postgres}}
DB_PASS=${2:-${LOCAL_DB_PASS:-admin}}
PG_CONTAINER=${3:-pestscan-postgres}

echo "Creating database user '${DB_USER}' with given password on container ${PG_CONTAINER}..."

# Create user and grant privileges on pestscan_scouting
docker exec -i "$PG_CONTAINER" psql -U postgres -v ON_ERROR_STOP=1 <<-SQL
DO
$$
BEGIN
   IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '$DB_USER') THEN
      CREATE ROLE "$DB_USER" LOGIN PASSWORD '$DB_PASS';
   END IF;
END
$$;
GRANT ALL PRIVILEGES ON DATABASE pestscan_scouting TO "$DB_USER";
SQL

echo "Done."

