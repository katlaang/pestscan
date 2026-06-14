-- init.sql: create the pestscan_scouting database on first initialization
-- This file is executed by the official postgres Docker image only when the data
-- directory is empty (first time the container runs with this volume).

CREATE DATABASE pestscan_scouting;

