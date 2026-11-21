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
| `prometheus` | Scrapes the backend's `/actuator/prometheus` every 15 s, keeps 30 days |
| `grafana` | "Shopwise overview" dashboard; listens on the VM's localhost only |

## First time on the server

1. Create the VM and open ports 80 and 443 (in the cloud firewall and, on Oracle's images, in `iptables` too).
2. Install Docker and the Compose plugin.
3. Point a domain at the VM's public IP (DuckDNS works for a free subdomain).
4. The images are private (private repository), so log the VM in to GHCR once with a personal access token that has `read:packages`:

   ```bash
   echo <token> | docker login ghcr.io -u <github-user> --password-stdin
   ```

5. Copy the `deploy/` folder to the server (the deploy workflow does this too), then:

   ```bash
   cp .env.prod.example .env.prod   # fill in the domain, a strong POSTGRES_PASSWORD and the keys you have
   docker compose -f docker-compose.prod.yml --env-file .env.prod up -d
   ```

6. Sign up in the store with an e-mail listed in `SHOPWISE_ADMIN_EMAILS` to get the backoffice.
7. For card payments, set `STRIPE_SECRET_KEY` and create a webhook endpoint in the Stripe dashboard pointing to `https://<domain>/api/payments/stripe/webhook` with the events `checkout.session.completed`, `checkout.session.expired`, `checkout.session.async_payment_succeeded` and `checkout.session.async_payment_failed`. Put its signing secret in `STRIPE_WEBHOOK_SECRET`. Use test keys until you're ready to take real payments.

The first start pulls the Ollama models (about 5 GB), so the assistant needs a few minutes before local mode answers.

## Releasing and deploying

Every push to `main` runs **Release images** (`.github/workflows/release.yml`): it builds the backend and web images for `linux/arm64` and `linux/amd64` and pushes them to GHCR, tagged with the commit SHA and `latest`.

Deploying is manual: run **Deploy** (`.github/workflows/deploy.yml`) from the Actions tab with the tag to deploy. It copies `deploy/` to the VM (never `.env.prod`), sets `SHOPWISE_TAG`, pulls, restarts, and checks the site answers. It needs, in a GitHub environment called `production`:

| Name | Kind | Value |
|---|---|---|
| `DEPLOY_HOST` | secret | the VM's address |
| `DEPLOY_USER` | secret | SSH user on the VM |
| `DEPLOY_SSH_KEY` | secret | private key allowed to log in |
| `DEPLOY_KNOWN_HOSTS` | secret | output of `ssh-keyscan <host>` |
| `DEPLOY_PATH` | variable | optional, defaults to `shopwise/deploy` |
| `PUBLIC_URL` | variable | optional, enables the check after deploying |

To deploy by hand instead, set `SHOPWISE_TAG` in `.env.prod` and run:

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod pull backend web
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d
```

Flyway migrates the database when the backend starts.

## Monitoring

Grafana isn't exposed to the internet. Open an SSH tunnel and browse to http://localhost:3000 (user `admin`, password `GRAFANA_ADMIN_PASSWORD`):

```bash
ssh -L 3000:localhost:3000 <user>@<vm>
```

The "Shopwise overview" dashboard shows orders, revenue, cancellations, HTTP traffic, latency and errors, the assistant's time to first token, model requests and tokens per provider, and JVM memory. The backend's metrics endpoint is only reachable inside the Docker network: Caddy doesn't route `/actuator`.

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
