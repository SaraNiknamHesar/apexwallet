# Demo script

No video is stored in this folder. Record the screen while you follow this, or just run it live.

1. `cd apex/ApexWallet`
2. `mvn test`
3. `mvn -pl apexwallet-app spring-boot:run`
4. Open `http://localhost:8080/swagger-ui.html`
5. Log in as alice / alice-pass-1
6. List accounts and send 1000 cents to bob with header `X-Idempotency-Key: rent-1`
7. Send the same request again and show `Idempotency-Replayed: true`
8. In another terminal, `cd web`, then `npm install`, then `npm run dev`
9. Open `http://localhost:3000`. Sign in as Alice, send 10.00 EUR, send that same payment again, then send 1,000.00 EUR and approve it as Sam
10. Stop the API with Ctrl+C

Say clearly that this running demo keeps data in memory. Postgres, Kafka, and Grafana start only when you ask for them: `docker compose up -d postgres`, then the jar with `--spring.profiles.active=postgres`. Add `,kafka` only when the broker is up. Grafana is `docker compose up -d prometheus grafana` on port 3001. A hosted model is used only when `apex.model-url` is set.
