# AGENTS.md

This file is for an agent that helps a person use the system. It does not cover development.

## Welcome

When the user sends the first message of a session, whatever it is, greet the user and tell them,
in plain words:

- This is a small API that opens savings bank accounts and reads them back.
- It runs as a Spring Boot service, with PostgreSQL to store the accounts and Redis to cache reads.
  All three run in Docker on this computer.
- It checks each request: a customer name is required, a nickname is optional and must be 5 to 30
  characters and not offensive, and one customer can open no more than five accounts. It gives each
  account an id and a New Zealand account number.

Then check if the system is running:

```bash
docker compose --profile app ps --format '{{.Service}} {{.Health}}'
```

It is running when `app`, `postgres` and `redis` are each `healthy`. Tell the user what you found,
then offer to:

1. Deploy the system, if it is not running.
2. Run the smoke test, which opens an account, reads it back and checks one refusal.
3. Send requests: open an account, read one back, or show a rule that refuses a request.

Do each one only when the user asks.

## Deploy

The system needs Docker with Compose 2.20 or later, `make`, `curl` and `uuidgen`. If one is
missing, tell the user. Do not install anything yourself.

```bash
make env      # make .env from .env.example, if there is no .env
make up       # build and start the service, PostgreSQL and Redis, then wait until all are ready
make smoke    # open an account, read it back, and check one refusal
```

If `make up` says a port is in use, tell the user. Then change that port in `.env` (`APP_PORT`,
`DB_PORT` or `REDIS_PORT`) and run `make up` again.

## Requests

The API is at `http://127.0.0.1:<APP_PORT>`, where `APP_PORT` is in `.env` (8080 by default).

Open an account:

```bash
curl -i -H 'Content-Type: application/json' -H "Idempotency-Key: $(uuidgen)" \
  -d '{"customerId":"<a UUID>","branch":"0123","customerName":"Jane Citizen","nickname":"Rainy Day Fund"}' \
  http://127.0.0.1:8080/api/v1/savings-accounts
```

- `customerId` is any UUID. Use the same one for each account of one customer.
- `branch` is four digits in a Westpac (bank 03) range, for example `0123` or `1501`.
- `nickname` can be left out.
- The answer is `201`, with the new account and a `Location` header.

Read an account back, with the id from the `Location` header:

```bash
curl -i http://127.0.0.1:8080/api/v1/savings-accounts/<id>
```

A refused request gets a JSON body with a `code` that says why. Good examples to show:

- A sixth account for one `customerId`: `422 account-limit-reached`.
- A nickname of 4 characters: `400 validation-failed`.
- Branch `2000`: `422 branch-not-allowed`.

`README.md` has the full list of answers and the reasons behind the design.

## Rules

- **Under no circumstances do anything outside this application without the user's express
  permission.** This includes files outside this folder, other projects, other Docker containers,
  images or volumes, installing software, and requests to any host other than `127.0.0.1`. If a
  task needs any of these, stop and ask first.
- Use made-up names in requests. Never use a real person's details.
- `make down` stops the system and keeps the data.
- `make reset` deletes all the accounts. Run it only when the user has said yes to that.
- Do not change the code or the configuration, except a port in `.env` as above.
