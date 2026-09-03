# OpeningHours frontend

Webgrensesnitt for Navs åpningstidstjeneste. Viser driftsbildet for alle
tjenester i dag og de nærmeste dagene, én dato om gangen, som kalender per
tjeneste (måned, uke og år), som sammenligning på tvers, og som oversiktstabell.

Backend ligger i `../backend` (Kotlin/Spring Boot, port 8081).

## Arkitektur

```
Nettleser ──▶ Wonderwall (sidecar, Azure AD) ──▶ BFF (Express, Node 22)
                                                    │  legger på X-API-Key
                                                    ▼
                                              Backend :8081
```

BFF-en serverer også den ferdigbygde klienten. API-nøkkelen finnes kun i
BFF-prosessen — den kommer aldri inn i klientbundelen.

| Mappe | Innhold |
| --- | --- |
| `client/` | React + TypeScript + Vite, NAV Aksel designsystem |
| `server/` | BFF: proxy med whitelist, autorisasjon, maskering, statiske filer |
| `.nais/` | NAIS-manifest (`openinghours-frontend`, namespace `navdig`) |
| `design_handoff_openinghours/` | Designleveranse (referanse, ikke produksjonskode) |

## Kom i gang

Krever Node 22+ og pnpm 11.

```bash
pnpm install
```

Start backend først (fra `../backend`):

```bash
docker compose up -d postgres
mvn spring-boot:run
```

Deretter frontend:

```bash
pnpm dev          # Vite på :5173 og BFF på :3000
```

Vite proxyer `/api` og `/me` videre til BFF-en, så åpne `http://localhost:5173`.

### Miljøvariabler

Legg dem i `frontend/.env` (ikke sjekk inn filen).

| Variabel | Standard i dev | Beskrivelse |
| --- | --- | --- |
| `API_KEY` | `key` | API-nøkkel til backend. **Påkrevd i produksjon.** |
| `BACKEND_URL` | `http://localhost:8081` | Backendens basis-URL |
| `PORT` | `3000` | Porten BFF-en lytter på |
| `STATIC_DIR` | `../client/dist` | Hvor de bygde klientfilene ligger |
| `PUBLIC_ACCESS` | `false` | Om kalenderen er åpen for uinnloggede. Se «Tilgang» |
| `ADMIN_GROUP_ID` | tom | Entra ID-gruppen som gir admintilgang. Tom = alle innloggede er admin |

Standardverdien for `API_KEY` matcher `../backend/env.local`.

I produksjon hentes `API_KEY` fra secreten `openinghours-app-swagger-secret` — samme
secret som backend bruker. Det er med vilje: backend godtar nøyaktig én gyldig nøkkel,
så en egen frontend-secret måtte hatt identisk verdi. Rotering må derfor gjøres for
begge appene samtidig.

## Designsystem

Appen bruker Aksels **darkside**-bundel, ikke den eldre `@navikt/ds-css`:

```ts
import '@navikt/ds-css/darkside';   // definerer --ax-*-tokenene
<Theme theme="light">…</Theme>      // skrur på omdøpingen navds-* → aksel-*
```

Disse to hører uløselig sammen, og begge er lette å fjerne ved et uhell:

- **CSS-en alene** gir komponenter uten stil, fordi darkside kun inneholder
  `aksel-`-klasser mens komponentene skriver `navds-` uten `<Theme>`.
- **`<Theme>` alene** gir omdøpte klasser som ingen CSS matcher.
- **Legacy-bundelen** definerer ingen `--ax-*`-tokens i det hele tatt. Designet er
  spesifisert i nettopp de tokenene, så all egen CSS mister farger og spacing i
  stillhet — nettleseren dropper deklarasjoner den ikke kan løse opp.

`src/theme.test.tsx` vokter oppsettet. Egen CSS skal kun bruke `--ax-*`; det
finnes ingen `--a-*`-tokens å falle tilbake på.

