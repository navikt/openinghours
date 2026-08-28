# openinghours

Monorepo for Navs åpningstidstjeneste.

| Katalog | Innhold |
| --- | --- |
| [`backend/`](backend/) | Kotlin/Spring Boot-API med regelmotor, Postgres og Flyway |
| [`frontend/`](frontend/) | React-klient med åpningstidskalender og admin, servert av en BFF i Node |

De to delene er selvstendige: hver har sin egen `Dockerfile`, `.nais/nais.yaml`
og byggekjede. Frontend snakker med backend over HTTP, ikke gjennom delt kode.

## Utvikling

Hver del bygges fra sin egen katalog:

```bash
cd backend  && mvn spring-boot:run   # http://localhost:8081
cd frontend && pnpm install && pnpm dev   # http://localhost:5173
```

Se [`frontend/README.md`](frontend/README.md) for frontendens detaljer.

## Bygg og deploy

To workflows i `.github/workflows/`, hver med sti-filter, så en endring i
frontend ikke trigger et fullt Maven-bygg:

| Workflow | Trigges av | Image | Manifest |
| --- | --- | --- | --- |
| `backend.yaml` | `backend/**` | `openinghours` | `backend/.nais/nais.yaml` |
| `frontend.yaml` | `frontend/**` | `openinghours-frontend` | `frontend/.nais/nais.yaml` |

Begge deployer til `prod-gcp` fra `main`.

## Miljøer

| Applikasjon | URL | Tilgang |
| --- | --- | --- |
| Backend | `https://openinghours.nav.no` | API-nøkkel |
| Frontend | `https://oppetidskalender.ansatt.nav.no` | Nav-ansatte |

Frontenden skal etter planen åpnes for publikum. Se «Tilgang» i
[`frontend/README.md`](frontend/README.md).
