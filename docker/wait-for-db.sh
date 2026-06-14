#!/usr/bin/env sh
set -e

HOST=${DB_HOST:-postgres}
PORT=${DB_PORT:-5432}
USER=${SPRING_DATASOURCE_USERNAME:-postgres}

echo "Waiting for Postgres at ${HOST}:${PORT} as ${USER}..."
# loop until pg_isready returns success
while ! pg_isready -h "$HOST" -p "$PORT" -U "$USER" >/dev/null 2>&1; do
  echo "Postgres is unavailable - sleeping"
  sleep 2
done

echo "Postgres is up - launching app"
exec "$@"

