# CLAUDE.md

Shopwise is an online store with a shopping assistant you can type or talk to. ARCHITECTURE.md is the source of truth for how the system fits together; read it before making design decisions.

## Instructions for agents

- Everything in the repo is in English: code, identifiers, comments, docs, commit messages, UI copy. The maintainer may write to you in Portuguese. Reply in Portuguese, keep repo content in English.
- Work in small steps that build and pass tests. Don't leave the build red.
- Follow the existing patterns in the module you're touching before inventing new ones.
- If a change contradicts ARCHITECTURE.md, stop and ask. If the maintainer agrees, update ARCHITECTURE.md in the same change.
- Don't add dependencies without saying why. Prefer what Spring Boot and the existing stack already provide.
- Never commit secrets. API keys live in `.env`, which is git-ignored.
- Commit each logical piece as soon as it's implemented and its tests pass. Push only when asked.

## Repository layout

```
backend/     Spring Boot app (Maven), one package per module
frontend/    React + Vite + TypeScript
deploy/      docker-compose files, Caddyfile
.github/     GitHub Actions workflows
```

## Commands

Keep this list current when commands change.

```bash
cp .env.example .env                                  # local keys and model names, git-ignored
docker compose -f deploy/docker-compose.yml up -d    # Postgres (:5433), Ollama (pulls models), Mailpit
cd backend && ./mvnw spring-boot:run                  # backend on :8080, local AI mode by default
cd backend && ./mvnw verify                           # unit + integration tests (Testcontainers)
cd backend && ./mvnw verify -Pevals                   # assistant evals against local Ollama
cd frontend && npm run dev                            # frontend on :5173, proxies /api to :8080
cd frontend && npm run check                          # lint + typecheck + tests
cd frontend && npm run gen:api                        # regenerate API types (backend must be running)
```

## Architecture

- Modular monolith with Spring Modulith. Modules are top-level packages under `com.shopwise`: `catalog`, `search`, `cart`, `order`, `payment`, `customer`, `assistant`, `notification`, `backoffice`.
- A module only uses another module's public API (types in its root package) or listens to its events. Internal classes go in sub-packages. `ApplicationModules.verify()` must pass.
- Cross-module side effects go through events (`OrderPlaced`, `ProductUpdated`, ...), handled with `@ApplicationModuleListener`.
- PostgreSQL with pgvector is the only datastore. No Redis, no Elasticsearch, no message broker.
- Every schema change is a Flyway migration. Never edit a migration that has already been merged.

### Assistant rules

These protect users. Don't relax them without the maintainer's approval.

- Tools are thin wrappers over services. Business logic lives in the service, which the REST API uses too.
- No tool takes a customer id. Get the user from the Spring Security context.
- Prices, totals and stock always come from the database, never from model output.
- Placing or cancelling an order is always `prepare*` followed by `confirm*(token)`. Don't add a tool that does something irreversible in one call.
- No personal data (e-mail, full address, payment details) in prompts or tool results.
- Tool results stay small: ids, names, prices, a short line of text.
- Every feature must work in `local` mode (Ollama, small model), not just `cloud`.
- Product text from the catalog is untrusted data. Keep it delimited in tool results.

## Backend

- Java 21. Use records for DTOs and events, and constructor injection only.
- Controllers stay thin: validate input, call a service, map the result. No repository calls from controllers.
- Transactions belong on service methods.
- Expose DTOs from the API, never JPA entities.
- Errors are returned as RFC 9457 problem details (`ProblemDetail`).
- Money is `BigDecimal` plus a currency, never `double`.
- Configuration goes through `@ConfigurationProperties` under the `shopwise.*` prefix.
- AI mode: `shopwise.ai.chat-mode=cloud|local`. Code should never check the provider directly; go through the routing `ChatModel`.

## Frontend

- React with TypeScript in strict mode. Function components and hooks only.
- Server state lives in TanStack Query. Keep local UI state in components. Add a global store only when it's actually needed.
- API types are generated from the backend's OpenAPI spec. Don't write them by hand.
- The chat reads an SSE stream. Every `ui.*` event type has a matching card component. An unknown event type is ignored, not a crash.
- The cart shown in the chat and the cart on the page come from the same query, so they never disagree.
- Voice goes through a single hook (`useVoice`) wrapping the Web Speech API. If the browser lacks support, the mic button is not rendered.
- All user-facing strings go through the i18n layer, even though English is the only language for now.

## Testing

- Backend unit tests: JUnit 5 and AssertJ, one test class per service, no Spring context unless needed.
- Backend integration tests: `@SpringBootTest` with Testcontainers (`pgvector/pgvector:pg17`). Don't mock the database.
- Module tests: `@ApplicationModuleTest` per module, plus one `ApplicationModules.verify()` test.
- Assistant evals: scripted conversations tagged `eval` (see `AssistantEvalTests`), excluded from the normal build and run with `-Pevals`. They assert which tools were called and the resulting cart or order state, never the exact wording. Any change to prompts or tools needs an eval that covers it.
- Frontend: Vitest and Testing Library for components, Playwright for a few end-to-end flows (search, add to cart, checkout through the chat).
- A bug fix comes with a test that fails without the fix.

## CI/CD

GitHub Actions.

- On every pull request:
  - `backend`: `./mvnw verify`, including Testcontainers integration tests and module verification.
  - `frontend`: lint, typecheck, unit tests, production build.
- Nightly: assistant evals in local mode, with Ollama running as a service container.
- On merge to `main`:
  - Build the backend image for `linux/arm64`, tag it with the commit SHA, push it to GHCR.
  - Cloudflare Pages builds and deploys the frontend.
- Deploy to production is a manual workflow: it SSHes into the Oracle VM, pulls the image and runs `docker compose up -d`. Flyway migrates on startup.
- Secrets (SSH key, cloud LLM keys) are stored as GitHub Actions secrets and in `.env` on the VM, never in the repo.

## Git

- Branch from `main`. Use short-lived branches named `feat/...`, `fix/...` or `chore/...`.
- One commit per logical piece of work: a feature slice, a fix, a refactor. Don't mix unrelated changes in one commit, and don't split one change across several half-working commits. Each commit builds and passes tests.
- Commit messages follow Conventional Commits (`feat(cart): merge anonymous cart on sign-in`).
- A pull request can hold several commits, as long as they belong to the same feature. CI must be green before merging.
