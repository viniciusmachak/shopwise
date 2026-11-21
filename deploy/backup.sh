#!/bin/sh
# Daily pg_dump into /backups, keeping the last BACKUP_KEEP_DAYS days.
set -eu

KEEP_DAYS="${BACKUP_KEEP_DAYS:-7}"

# Give the backend time to create and migrate the schema before the first dump.
sleep "${BACKUP_INITIAL_DELAY_SECONDS:-600}"

while true; do
  file="/backups/shopwise-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"
  if pg_dump --no-owner --clean --if-exists | gzip > "$file.tmp"; then
    mv "$file.tmp" "$file"
    echo "Backup written: $file"
  else
    rm -f "$file.tmp"
    echo "Backup failed" >&2
  fi
  find /backups -name 'shopwise-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
  sleep "${BACKUP_INTERVAL_SECONDS:-86400}"
done
