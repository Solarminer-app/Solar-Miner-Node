# Public deployment: Currency Rates

The public service host is `https://currency.solarminer.app`. It is for the
versioned, read-only `/api/v1/public/**` API consumed by SolarMiner Nodes and
public product surfaces. It is not part of a household's local Compose stack:
the local `currency-service` remains private at `http://currency-service:8080`.

Add the following services to the **central** Compose file that already owns
the `traefik` network, `fee.solarminer.app`, `lightning.solarminer.app`, and
`api.solarminer.app`. Do not publish `8080` or `3306` with `ports`; Traefik is
the only public ingress and the database stays internal.

```yaml
  currency-service:
    image: verdox/currency-rates-api:latest
    networks:
      - traefik
      - currency-internal
    restart: unless-stopped
    depends_on:
      mariadb-currency:
        condition: service_healthy
    env_file:
      - .env
    environment:
      - SPRING_PROFILES_ACTIVE=production
      - SERVER_FORWARD_HEADERS_STRATEGY=framework
      - MYSQL_URL=jdbc:mariadb://mariadb-currency:3306/currency_rates
      - MYSQL_USER=currency_rates
      - MYSQL_PASSWORD=${CURRENCY_DB_PASSWORD}
    labels:
      - traefik.enable=true
      - traefik.docker.network=traefik
      - traefik.http.routers.currency.entrypoints=websecure
      - traefik.http.routers.currency.rule=Host(`currency.solarminer.app`)
      - traefik.http.routers.currency.tls.certresolver=cf
      - traefik.http.services.currency.loadbalancer.server.port=8080

  mariadb-currency:
    image: mariadb:latest
    networks:
      - currency-internal
    restart: unless-stopped
    env_file:
      - .env
    environment:
      MYSQL_RANDOM_ROOT_PASSWORD: "yes"
      MYSQL_DATABASE: currency_rates
      MYSQL_USER: currency_rates
      MYSQL_PASSWORD: ${CURRENCY_DB_PASSWORD}
    volumes:
      - ./currency/mariadb:/var/lib/mysql
    healthcheck:
      test: ["CMD-SHELL", "mariadb-admin ping -h localhost -u$${MYSQL_USER} -p$${MYSQL_PASSWORD}"]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 30s

networks:
  traefik:
    external: true
  currency-internal:
    driver: bridge
```

`currency-rates` now maps the Compose `MYSQL_URL`, `MYSQL_USER`, and
`MYSQL_PASSWORD` variables to Spring's datasource configuration and includes
the MariaDB runtime driver. Without those mappings it falls back to its local
H2 store, which is appropriate only for a local/default run.

Before rollout, create the DNS record for `currency.solarminer.app` using the
same Cloudflare/TLS setup as the other central services. Verify a date-based
read request and both `GET /api/v1/public/mining-networks` and
`GET /api/v1/public/mining-networks/ETC` through Traefik. The list must contain
fresh, non-zero XMR/PRL/RVN/ETC rows after startup and the same rows must remain
available after a MariaDB/container restart. Also verify that database port
3306 and service port 8080 are unreachable from the internet.
The current API is suitable for Nodes' server-to-server reads. If a browser is
later allowed to call it directly, add an explicit CORS allowlist for trusted
SolarMiner origins; do not add `Access-Control-Allow-Origin: *` by default.

Add an edge rate-limit middleware after it exists in the central Traefik
dynamic configuration. Referencing a nonexistent middleware label will make
the router unusable, so this document intentionally does not assume a
particular file-provider middleware name.
