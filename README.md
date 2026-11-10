# Shopwise

An online store you can talk to. Browse and buy the usual way, or tell the assistant what you're after:

```
> I need running shoes under $100
> add the second one, size 10
> check out with my saved address
```

It finds products, fills the cart, and places the order once you confirm. Works by typing or by voice, in English.

See [ARCHITECTURE.md](ARCHITECTURE.md) for how it's built.

## Built with

- Java 21, Spring Boot 4.1, Spring Modulith, Spring AI, Spring Batch
- PostgreSQL 17 with pgvector
- React, Vite, TypeScript
- Ollama for local models, or the Gemini/Groq free tiers

The chat model can run on a free cloud API or locally on Ollama, and you can switch between the two.

## Roadmap

1. Catalog, search, cart and chat, running locally
2. Accounts, checkout, orders, voice
3. Admin backoffice, CSV import, production deploy
4. Real payments (Stripe test mode), recommendations