Aksel henter Source Sans 3 fra `cdn.nav.no`, som derfor må stå i BFF-ens
`fontSrc`-direktiv. Uten det faller appen tilbake til Arial uten noen feilmelding.

## Kommandoer

```bash
pnpm dev          # klient og BFF parallelt
pnpm build        # bygger begge
pnpm start        # kjører BFF-en mot ferdig bygget klient
pnpm test         # vitest i begge pakker
pnpm typecheck    # tsc --noEmit i begge pakker
```

## Sider og URL-tilstand

All tilstand ligger i URL-en, slik at en visning kan deles som lenke.

| Rute | Parametre | Innhold |
| --- | --- | --- |
| `/` | – | Driftsbildet i dag, og stripen med de neste seks dagene |
| `/dag/:dato` | `vis=missing\|unstable\|closed\|open` | Alle tjenester på én dato, avvik øverst |
| `/tjenester` | `sok=<tekst>`, `team=<navn>`, `type=TJENESTE\|KOMPONENT` | Alle tjenester med «åpen nå»-status |
| `/t/:serviceId` | `visning=maned\|uke\|aar`, `dag=<ISO-dato>`, `dato=<ISO-dato>` | Kalender for én tjeneste |
| `/sammenlign` | `dato=<ISO-dato>`, `tjenester=<id,id,…>` | Inntil seks tjenester på samme tidsakse |
| `/admin` | – | Nøkkeltall og avvik i oppsettet |
| `/admin/regler` | `flagg=ansatte\|ubrukt\|ugyldig` | Regelliste med søk og filter |
| `/admin/regler/ny`, `/admin/regler/:ruleId` | `slett=1` | Regelskjema med veiviser |
| `/admin/grupper`, `/admin/grupper/:groupId` | – | Gruppetre med prioritet og testpanel |
| `/admin/tjenester` | – | Tjenester og gruppekobling |

Admin krever innlogging og lastes som egen bundel — uinnloggede laster den aldri.

`dag` er ankeret som bestemmer perioden, `dato` er dagen som er valgt i
detaljpanelet. De tre visningene deler samme range-endepunkt og skiller seg bare i
hvilket tidsrom de spør om — logikken ligger i `lib/view.ts`.

Forsiden og dagsvisningen deler bøttene i `lib/summary.ts`: uten åpningstider,
ustabil, stengt, åpen — i den rekkefølgen. Bøttene er gjensidig utelukkende, så en
tjeneste som er både åpen og ustabil telles kun som ustabil. Det er avviket som er
verdt å vise.

Dagens status hentes fra `/daily` (ett kall for alle tjenester). Andre datoer må
hentes med ett kall per tjeneste, siden backend ikke har noe samlet endepunkt for
en dato. Stripen henter derfor hele seksdagersvinduet i ett kall per tjeneste
framfor én dag av gangen.

To detaljer skiller `/daily` fra periodeendepunktet, og begge håndteres i
`dailyToQuery`: dagcachen sier ikke fra når ingen regel traff — den sender
backendens standardregel (`ruleName = "No Rules stated"`, døgnåpent) — og den
bærer `isOpen` for akkurat nå. Forsiden lover «åpne nå», så bøttene får
klokkeslettet inn og teller en tjeneste med åpningstid 08:00–15:30 som stengt
klokken 20:00. For framtidige datoer finnes det ikke noe «nå», og da teller dagen
som åpen hvis den er åpen på et tidspunkt.

Under 720 px erstattes måneds- og ukevisningen av en agendaliste.

## Avvik mellom design og backend

### Del 1 — kalenderen

Designleveransen forutsetter fire ting backend ikke leverer. Slik er de løst:

