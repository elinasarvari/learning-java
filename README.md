# learning-java

## Running the Project

### Backend (Spring Boot)

**Prerequisites:** Java 21, Maven

```bash
cd backend
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080`.  
H2 console (dev): `http://localhost:8080/h2-console`

### Frontend (React + Vite)

**Prerequisites:** Node.js

```bash
cd frontend
npm install
npm run dev
```

The app will be available at `http://localhost:5173`.

### Redis (caching example)

**Prerequisites:** Docker

```bash
docker compose up -d redis
```

Redis is **optional** — the application runs without it. `RedisCache` degrades every
operation to a cache miss, so endpoints stay correct, just slower.

#### Trying the cache

```bash
# First call: ~400ms, logs "CACHE MISS"
curl -w "\n%{time_total}s\n" http://localhost:8080/api/catalog/products/p-001

# Second call: single-digit ms, no log line — served from Redis
curl -w "\n%{time_total}s\n" http://localhost:8080/api/catalog/products/p-001

# Hit/miss counters
curl http://localhost:8080/api/catalog/cache/stats

# A write evicts the entry — the next read misses again
curl -X PUT http://localhost:8080/api/catalog/products/p-001/price \
     -H 'Content-Type: application/json' -d '{"price": 99.00}'

# Inspect what is actually stored
docker compose exec redis redis-cli --scan --pattern 'learning-java:*'
docker compose exec redis redis-cli GET 'learning-java:product:p-001'
docker compose exec redis redis-cli TTL 'learning-java:product:p-001'
```

Seeded products: `p-001`…`p-004` in category `peripherals`, `p-005`/`p-006` in `furniture`.

The 400ms delay is artificial — H2 is otherwise too fast for the cache's effect to be
visible. Disable it with `app.catalog.simulated-latency-ms=0`.

See [docs/architecture/caching-pattern.md](docs/architecture/caching-pattern.md) for the
design rules and known gaps.

#### Key/value comparison endpoints

Compare a supplied parameter against what is stored under a key, without reading the
value back out.

```bash
# Store a value (raw string, not JSON)
curl -X PUT http://localhost:8080/api/cache/otp:user-1 \
     -H 'Content-Type: application/json' -d '{"value":"483920","ttlSeconds":300}'

# Compare — leaves the key in place, so it can be checked again
curl -X POST http://localhost:8080/api/cache/otp:user-1/match \
     -H 'Content-Type: application/json' -d '{"value":"483920"}'
# -> {"key":"otp:user-1","matched":true,"ttlSeconds":287}

# Compare and consume — succeeds exactly once (atomic GETDEL)
curl -X POST http://localhost:8080/api/cache/otp:user-1/verify \
     -H 'Content-Type: application/json' -d '{"value":"483920"}'

# Presence and remaining TTL, without revealing the value
curl http://localhost:8080/api/cache/otp:user-1/exists
```

| Method | Path | Purpose |
|--------|------|---------|
| `PUT` | `/api/cache/{key}` | Store a raw string. **503** if the write did not land |
| `POST` | `/api/cache/{key}/match` | Compare; key survives |
| `POST` | `/api/cache/{key}/verify` | Compare and delete on success (single-use) |
| `GET` | `/api/cache/{key}/match?value=` | Same as POST match, for quick curl checks |
| `GET` | `/api/cache/{key}/exists` | Presence + TTL only |
| `DELETE` | `/api/cache/{key}` | Evict |
| `GET` | `/api/cache/{key}` | Raw value — **403 unless** `app.cache.lookup.expose-values=true` |

Missing key, expired key and wrong value all return `matched: false`. Distinguishing
them would tell a caller which keys exist.
