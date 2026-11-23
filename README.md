# openinghours

Monorepo for Navs åpningstidstjeneste.

| Katalog | Innhold |
| --- | --- |
| [`backend/`](backend/) | Kotlin/Spring Boot-API med regelmotor, Postgres og Flyway |
| [`frontend/`](frontend/) | React-klient med åpningstidskalender og admin, servert av en BFF i Node |

De to delene er selvstendige: hver har sin egen `Dockerfile`, `.nais/nais.yaml`
og byggekjede. Frontend snakker med backend over HTTP, ikke gjennom delt kode.

## Aktiviteter

Vedlikehold og andre planlagte hendelser registreres som *aktiviteter*
(`/api/openinghours/activity`), ikke som åpningstidsregler. En aktivitet
gjelder én eller flere tjenester (med ID eller bare navn), har overskrift, melding og start/slutt
(lokal tid, `Europe/Oslo`). Aktiviteter påvirker ikke `isOpen`, og
en tjeneste kan ha flere aktiviteter samme dag. Spørringer finnes for i dag,
en gitt dato og et datointervall – per tjeneste eller for alle tjenester. Se
Swagger (`/swagger-ui.html`) for detaljer.

### Aktiviteter fra #varsling-nedetid

Backend henter meldinger fra Slack-kanalen #varsling-nedetid
(`conversations.history`, hvert 5. minutt) og svar i tråder på meldinger fra
siste døgn (`conversations.replies`), og lagrer råteksten i
`slack_downtime_message`. En LLM (Azure OpenAI) får meldingen, tidspunktet den
ble postet, eventuell trådstart og listen over kjente tjenester. Modellen
*trekker bare ut* hva som står: type melding, tjenester, tidspunkter, varighet
og en ryddet melding. Tidsreglene under brukes deretter i koden, slik at
resultatet blir likt fra gang til gang. Modellsvaret, antagelsene som ble gjort
og koblingen til aktivitetene lagres på meldingen (`reason`). Feilede uttrekk
prøves på nytt inntil `SLACK_DOWNTIME_MAX_ATTEMPTS` ganger.

**Tjenester.** Kjente tjenester lagres med ID, slik at aktiviteten vises i
tjenestens kalender. Tjenester som ikke finnes i katalogen (f.eks. ID-porten,
Tableau eller «alle *.nav.no-tjenester») lagres med navn
(`activity.serviceNames`) i stedet for å forkastes. En aktivitet må ha minst én
tjeneste-ID eller ett navn.

**Tid** (lokal tid, meldingstidspunktet avrundes ned til hele minutter):

| Oppgitt | Tidsvindu |
| --- | --- |
| start og slutt | som oppgitt; slutt før/lik start samme dato flyttes til neste dag (22:00–02:00) |
| start og varighet | slutt = start + varighet |
| slutt og varighet | start = slutt − varighet |
| bare slutt | start = meldingstidspunktet (samme regel for midnatt) |
| bare start | 30 min ved «kort/kortvarig/litt nedetid/restart», ellers til midnatt |
| bare varighet | start = meldingstidspunktet |
| bare dato (ikke i dag) | hele dagen |
| ingenting | start = meldingstidspunktet, slutt som for bare start |

Varighet: «et par» = 2, «noen» = 3, intervaller bruker høyeste verdi (30–40 min
→ 40 min, en til to timer → 2 t), og sekunder gir 1 minutt. Vinduer der slutt
ikke er etter start, eller som er lengre enn 14 dager, forkastes. Aktiviteter
som allerede er over, lagres likevel. Finnes det allerede en aktivitet for samme
tjeneste med overlappende tid, opprettes ingen ny.

**Oppfølging.** Meldinger som melder at arbeidet er ferdig, avlyst eller
utsatt, endrer aktiviteter som er opprettet fra Slack. Et trådsvar gjelder
aktivitetene til trådstarten; ellers brukes Slack-aktiviteter for samme tjeneste
som er meldt innen `SLACK_DOWNTIME_FOLLOW_UP_WINDOW_HOURS` (standard 24).

| Type | Virkning |
| --- | --- |
| Ferdig | slutt settes til oppgitt tidspunkt, ellers meldingstidspunktet |
| Avlyst før start | aktiviteten slettes |
| Avlyst etter start | slutt settes til meldingstidspunktet |
| Avlyst for noen tjenester | bare de tjenestene fjernes |
| Utsatt | aktiviteten flyttes; uten nytt tidspunkt slettes den |

Aktiviteter som er opprettet eller endret manuelt, endres aldri av Slack.

Meldingsstatus: `PENDING`, `ACTIVITY_CREATED`, `ACTIVITY_UPDATED`,
`ACTIVITY_CANCELLED`, `NOT_RELEVANT` og `FAILED`.

Aktiviteter fra Slack vises som standard bare for Nav-ansatte
(`SLACK_DOWNTIME_ONLY_SHOW_FOR_NAV_EMPLOYEES`). Redigerte eller slettede
Slack-meldinger oppdaterer ikke aktiviteter som allerede er opprettet.

| Endepunkt | Beskrivelse |
| --- | --- |
| `GET /api/openinghours/slack-downtime/messages` | Siste meldinger med status, modellsvar og aktivitet-ID-er |
| `POST /api/openinghours/slack-downtime/poll` | Hent og behandle nye meldinger nå |
| `POST /api/openinghours/slack-downtime/messages/{id}/retry` | Legg en feilet/ikke-relevant melding i kø igjen |

Funksjonen er av til alle disse er satt: `SLACK_BOT_TOKEN` (scope
`channels:history`, boten må være med i kanalen), `SLACK_DOWNTIME_CHANNEL_ID`,
`AZURE_OPENAI_ENDPOINT`, `AZURE_OPENAI_API_KEY` og `AZURE_OPENAI_DEPLOYMENT`.
Valgfritt: `AZURE_OPENAI_API_VERSION`, `SLACK_DOWNTIME_POLL_CRON`,
`SLACK_DOWNTIME_LOOKBACK_HOURS` (første kjøring, standard 24),
`SLACK_DOWNTIME_BATCH_SIZE` og `SLACK_DOWNTIME_FOLLOW_UP_WINDOW_HOURS`.
Azure OpenAI-verten må også legges til under
`accessPolicy.outbound.external` i `backend/.nais/nais.yaml`.

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