| Avvik | Løsning |
| --- | --- |
| Designet bruker regelsyntaks som `* * man-tor 08:00-15:30` | `lib/rule.ts` implementerer **faktisk** syntaks (`<dato> <dagIMåned> <ukedag> <tid>`, numeriske ukedager 1–7) og formulerer den på norsk for visning |
| Flere åpningstidsintervaller per dag (lunsjpause) | Backend har kun ett intervall. `toIntervals()` returnerer en liste med 0–1 elementer, og `OpeningBar` tegner vilkårlig mange segmenter — så designet kan realiseres uendret den dagen backend støtter det |
| Helligdagsnavn («Grunnlovsdagen») finnes ikke i API-et | `lib/holidays.ts` speiler backendens helligdagsalgoritme og gir navnet. Kilden til sannhet for *om* en dag er rød er fortsatt `redDay` fra API-et |
| «Faller tilbake på neste regel» for `onlyShowForNavEmployees` | Backend har ingen slik modus. BFF-en **masker** dagen i stedet (`server/src/masking.ts`), og klienten viser «Intern åpningstid — logg inn som ansatt for å se». Hvilende så lenge appen er intern — se «Tilgang» |

### Del 2 — admin

| Avvik | Løsning |
| --- | --- |
| Avkrysning «Rød dag» på regelen | `redDay` settes **aldri** av `PUT`/`PATCH /rule` — det er et legacy-flagg, og røde dager beregnes ved lesetid. Avkrysningen er fjernet og erstattet med en opplysning om at helligdager kommer automatisk |
| Regelskjema sender JSON-body | `PUT /rule` og `PATCH /rule/{id}` tar **query-parametre**. `hooks/admin.ts` bygger querystring for regler og JSON-body for grupper og tjenester |
| «Ny regel» oppretter en ny regel | `PUT /rule` er en **upsert på navn**, og navn er unikt. Skjemaet sjekker navnet mot regellisten og krever en eksplisitt bekreftelse før en eksisterende regel overskrives |
| Live forhåndsvisning av én regel | Finnes ikke i API-et. `lib/evaluate.ts` speiler `OpeningHoursEvaluator.matchesDate/DayOfMonth/Weekday` og er merket som lokalt beregnet i UI. **Gruppetestpanelet** bruker derimot ekte `GET /query/group/{id}?date=`, som er autoritativt |
| «Siste endringer» / endringslogg | Ingen auditlogg finnes. Seksjonen er erstattet med en avvikstabell på adminoversikten, som svarer på det samme spørsmålet: «er noe galt?» |
| 403 ved manglende team-tilgang, 409 ved samtidig redigering | Backend har verken RBAC eller `@Version`/ETag. Begge er tatt ut av feilhåndteringen. 409 ved sletting finnes og er beholdt |
| Tjenesteskjema uten `type` | `ServiceRequest.type` er påkrevd, så feltet er beholdt |
| «Flere klokkeslett med komma» | Ett intervall per regel. Hjelpeteksten sier i stedet at en lunsjpause blir to regler i samme gruppe |
| Ukedag som `man-fre` | Numerisk 1–7. `lib/rulebuild.ts` oversetter begge veier, tapsfritt |
| Mønsteret «arbeidsuke med lunsjpause» | Krever to intervaller, som backend ikke har. Byttet ut med «Døgnåpent» |
| Dra-og-slipp i gruppetreet | Utelatt. Designet krever at det aldri er den eneste måten, og Opp/Ned-knappene dekker behovet fullt ut — også fra tastatur, uten en egen tastaturmodell å lære |

Backendens `?confirm=true` kan overstyre 409 ved sletting. Designet sier at en
«slett likevel»-knapp ikke skal finnes, så klienten sender den aldri **og**
BFF-en filtrerer den bort (`FORBIDDEN_PARAMS` i `server/src/proxy.ts`).

## Tilgang

Appen kjører i dag **kun for Nav-ansatte** på
`https://oppetidskalender.ansatt.nav.no`. Domenet slipper bare gjennom
autentiserte brukere på godkjente enheter, og Wonderwall står foran med
`autoLogin: true`. Alt som når BFF-en er derfor innlogget.

Kalenderen skal etter planen åpnes for publikum. Koden for det er bygget og
testet, men hvilende: maskeringen av interne åpningstider (`masking.ts`), de
offentlige leserutene i proxyen, og «Logg inn»-tilstandene i grensesnittet.
De aktiveres av tre samtidige endringer:

