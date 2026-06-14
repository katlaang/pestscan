#!/usr/bin/env bash
set -e

# Recreate Postgres volume and start compose for a clean local DB init
# WARNING: this removes the postgres-data volume and all DB contents

if [ "$1" != "--yes-i-understand" ]; then
  echo "This will remove the postgres-data volume and all database contents."
  echo "Run with: ./scripts/recreate-db.sh --yes-i-understand"
  exit 1
fi

echo "Stopping and removing compose services and volumes..."
docker compose down -v

echo "Bringing up postgres and redis..."
docker compose up -d postgres redis

echo "Waiting for postgres to initialize (check logs)..."
docker logs -f pestscan-postgres

echo "Now you can bring up the app: docker compose up -d app"

