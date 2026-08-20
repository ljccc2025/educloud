#!/usr/bin/env bash

set -euo pipefail

required_variables=(
  MYSQL_ROOT_PASSWORD
  EDUCLOUD_USER_DB_PASSWORD EDUCLOUD_USER_MIGRATION_PASSWORD
  EDUCLOUD_COURSE_DB_PASSWORD EDUCLOUD_COURSE_MIGRATION_PASSWORD
  EDUCLOUD_CONTENT_DB_PASSWORD EDUCLOUD_CONTENT_MIGRATION_PASSWORD
  EDUCLOUD_ORDER_DB_PASSWORD EDUCLOUD_ORDER_MIGRATION_PASSWORD
  EDUCLOUD_PAYMENT_DB_PASSWORD EDUCLOUD_PAYMENT_MIGRATION_PASSWORD
  EDUCLOUD_LIVE_DB_PASSWORD EDUCLOUD_LIVE_MIGRATION_PASSWORD
  EDUCLOUD_FILE_DB_PASSWORD EDUCLOUD_FILE_MIGRATION_PASSWORD
  EDUCLOUD_NOTIFICATION_DB_PASSWORD EDUCLOUD_NOTIFICATION_MIGRATION_PASSWORD
  EDUCLOUD_ANALYTICS_DB_PASSWORD EDUCLOUD_ANALYTICS_MIGRATION_PASSWORD
  EDUCLOUD_SEARCH_DB_PASSWORD EDUCLOUD_SEARCH_MIGRATION_PASSWORD
  EDUCLOUD_RECOMMENDATION_DB_PASSWORD EDUCLOUD_RECOMMENDATION_MIGRATION_PASSWORD
)

for variable_name in "${required_variables[@]}"; do
  variable_value="${!variable_name:-}"
  if [[ -z "$variable_value" ]]; then
    printf 'Required variable %s is empty\n' "$variable_name" >&2
    exit 1
  fi
  if [[ ! "$variable_value" =~ ^[A-Za-z0-9_.:@%+=-]{16,128}$ ]]; then
    printf 'Variable %s must be 16-128 characters from the approved local-secret character set\n' "$variable_name" >&2
    exit 1
  fi
done

# Each entry is database:application account:migration account:app secret:migration secret.
# Application accounts intentionally receive no database-wide grants here. Each module's
# migrations grant only the table privileges it needs, including INSERT/SELECT-only audit tables.
database_mappings=(
  'educloud_user:user_app:user_migration:EDUCLOUD_USER_DB_PASSWORD:EDUCLOUD_USER_MIGRATION_PASSWORD'
  'educloud_course:course_app:course_migration:EDUCLOUD_COURSE_DB_PASSWORD:EDUCLOUD_COURSE_MIGRATION_PASSWORD'
  'educloud_content:content_app:content_migration:EDUCLOUD_CONTENT_DB_PASSWORD:EDUCLOUD_CONTENT_MIGRATION_PASSWORD'
  'educloud_order:order_app:order_migration:EDUCLOUD_ORDER_DB_PASSWORD:EDUCLOUD_ORDER_MIGRATION_PASSWORD'
  'educloud_payment:payment_app:payment_migration:EDUCLOUD_PAYMENT_DB_PASSWORD:EDUCLOUD_PAYMENT_MIGRATION_PASSWORD'
  'educloud_live:live_app:live_migration:EDUCLOUD_LIVE_DB_PASSWORD:EDUCLOUD_LIVE_MIGRATION_PASSWORD'
  'educloud_file:file_app:file_migration:EDUCLOUD_FILE_DB_PASSWORD:EDUCLOUD_FILE_MIGRATION_PASSWORD'
  'educloud_notification:notification_app:notification_migration:EDUCLOUD_NOTIFICATION_DB_PASSWORD:EDUCLOUD_NOTIFICATION_MIGRATION_PASSWORD'
  'educloud_analytics:analytics_app:analytics_migration:EDUCLOUD_ANALYTICS_DB_PASSWORD:EDUCLOUD_ANALYTICS_MIGRATION_PASSWORD'
  'educloud_search:search_app:search_migration:EDUCLOUD_SEARCH_DB_PASSWORD:EDUCLOUD_SEARCH_MIGRATION_PASSWORD'
  'educloud_recommendation:recommendation_app:recommendation_migration:EDUCLOUD_RECOMMENDATION_DB_PASSWORD:EDUCLOUD_RECOMMENDATION_MIGRATION_PASSWORD'
)

for mapping in "${database_mappings[@]}"; do
  IFS=':' read -r database_name app_user migration_user app_password_name migration_password_name <<<"$mapping"
  app_password="${!app_password_name}"
  migration_password="${!migration_password_name}"

  MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket --user=root <<SQL
CREATE DATABASE IF NOT EXISTS \`${database_name}\`
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS '${app_user}'@'%' IDENTIFIED BY '${app_password}';
ALTER USER '${app_user}'@'%' IDENTIFIED BY '${app_password}';

CREATE USER IF NOT EXISTS '${migration_user}'@'%' IDENTIFIED BY '${migration_password}';
ALTER USER '${migration_user}'@'%' IDENTIFIED BY '${migration_password}';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES,
  CREATE VIEW, SHOW VIEW, TRIGGER
  ON \`${database_name}\`.* TO '${migration_user}'@'%';
SQL
done

MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket --user=root --execute='FLUSH PRIVILEGES;'
