# Core API and Guardrails

This service was built for the backend engineering assignment as a small Spring Boot microservice backed by PostgreSQL and Redis. PostgreSQL stores the source-of-truth content (`users`, `bots`, `posts`, `comments`) and Redis handles the fast-changing guardrail state such as virality scores, bot reply caps, cooldown windows, and pending notification batches.

## Stack

- Java 17
- Spring Boot 3.2
- Spring Data JPA
- PostgreSQL 15
- Redis 7
- Docker Compose

## What is implemented

### Phase 1

- `POST /api/posts` creates a post from either a user or a bot.
- `POST /api/posts/{postId}/comments` adds a comment to a post.
- `POST /api/posts/{postId}/like` tracks a like.
- JPA entities are present for `User`, `Bot`, `Post`, and `Comment`.
- `Comment` supports threaded replies through `parentCommentId`, and the `Post` to `Comment` relationship is mapped in JPA.

### Phase 2

- Virality score is stored in Redis under `post:{id}:virality_score`.
- Points are applied exactly as required:
  - bot reply = `+1`
  - human like = `+20`
  - human comment = `+50`
- Horizontal cap is enforced with a Redis Lua script against `post:{id}:bot_count`.
- Vertical cap blocks comments deeper than level `20`.
- Cooldown cap uses a 10-minute TTL key in Redis:
  - `cooldown:bot_{id}:user_{id}`

### Phase 3

- If a bot interacts with a user while that user is still inside the 15-minute notification cooldown, the message is pushed to `user:{id}:pending_notifs`.
- Pending users are also tracked in a Redis set so the sweeper does not need to scan Redis with wildcard keys.
- A scheduled job runs every 5 minutes and logs a summary message for queued notifications.

## Why the concurrency guard is safe

The race-condition-sensitive part is the horizontal bot cap. That check is not implemented in Java with `get` then `set`, because that would allow multiple requests to pass at the same time. Instead, the service executes a Lua script inside Redis. Redis runs that script atomically, so each competing request sees a consistent counter update. Once the counter reaches `100`, every later request gets `-1` back and the API returns `429 Too Many Requests`.

I also added rollback handling around comment creation: if a bot slot or cooldown key is reserved in Redis but the database save fails, the controller releases those Redis keys before rethrowing the error. That keeps Redis guardrail state aligned with the database as closely as possible.

## Running locally

### With Docker

```bash
docker-compose up --build -d
```

This starts:

- PostgreSQL on `localhost:5432`
- Redis on `localhost:6379`
- Spring Boot on `localhost:8080`

### Local configuration

`application.yml` expects:

- PostgreSQL database name: `arogyadoot`
- PostgreSQL username: `user`
- PostgreSQL password: `password`
- Redis host: `localhost` by default

## Postman collection

A ready-to-import collection is included here:

- [Core-API-Guardrails.postman_collection.json](postman/Core-API-Guardrails.postman_collection.json)

It includes example requests for:

- creating a post
- adding a top-level comment
- replying to a comment
- liking a post
- reading the virality score

## Testing notes

There is a concurrency-focused test in:

- [ConcurrentBotCommentTest.java](/c:/Users/Hp/Java-DEV/src/test/java/com/coreapi/guardrails/service/ConcurrentBotCommentTest.java)

It checks that, under 200 concurrent attempts, exactly 100 bot comment slots are accepted and 100 are rejected.

## Submission notes

The project intentionally keeps all mutable guardrail state out of in-memory Java collections. Redis is the gatekeeper for:

- virality counters
- bot reply caps
- bot-to-user cooldown windows
- notification cooldowns
- queued notification batches

That keeps the service stateless and makes the concurrency behavior stable even when multiple requests hit the API at the same time.