1. `ingresses` → `https://<navn>.nav.no` i `.nais/nais.yaml`
2. `azure.sidecar.autoLogin` → `false`
3. `PUBLIC_ACCESS` → `"true"`

Innloggingssjekkene i BFF-en gjelder uansett. De er forsvar i dybden mot kall
som omgår sidecaren, for eksempel pod-til-pod inne i clusteret.

### Hvem får administrere?

Å **se** kalenderen krever bare innlogging. Å **endre** åpningstider krever i
tillegg medlemskap i en Entra ID-gruppe:

```
01a18f07-4dc7-4426-a407-09a1021dc024
```

Gruppen må stå **to steder** i `.nais/nais.yaml`, og oppsettet virker ikke med
bare det ene:

1. `azure.application.claims.groups` — får Entra ID til å legge gruppen i
   `groups`-claimet i tokenet
2. `ADMIN_GROUP_ID` under `env` — forteller BFF-en hvilken gruppe den skal se
   etter

`allowAllUsers: true` beholdes ved siden av. Kombinasjonen er Nais' anbefalte
mønster når alle skal slippe inn, men bare noen skal ha utvidede rettigheter:
alle ansatte autentiseres og kan se kalenderen, mens `groups`-claimet avgjør
hvem som i tillegg er admin.

**Kun direkte medlemskap teller.** Er en bruker medlem gjennom en nestet gruppe,
kommer ikke gruppe-ID-en med i tokenet, og hen blir ikke admin.

Sperren håndheves i BFF-en (`isAdmin` i `auth.ts`, brukt av `isAllowed` i
`proxy.ts`), som avviser både lesing og skriving på adminrutene med `403`.
Grensesnittet skjuler i tillegg adminlenken og viser en forklaring på
`/admin` — men det er kosmetikk, ikke sikkerhet.

Settes `ADMIN_GROUP_ID` til tom streng, er alle innloggede admin. Det er
oppførselen appen hadde før gruppestyringen, og den holder lokal utvikling
kjørbar uten en ekte Entra ID-gruppe.

## Status

Ferdig: prosjektoppsett, API-lag, statussystem, kalender med måneds-, uke- og
årsvisning og full tastaturnavigasjon, detaljpanel, agendaliste for mobil,
sammenlign-side, landingsside med dagsstripe, dagsvisning, tjenesteoversikt,
BFF med autorisasjon og maskering, Dockerfile,
NAIS-manifest, GitHub Actions.

Ferdig i admin: oversikt med avviksdeteksjon, regelliste, regelskjema med
veiviser og lokal forhåndsvisning, gruppetre med prioritet og testpanel,
tjenester med gruppekobling, og slettedialoger som sperrer sletting av noe som
er i bruk.

Gjenstår: Wonderwall-integrasjonen er ikke testet mot en faktisk sidecar, og
det er ikke verifisert at `oppetidskalender.ansatt.nav.no` er ledig. Når
kalenderen skal åpnes for publikum, må et eget `nav.no`-navn velges — backend
opptar allerede `openinghours.nav.no`.

### Testoppsett

BFF-en testes med `light-my-request` framfor supertest. Den dispatcher rett inn i
Express-appen uten å åpne en socket, som gjør at testene også kjører i miljøer der
lytting på porter er blokkert.

Regellogikken er den mest testkritiske delen, siden den speiler Kotlin-kode:
`validate.ts` (31 tester), `evaluate.ts` (19), `rulebuild.ts` (22) og `tree.ts`
(13) dekker grensetilfellene — stigende rekkefølge, `L` sist, skuddår, sirkler i
gruppegrafen og regler som aldri kan treffe.

Ikoner: Aksel 7.40 har ikke noe flagg-ikon, så rød dag bruker `StarIcon`. I
årsvisningen er rutene for små til ikoner, og status bæres derfor av bakgrunn,
tekstvekt og understrek samtidig — aldri av farge alene.
