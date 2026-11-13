# Deploying Shopwise

Production runs on one VM (planned: Oracle Cloud Always Free, ARM, 4 cores / 24 GB) with Docker Compose. Caddy serves the frontend and proxies the API on the same domain, with an HTTPS certificate from Let's Encrypt.

## Services

| Service | What it does |
|---|---|
| `web` | Caddy: static frontend, `/api` proxy, HTTPS |
| `backend` | Spring Boot app (`prod` profile, no demo data) |
| `postgres` | PostgreSQL 17 with pgvector |
| `ollama` | Embeddings and the local chat model; `ollama-models` pulls them once |
| `backup` | Daily `pg_dump` into the `backups` volume, keeping 7 days |
| `backup-offsite` | Optional (`--profile offsite`): copies backups to S3-compatible storage |

## First time on the server

1. Create the VM and open ports 80 and 443 (in the cloud firewall and, on Oracle's images, in `iptables` too).
2. Install Docker and the Compose plugin.
3. Point a domain at the VM's public IP (DuckDNS works for a free subdomain).
4. Copy the `deploy/` folder to the server, then:

   ```bash
   cp .env.prod.example .env.prod   # fill in the domain, a strong POSTGRES_PASSWORD and the keys you have
   docker compose -f docker-compose.prod.yml --env-file .env.prod up -d
   ```

5. Sign up in the store with an e-mail listed in `SHOPWISE_ADMIN_EMAILS` to get the backoffice.

The first start pulls the Ollama models (about 5 GB), so the assistant needs a few minutes before local mode answers.

## Updating

Images are tagged with the commit SHA. Set `SHOPWISE_TAG` in `.env.prod` and run:

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod pull backend web
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d
```

Flyway migrates the database when the backend starts.

## Backups and restore

Backups are in the `backups` volume (`docker compose ... exec backup ls /backups`). To restore one:

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod stop backend
docker compose -f docker-compose.prod.yml --env-file .env.prod exec -T backup \
  sh -c 'gunzip -c /backups/shopwise-YYYYMMDDTHHMMSSZ.sql.gz | psql'
docker compose -f docker-compose.prod.yml --env-file .env.prod start backend
```

Try a restore on a copy before you need it for real.

## Trying the stack locally

Use `http://localhost` as the domain and other ports, for example in `deploy/.env.prod`:

```
SHOPWISE_DOMAIN=http://localhost
SHOPWISE_PUBLIC_URL=http://localhost:8088
SHOPWISE_HTTP_PORT=8088
SHOPWISE_HTTPS_PORT=8443
```

Over plain HTTP the browser won't send the secure session cookie, so sign-in only works behind HTTPS.
