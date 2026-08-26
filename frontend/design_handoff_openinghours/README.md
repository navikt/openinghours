# Handoff: OpeningHours — komplett designforslag

## Overview

OpeningHours er et nytt webgrensesnitt for Navs backend-tjeneste for åpningstider. Tjenesten har i dag kun REST-API og Swagger. Grensesnittet betjener to målgrupper:

1. **Publikum og Nav-ansatte som forbrukere** — «er tjenesten åpen nå?» og «når er den åpen denne måneden?»
2. **Fagansvarlige/administratorer** (Azure AD) — konfigurerer regler, grupperer dem og kobler dem til tjenester.

Pakken dekker hele forslaget: **del 1** (navigasjonsstruktur, statussystem, tjenesteoversikt, månedskalender, ukevisning, sammenligning, årsoversikt, tilstander) og **del 2** (adminoversikt, regler, grupper, tjenester, slettedialoger, feilmeldinger).

## About the Design Files

Filene i denne pakken er **designreferanser laget i HTML** — prototyper som viser tilsiktet utseende og oppførsel. De er ikke produksjonskode som skal kopieres direkte.

Oppgaven er å **gjenskape designet i målkodebasens eget miljø**: React + TypeScript med `@navikt/ds-react`, `@navikt/ds-css` og `@navikt/aksel-icons`. Prototypene bruker en rekonstruksjon av Aksel med inline-stiler og egne SVG-ikoner fordi de kjører uten byggesteg. **I produksjon skal alle farger, typografi og spacing komme fra `@navikt/ds-tokens`, alle komponenter fra `@navikt/ds-react`, og alle ikoner fra `@navikt/aksel-icons`** — ikke fra hexverdiene og SVG-banene i prototypen.

Hver fil er ett dokument som inneholder alle skjermbildene som wireframes med spesifikasjonstekst ved siden av. De er ikke klikkbare apper; se «Files» nederst for hva som faktisk er interaktivt.

### Kjente avvik mellom prototypen og ekte Aksel

Prototypens bundel er en delvis rekonstruksjon. Tre steder er den svakere enn `@navikt/ds-react`, og prototypen kompenserer med egne løsninger som **ikke skal videreføres**:

1. **`Button variant="danger"` finnes ikke i prototypens bundel.** De to slett-knappene er derfor håndstylte `<button>` med `--ax-color-danger-500` bakgrunn og hvit tekst. I produksjon: bruk `<Button variant="danger">`, som finnes i `@navikt/ds-react`.
2. **Prototypens `Select` kobler ikke `description` til feltet.** Den rendrer hjelpeteksten synlig, men uten `id` og uten `aria-describedby` på `<select>`. Ekte Aksel `Select` gjør dette riktig — ikke reproduser gapet ved å hånd-rulle labelen.
3. **Prototypens `Tag` tar ikke ikon.** Statusmerkene i kalenderen er derfor egendefinerte. Sjekk om ekte Aksel `Tag` støtter ikon før du bygger en egen komponent; kravet er at status aldri hviler på farge alene.

## Fidelity

**Høy fidelitet for kalenderen og tjenesteoversikten** (del 1). Layout, typografiskala, statusfarger, ikonplassering, spacing og all mikrotekst er ferdig bestemt og skal gjenskapes presist — men uttrykt gjennom Aksel-tokens, ikke gjennom prototypens tall.

**Lo-fi for administrasjon** (del 2). Struktur, informasjonsarkitektur, interaksjonsmønstre, all mikrotekst og validering er bestemt. Finpuss av spacing og visuelle detaljer er ikke gjort — følg Aksels standardmønstre for skjema og tabell.

## Teknisk ramme (må respekteres)

- React + TypeScript, Aksel (`@navikt/ds-react`)
- Norsk bokmål i hele grensesnittet, sentence case overalt
- WCAG 2.1 AA. **Status kommuniseres aldri med farge alene** — hver status har bakgrunn + ikon + tekst
- Responsivt: mobil, nettbrett, desktop
- Tidssone `Europe/Oslo`, uke starter mandag

---

## Datamodell

- **Service** — navn, type (`TJENESTE` | `KOMPONENT`), team, beskrivelse, lenke til overvåkning, lenke til logger, valgfri kobling til én åpningstidsgruppe.
- **OhGroup** — navn + ordnet liste av medlemmer. Et medlem er en regel eller en annen gruppe (trestruktur). **Rekkefølgen er prioritet: første regel som matcher datoen vinner.**
- **Rule** — navn, regeluttrykk (tekststreng), valgfri `displayHeader`, valgfri `displayText`, flagg `onlyShowForNavEmployees`, flagg `redDay`.

**Dagsdata per dato** (det kalenderen faktisk rendrer):
`isOpen`, `openingTime`, `closingTime`, `displayHeader`, `displayText`, `onlyShowForNavEmployees`, `redDay`, `matchedRule` (navn + uttrykk), `warningMessage`.

**Regeluttrykkets form** i eksempeldataene er fire feltverdier atskilt med mellomrom: `dato  dag-i-måned  ukedag  klokkeslett`, der `*` betyr «alle» og tomt klokkeslett betyr stengt.

```
*  *  man-tor  08:00-11:30, 12:15-15:30
*  *  fre  08:00-11:30, 12:15-14:00
*  *  lør-søn  stengt
01.05.2026, 14.05.2026, 17.05.2026  *  *  stengt
01.09.2026-30.09.2026  *  man-fre  07:00-17:00
```

**Verifiser syntaksen mot API-et før du bygger veiviseren** — den er utledet fra oppgavebeskrivelsen, ikke fra dokumentasjon. Se åpne spørsmål nederst.

---

## Sitemap / navigasjonsstruktur

Offentlig (ingen pålogging):

| Rute | Innhold |
| --- | --- |
| `/` | Tjenesteoversikt — tabell, søk, filtre |
| `/t/:id` | Kalender for én tjeneste |
| `/t/:id?visning=maned` | Månedsrutenett med detaljpanel (standard) |
| `/t/:id?visning=uke` | Ukevisning på tidsakse |
| `/t/:id?visning=aar` | Årsoversikt for røde dager |
| `/sammenlign` | Sammenlign flere tjenester på én dato |
| `/slik-virker-det` | Hvordan åpningstider settes opp |

Innlogget (Azure AD):

| Rute | Innhold |
| --- | --- |
| `/admin` | Oversikt: avvik øverst, nøkkeltall, endringslogg |
| `/admin/regler` | Regler — tabell med uttrykk, flagg og «Brukt i» |
| `/admin/regler/ny`, `/admin/regler/:id` | Veiviser og regeluttrykk, live forhåndsvisning |
| `/admin/grupper` | Grupper — liste |
| `/admin/grupper/:id` | Trestruktur, rekkefølge = prioritet, datotest |
| `/admin/tjenester` | Tjenester — CRUD og kobling av gruppe |

Adminskjemaene er egne sider, ikke modaler: de er lange, og en URL per regel gjør det mulig å dele lenke til «denne regelen».

**Viktig for implementasjonen:** adminlenker og interne snarveier skal **ikke rendres** når brukeren er uinnlogget. De skal ikke skjules med CSS eller `hidden` — de skal ikke finnes i DOM-en.

---

## Statussystem

Fem visuelle tilstander. Hver har tre bærere — bakgrunn, ikon og tekst — slik at statusen er entydig uten farge.

| Status | Ikon (`@navikt/aksel-icons`) | Tekst i grensesnittet | Tokens | Kontrast |
| --- | --- | --- | --- | --- |
| Åpen | `CheckmarkCircleIcon` | «Åpen 08:00–15:30» med klokkeslett | `--ax-text-success` (success-700 `#006128`) på `--ax-bg-raised` | 7,7:1 |
| Stengt hele dagen | `MinusCircleIcon` | «Stengt» + underlinje som sier hvorfor («Helg») | neutral-700 `#4d565e` på neutral-50 `#f7f8fa` | 6,6:1 |
| Rød dag | `FlagIcon` | «Rød dag» + navnet på dagen, f.eks. «Grunnlovsdagen · stengt» | danger-600 `#a40000` på danger-50 `#fbeaea` | 7,0:1 |
| Advarsel | `ExclamationmarkTriangleIcon` | «Ikke satt opp» + «Ingen regel treffer» | warning-700 `#6b4200` på warning-50 `#fcf0ce` | 7,7:1 |
| Åpent nå / Stengt nå | `ClockIcon` | «Åpent nå» / «Stengt nå» — nå-indikator på dagens dato | success-700 `#006128` på `--ax-bg-raised` | 7,7:1 |

Kontrasttallene er målt mot prototypens tokenverdier. **Mål dem på nytt mot `@navikt/ds-tokens`** når de reelle hexverdiene er på plass — alle par skal ligge over 4,5:1.

**Dagens dato bruker ingen egen farge.** Den får 2 px nøytral ramme (`--ax-border-neutral-strong`), et «I dag»-merke og nå-indikatoren. Dette holder markeringen av i dag fra å konkurrere med statusfargen i samme celle.

**Valgt dag** får 3 px aksentramme (`--ax-border-focus` / accent-500) og `aria-selected="true"`.

---

# Del 1 — offentlige skjermbilder

## Skjerm 1 — Tjenesteoversikt (`/`)

### Formål
Finne én tjeneste raskt og se om den er åpen nå. Alt annet er sekundært.

### Layout, desktop
Header (`InternalHeader` for innloggede, enklere variant uten meny for uinnloggede) → `Heading level=1 size=xlarge` «Åpningstider for Navs tjenester» + `BodyLong size=large` ingress → filterrad → treffteller → tabell.

Filterraden er en flex-rad, `align-items: flex-end`, `gap: var(--ax-space-16)`, wrap:
- Fritekstsøk (`Search`), `flex: 1`, minimum 260 px, label «Søk etter tjeneste», placeholder «F.eks. dagpenger»
- «Team» (`Select`), 200 px, standard «Alle team»
- «Type» (`Select`), 200 px, «Alle typer» / «Tjeneste» / «Komponent»

Treffteller over tabellen: «Viser 8 av 47 tjenester · tirsdag 12. mai 2026, kl. 13:20». Høyrestilt: «Sortert etter navn».

Tabell (`Table`), seks kolonner, proporsjoner `2fr 0.8fr 1.2fr 1.4fr 1.1fr 1.1fr` — alle med `minmax(0, …)` slik at ingen kolonne klippes på smale vinduer:

| Tjeneste | Type | Team | Status nå | Åpent i dag | Snarveier |
| --- | --- | --- | --- | --- | --- |

- **Tjeneste** — lenke til `/t/:id`, `BodyShort weight=bold`, 16 px
- **Type** — nøytral pille, 13 px, `--ax-color-neutral-100`, radius `full`
- **Team** — vanlig tekst, 15 px
- **Status nå** — statusmerke (ikon + tekst)
- **Åpent i dag** — «08:00–15:30», «Døgnåpen», «Åpner 16.00» eller «Ukjent»
- **Snarveier** — «Overvåkning» og «Logger», stablet vertikalt, `gap: 4px`, ny fane. **Bare for innloggede**; uinnlogget rendres ikke innholdet (viser «—»)

Eksempeldata: Aa-registeret (komponent, døgnåpen), Dagpengesøknad, Foreldrepengesøknad, Innsyn i sak, Meldekort (stengt nå), Pesys-integrasjon (komponent), Skriv til oss (stengt nå), Sykepengesøknad (ingen gruppe → advarsel).

### Hvorfor tabell og ikke kort
Med 20–100 tjenester tåler tabellen skanning nedover, sortering og en tett statuskolonne. Kort ville gitt for lav informasjonstetthet.

### Interaksjon
- Fritekstsøk filtrerer mens du skriver, 250 ms debounce
- Team og type er selvstendige filtre, kombineres med OG
- Aktive filtre speiles i URL-en, slik at en filtrert liste kan deles
- **Tjenestenavnet er lenken**, ikke hele raden — raden inneholder også lenker til overvåkning og logger
- Sortering på navn (standard) og team

### Tastatur
Ren tabulatorrekkefølge: søk → team → type → tabellens lenker. Tabellen er en `<table>` med `scope="col"`, ikke et rutenett — den trenger ingen pilnavigasjon.

### Skjermleser
Treffantallet ligger i et `aria-live="polite"`-område: «Viser 8 av 47 tjenester». Statusmerket har teksten synlig og trenger ingen egen `aria-label`.

### Mobil (< 640 px)
Tabellen blir kort: navn, statusmerke og én linje med åpningstid («Åpen til 15:30 i dag», «Stengt nå, åpner 16:00», «Døgnåpen», «Åpningstider ikke satt opp»). Team og type flyttes til en filtermeny (`Chips.Toggle`), snarveiene til tjenestens kalenderside. Kortet har chevron til høyre. **Alle trykkmål minst 44 px.** Header blir hamburgermeny.

---

## Skjerm 2 — Månedskalender (`/t/:id?visning=maned`)

Produktets kjerne. Hver celle svarer på ett spørsmål — «når er det åpent denne dagen?» — som tall og som strek.

### Layout, desktop
Tilbakelenke «Tilbake til alle tjenester» → `Heading level=1` med tjenestenavnet → metalinje «Tjeneste · team dagpenger · gruppe «Selvbetjening ordinær»». Høyrestilt: tjenestevelger (`Select`, 260 px) for å bytte tjeneste uten å gå tilbake.

Under: visningsvelger (`Tabs`) «Måned» / «Uke» / «År».

Navigasjonsrad, `justify-content: space-between`:
- Venstre: `Button variant=secondary size=small` «Forrige måned» og «Neste måned» (med `ChevronLeftIcon` / `ChevronRightIcon`), `Button variant=tertiary size=small` «I dag»
- Høyre: månedsnavnet 22 px halvfet + `MonthPicker` «Velg måned»

Hovedområde: flex-rad, `gap: var(--ax-space-24)` — rutenettet `flex: 1`, detaljpanel `flex: none`, **300 px**.

### Rutenettet
`display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 6px`. **`minmax(0, 1fr)` er nødvendig** — uten den gjør cellenes innhold at sporene vokser over containeren og legger seg over detaljpanelet.

Ukedagsrad over rutenettet med samme kolonnedefinisjon: Mandag … Søndag, 13 px halvfet, `--ax-text-neutral-subtle`.

### Dagcellen
Minimum 118 px høy, 1 px ramme, radius 4, `padding: 8px 8px 10px`, `display: flex; flex-direction: column; gap: 6px`, `min-width: 0`. Fire lag, alltid i samme rekkefølge:

1. **Dagnummer** — 17 px halvfet, venstrestilt. Høyrestilt i samme rad: «I dag»-merket når det gjelder (11 px, 700, uppercase, `--ax-color-neutral-800` bakgrunn, hvit tekst, radius `full`, padding 2/7)
2. **Tidsstrek** — 6 px høy, radius 3, spor i `--ax-color-neutral-100`. Segmenter absolutt posisjonert i `--ax-color-success-500`. **Streken går over et fast vindu 07:00–17:00** slik at cellene kan sammenlignes på tvers av rader: `left = (minutter − 420) / 600 · 100 %`, `width = varighet / 600 · 100 %`
3. **Status** — ikon 15 px + klokkeslett 13 px halvfet i statusfargen, `gap: 5px`. Teksten bryter (ingen `nowrap`) slik at cellen ikke presser rutenettet bredt
4. **Underlinje** — 12 px, `--ax-text-neutral-subtle`: «Stengt 11:30–12:15», «Helg», «Grunnlovsdagen · stengt», «Ingen regel treffer»

Dagens dato får i tillegg «Åpent nå» / «Stengt nå», 12 px, 700, success-700.

Dager fra forrige og neste måned: `opacity: 0.55`, dempet dagnummer, ingen status.

### Flere åpningstidsintervaller per dag
Cellen viser **samlet spenn som tall** («08:00–15:30») og **pausen som et hull i streken** — to segmenter med gap. Underlinjen sier «Stengt 11:30–12:15». Alle intervaller listes i sin helhet i detaljpanelet. Fra tre intervaller og opp faller underlinjen tilbake til «Stengt i to perioder».

### Tegnforklaring
Under rutenettet, over en 1 px topplinje: label «Tegnforklaring» + fem merker (22 × 22 px fargefelt med ikon + navn), 14 px.

### Detaljpanel (desktop)
300 px, 1 px ramme, radius 8, `padding: 22px`, `gap: 14px`:
1. Label «Detaljer for dagen» + lukkeknapp
2. `Heading` — «12. mai 2026 · tirsdag», 24 px
3. Statusmerke, 16 px — «Åpen 08:00–15:30» / «Stengt hele dagen» / «Rød dag · stengt» / «Ikke satt opp»
4. `Alert variant=warning` når `warningMessage` finnes
5. `displayHeader` (16 px halvfet) og `displayText` (15 px) i et nøytralt felt, med kildehenvisning i 12 px
6. «Regel som traff»: regelnavn 16 px halvfet, regeluttrykket i fast bredde på `--ax-color-neutral-100` med `CopyButton`, og «Rød dag: ja/nei»
7. **Kun innlogget:** lenke «Åpne regelen i administrasjon»

### Interaksjon
- Klikk på en dag åpner detaljpanelet og markerer dagen med 3 px aksentramme
- Valget ligger i URL-en (`?dato=2026-05-12`) slik at en enkelt dag kan deles som lenke
- «I dag» hopper til inneværende måned og velger dagens dato
- Nå-indikatoren beregnes i `Europe/Oslo` og oppdateres hvert minutt; den annonseres **ikke** på nytt av skjermleser

### Tastaturnavigasjon i rutenettet
- Rutenettet er **én tabulatorstopp**: bare den aktive dagen har `tabindex="0"`, resten `tabindex="-1"`
- **Piltaster** flytter én dag (venstre/høyre) eller én uke (opp/ned) og **krysser månedsgrensen** — da lastes forrige eller neste måned og fokus følger dagen
- **Home / End** går til første og siste dag i uken
- **PageUp / PageDown** går til forrige og neste måned
- **Enter / mellomrom** åpner detaljpanelet og flytter fokus til overskriften i panelet
- **Escape** lukker panelet og gir fokus tilbake til dagen

### Skjermleser
`role="grid"` med ukedagene som kolonneoverskrifter. Hver celle har hele statusen i `aria-label`:

> «12. mai 2026, tirsdag, åpent 08:00 til 15:30, stengt for lunsj 11:30 til 12:15, i dag»

Månedsbytte annonseres i et live-område: «Mai 2026, 31 dager».

### Kun for Nav-ansatte
Dager der `onlyShowForNavEmployees` er satt, vises bare når du er innlogget, og merkes «Intern» i cellen. Uinnlogget faller dagen tilbake på neste regel som treffer.

### Mobil (< 720 px) — agendaliste
Rutenettet erstattes av en vertikal agendaliste, én rad per dag, gruppert per uke, som **starter på i dag**. Rutenettet er fortsatt tilgjengelig via visningsvelgeren.

Radoppbygging: datoblokk 52 px bred (ukedagsforkortelse 12 px uppercase over dagnummer 20 px halvfet, bakgrunn i dagens statusfarge, 2 px ramme når det er i dag) → statuslinje med ikon + klokkeslett 16 px halvfet → underlinje 14 px → «I dag · åpent nå» når det gjelder → chevron. Minimum 44 px høyde per rad.

Visningsvelgeren blir et segmentert kontrollelement «Liste / Måned / År». Månedsnavigasjon: 44 × 44 px piler rundt månedsnavnet. Detaljpanelet blir en `Modal` i bunnen som dekker to tredjedeler av høyden.

---

## Skjerm 3 — Ukevisning (`/t/:id?visning=uke`)

Samme data på en tidsakse. Én rad per dag gjør pauser og kortere fredager synlige uten å lese tall.

Tidsakse øverst: flex-rad med **samme geometri som radene** — 174 px venstre avstandsholder, `flex: 1` akse, 200 px høyre avstandsholder. Etikettene 07:00 … 17:00 er **absolutt posisjonert** i akseområdet på `left = (time − 7) / 10 · 100 %` med `translateX(-50%)`. (Ikke bruk `padding-left` + flex-etiketter med gap — det gir feil tidsangivelse.)

Per rad:
- Venstre, 174 px: «tirsdag 12.» 15 px halvfet + «I dag»-merke
- Midten, `flex: 1`: 34 px høyt spor, 1 px ramme, radius 4. Åpne intervaller som absolutt posisjonerte segmenter i success-500 med klokkeslettet i hvitt 12 px inni. Stengte/advarende dager viser i stedet ikon + tekst venstrestilt i sporet
- Høyre, 200 px: **regelen som traff** — 14 px

Regelkolonnen er ukevisningens egentlige nytte for fagansvarlige: den avslører hvilken regel som styrer hver dag uten å åpne detaljpanelet sju ganger.

---

## Skjerm 4 — Sammenlign tjenester (`/sammenlign`)

Én dato, én rad per tjeneste, samme tidsakse. Svarer på «hvilke tjenester er åpne samtidig?» — for eksempel når et vedlikeholdsvindu skal legges.

Kontrollrad: «Dato» (`DatePicker`, 200 px), «Tjenester» (kombinasjonsfelt med `Chips` for valgte, `flex: 1`), `Button variant=secondary` «Velg hele et team».

Tidsakse med 224 px venstre avstandsholder, ingen høyre avstandsholder, samme absolutte posisjonering som ukevisningen.

Per rad: 224 px venstre kolonne med tjenestenavn 15 px halvfet + team 13 px dempet, deretter 34 px spor identisk med ukevisningen.

**Tjenester uten åpningstidsgruppe får advarselsraden**, ikke en tom akse, slik at «ikke satt opp» ikke forveksles med «stengt hele dagen».

### Mobil
Tidsaksen er ikke lesbar på 375 px. Visningen blir en liste per tjeneste med tekstlige klokkeslett.

---

## Skjerm 5 — Årsoversikt for røde dager (`/t/:id?visning=aar`)

Tolv minimåneder i `repeat(6, minmax(0, 1fr))` (to kolonner under 900 px), `gap: var(--ax-space-24)`. Hver måned: månedsnavn 15 px halvfet + `repeat(7, minmax(0, 1fr))` rutenett med 3 px gap. Dagceller er kvadratiske (`aspect-ratio: 1`), 11 px tekst, radius 2.

- **Helligdag** — bakgrunn danger-50, tekst danger-600, `font-weight: 700`, `border-bottom: 2px solid` danger-500
- **Søndag** — bakgrunn danger-50, ordinær vekt
- **Ordinær dag** — bakgrunn neutral-100

Formålet er kontroll: har regelsettet fanget alle røde dager i år? Visningen er en **inngang, ikke en kilde** — klikk på en dag går til måneden, der statusen står i tekst.

**Fargen står aldri alene:** helligdager er halvfete og understreket, og hver dag har navnet på dagen i `aria-label` og som synlig hjelpetekst ved fokus: «17. mai 2026 — grunnlovsdagen, rød dag».

Røde dager i eksempeldataene (2026): 1. januar, palmesøndag 29. mars, skjærtorsdag 2. april, langfredag 3. april, 1. påskedag 5. april, 2. påskedag 6. april, 1. mai, Kristi himmelfartsdag 14. mai, grunnlovsdagen 17. mai, 1. pinsedag 24. mai, 2. pinsedag 25. mai, 1. juledag 25. desember, 2. juledag 26. desember.

---

# Del 2 — administrasjon

## Skjerm 6 — Adminoversikt (`/admin`)

Svarer på «er noe galt?» før den tilbyr «hva vil du endre?» — derfor ligger avvikene øverst, ikke snarveiene.

Layout: sidemeny 210 px (Oversikt / Regler / Grupper / Tjenester, aktiv rad med 3 px aksentkant og `--ax-bg-accent-soft`) + innhold.

Innholdet, i rekkefølge:
1. `Heading` «Administrasjon» + «Innlogget som Kari Nordmann · team dagpenger»
2. `Alert variant=warning` som oppsummerer avvikene: «2 tjenester mangler åpningstidsgruppe, og 1 gruppe har en regel som aldri treffer.»
3. Tre nøkkeltallkort: 47 tjenester · 12 åpningstidsgrupper · 24 regler, 3 ikke i bruk — hver med lenke
4. «Trenger oppmerksomhet» — tabell med advarselsmerke, beskrivelse og handlingslenke per avvik
5. «Siste endringer» — 130 px tidskolonne, beskrivelse, initiator

Endringsloggen er **lesbar, ikke teknisk**: «Endret klokkeslett i «Ordinær åpningstid» fra 15:00 til 15:30», ikke en diff av regelstrengen. Full historikk ligger på hver regel og gruppe.

---

## Skjerm 7 — Regler, listevisning (`/admin/regler`)

Regeluttrykket står i tabellen, men i lesbar form: hver rad viser **både strengen og en setning som forklarer den**. Kolonnen «Brukt i» er den viktigste — den forteller om regelen er trygg å endre.

Topp: `Heading` «Regler» + «24 regler · en regel kan brukes i flere grupper», høyrestilt `Button` «Ny regel». Filterrad: søk i navn og uttrykk + `Select` «Flagg» (Alle regler / Rød dag / Kun for Nav-ansatte / Ikke i bruk).

Tabell, `1.4fr 2.2fr 1.1fr 1.2fr 0.7fr`:

| Navn | Regeluttrykk | Flagg | Brukt i | (handlinger) |
| --- | --- | --- | --- | --- |

- **Regeluttrykk** — strengen i fast bredde på `--ax-color-neutral-100`, med den forklarende setningen i 13 px under
- **Flagg** — `Tag` uten ikon: «Rød dag», «Kun for Nav-ansatte», «Ikke i bruk». Fargen bærer ingen mening alene her, så ikon er ikke nødvendig
- **Brukt i** — «2 grupper», eller «Ikke i bruk» i `--ax-color-warning-700` halvfet
- **Handlinger** — «Rediger» og «Slett» som lenker, stablet. Tekstlenker, ikke ikonknapper: ordet «Slett» er tryggere enn et ikon

Regler som ikke er i bruk merkes tydelig — det er den vanligste kilden til forvirring når noen endrer en åpningstid og ikke skjønner hvorfor endringen ikke vises.

---

## Skjerm 8 — Regel, skjema med veiviser (`/admin/regler/ny`)

Skjemaets vanskeligste oppgave er å skrive et gyldig regeluttrykk. **Løsningen er fire felt side om side som til sammen _er_ uttrykket:** du fyller ut felt, strengen skrives for deg, og strengen er samtidig redigerbar for den som kan syntaksen.

### Layout
Tokolonne: skjema `flex: 1` (min 460 px), sidepanel 340 px.

Skjemaet:
1. `TextField` «Navn på regelen», description «Brukes i grupper og i endringsloggen. Beskriv hva regelen gjør, ikke når den ble laget.»
2. **Veiviserkortet** (1 px ramme, radius 8, `padding: 22px`):
   - «Når gjelder regelen?» + «Fyll ut feltene du trenger. Et felt du lar stå tomt betyr «alle» og skrives som `*`.»
   - Fire felt i `repeat(4, minmax(0, 1fr))` med **`align-items: end`** — nødvendig fordi Aksel-feltene rendrer label → description → input, og descriptions med ulikt antall linjer ellers gir inputer på ulik høyde
     - «Dato», description «17.05.2026 eller 17.05 hvert år», placeholder «Alle»
     - «Dag i måneden», description «1, 15, siste», placeholder «Alle»
     - «Ukedag» (`Select`), description «Enkeltdag eller intervall»: Alle / man-fre / man-tor / fre / lør-søn
     - «Klokkeslett», description «08:00-15:30, flere med komma», placeholder «stengt»
   - **Regeluttrykk** — strengen i 15 px fast bredde, ramme skifter til danger-500 ved ugyldig. Under: kvitteringslinje med ikon («Uttrykket er gyldig» / feilmeldingen) og den forklarende setningen
   - **«Eller start fra et vanlig mønster»** — fem `Chips.Toggle`: ordinær arbeidsuke, arbeidsuke med lunsjpause, kortere fredag, stengt i helgen, stengt en bestemt dato
3. `TextField` «Overskrift til brukeren» (`displayHeader`, valgfri)
4. `Textarea` «Forklarende tekst» (`displayText`, valgfri), description «Skriv til brukeren i du-form, og si når tjenesten åpner igjen.»
5. Flaggkort med to `Checkbox`: «Rød dag — dagen markeres som helligdag i kalenderen», «Kun for Nav-ansatte — dagen skjules for uinnloggede»
6. `Button` «Lagre regelen» + `Button variant=tertiary` «Avbryt»

Sidepanelet:
- **Forhåndsvisning** — fire datochips (i dag, en fredag, en lørdag, 17. mai neste år), deretter et resultatfelt med status, klokkeslett og en setning som forklarer **hvorfor** regelen traff eller ikke traff
- **«Brukt i 2 grupper»** — lenker, med «En endring her slår ut i begge gruppene, og i alle tjenestene som bruker dem.»

### Toveis kobling
Feltene og strengen er samme data. Endrer du et felt, skrives strengen på nytt. Endrer du strengen, fylles feltene på nytt så lenge uttrykket kan tolkes. Kan det ikke tolkes, låses feltene med «Uttrykket er skrevet manuelt. Nullstill feltene for å redigere dem igjen» og en knapp som gjenoppretter koblingen.

### Validering
Skjer mens du skriver, med 300 ms forsinkelse, og aldri mens markøren står i feltet du fortsatt fyller ut. Gyldig uttrykk gir en grønn kvitteringslinje med den samme setningen som vises i regellisten. Ugyldig gir **én konkret feilmelding**, ikke en syntaksdump:

- «Klokkeslettet må skrives som 08:00-15:30.»
- «Sluttidspunktet må være etter starttidspunktet.»
- «To av intervallene overlapper. Del dem i to regler, eller slå dem sammen.»
- «17.05.2026 er en dato, ikke en ukedag. Flytt den til feltet Dato.»

### Live forhåndsvisning
Fire forhåndsvalgte datoer dekker de typiske tilfellene. Panelet viser status, klokkeslett og en setning som forklarer hvorfor regelen traff. **«Traff ikke» er en gyldig og nyttig visning**, ikke en tom tilstand — den forklarer hvilket felt som utelukket datoen.

Forhåndsvisningen viser regelen **alene**. Hva brukeren faktisk ser avhenger av rekkefølgen i gruppen — det testes på gruppesiden.

### Mønsterknappene
Fem vanlige mønstre fyller alle fire feltene med ett klikk. De erstatter ikke veiviseren, de gir den et startpunkt — for de fleste reglene er justering av et ferdig mønster nok.

### Lagring
Er regelen i bruk, viser lagringen en bekreftelse med **konsekvensen først**: «Regelen brukes i 2 grupper og påvirker 7 tjenester. Lagre endringen?» Ubrukte regler lagres uten mellomsteg. Etter lagring: `Alert` «Regelen er lagret» med lenke til gruppene den brukes i.

### Tastatur og skjermleser
Vanlig skjematabulering — ingen egen tastaturmodell. Kvitteringslinjen og valideringsfeilen ligger i et `aria-live="polite"`-område knyttet til uttrykksfeltet med `aria-describedby`. Forhåndsvisningen annonseres ved datobytte: «12. mai 2026: åpent 08:00 til 15:30».

### Mobil
Fire felt side om side blir fire felt i kolonne. Forhåndsvisningen flyttes under uttrykket, som et sammenleggbart panel som er åpent som standard.

---

## Skjerm 9 — Grupper, trestruktur (`/admin/grupper/:id`)

Rekkefølgen **er** prioriteten, så prioriteten skal være synlig som et tall — ikke bare underforstått av posisjonen.

### Layout
Tokolonne: tre `flex: 1` (min 480 px), testpanel 320 px.

Topp: tilbakelenke → `Heading` med gruppenavnet → «7 medlemmer · brukt av 7 tjenester» → `Alert variant=info size=small` «Første medlem som treffer datoen vinner. Flytt et medlem opp for å gi det høyere prioritet.»

Hver rad: dra-håndtak (22 px, `cursor: grab`) → prioritetsnummer (30 px, 700, `font-variant-numeric: tabular-nums`) → typepille «Regel»/«Gruppe» → navn 16 px halvfet + underlinje 13 px → «Opp» / «Ned» / «Fjern» som lenker. Grupperader får aksentfarget venstrekant, regelrader nøytral.

Under treet: `Button variant=secondary` «Legg til regel» og «Legg til undergruppe».

Testpanelet: tre datochips, deretter en sporingsliste som viser hvert medlem som ble vurdert med «Treffer ikke datoen» eller «Treffer — denne vinner», og til slutt «Resultat» med den effektive åpningstiden og hvilken prioritet som vant.

### Hierarkiet
Innrykk på 32 px per nivå, og en venstre 3 px kantstrek som markerer at raden hører til gruppen over. Undergrupper viser antall medlemmer i underlinjen og kan slås sammen. **Maksimalt tre nivåer vises utvidet;** dypere nivåer krever at du åpner undergruppen som egen side — et tre du ikke kan overskue er farligere enn ett klikk mer.

### Prioritetsnummeret
Fortløpende nummer i den rekkefølgen reglene evalueres, på tvers av nivåer — en regel inne i en undergruppe med nummer 2 får 2.1, 2.2. Nummeret er **ikke redigerbart**; det er en konsekvens av rekkefølgen, og skrives ut på nytt umiddelbart etter hver flytting.

### Omorganisering
Tre veier til samme handling: dra i håndtaket, «Opp»/«Ned»-lenkene i raden, eller tastatur. **Dra-og-slipp er aldri den eneste måten.** Under draing vises en innsettingslinje, og et medlem kan slippes inn i en undergruppe når du holder over den i 500 ms. Endringen lagres først når du trykker «Lagre rekkefølgen» — en utilsiktet dra skal ikke endre produksjon.

### Tastatur for flytting
Håndtaket er fokuserbart. Mellomrom løfter medlemmet («Løftet Ordinær åpningstid, prioritet 2 av 7»), piltastene flytter det, mellomrom slipper det, Escape avbryter og setter det tilbake. Ctrl + pil høyre gjør medlemmet til barn av raden over; Ctrl + pil venstre flytter det ut ett nivå.

### Skjermleser
Treet er en `<ol>` med nøstede lister, **ikke `role="tree"`** — det er en ordnet liste, og rekkefølgen er meningen. Hver rad annonserer «prioritet 2 av 7, regel, Ordinær åpningstid». Hver flytting bekreftes i et live-område: «Flyttet til prioritet 1 av 7».

### Legge til og fjerne
«Legg til regel» åpner en `Modal` med søkbar liste, der de som allerede er i gruppen er avkrysset og deaktivert. **Nye medlemmer legges nederst** — aldri øverst, som ville endret prioriteten til alt annet uten at du ba om det. «Fjern» tar medlemmet ut av gruppen, det sletter ikke regelen, og lenken sier nettopp «Fjern», ikke «Slett».

### Sirkelreferanse
En gruppe kan ikke inneholde seg selv, direkte eller indirekte. Slike valg er utilgjengelige i «Legg til undergruppe» med forklaringen «Kan ikke legges til: det ville laget en sirkel». Kommer konflikten fra API-et likevel: «Denne undergruppen inneholder allerede Selvbetjening ordinær. Velg en annen gruppe.»

### Regel som aldri treffer
Ligger en regel etter en bredere regel som alltid treffer først, merkes raden «Treffer aldri — regelen over dekker alle datoene denne dekker». Det er en **advarsel, ikke en feil**: rekkefølgen kan være tilsiktet. Advarselen vises også på adminoversikten.

---

## Skjerm 10 — Tjenester (`/admin/tjenester`)

Ordinær CRUD. Det ene som fortjener oppmerksomhet er koblingen til åpningstidsgruppe: den er tjenestens hele oppførsel i kalenderen.

### Listen
Tabell, `1.4fr minmax(88px, 0.8fr) 1.5fr minmax(64px, 0.6fr)` — de to minstebreddene hindrer at typepillen kolliderer med gruppekolonnen på smale vinduer.

| Tjeneste (+ team under) | Type | Åpningstidsgruppe | (rediger) |
| --- | --- | --- | --- |

**Tjenester uten gruppe sorteres øverst** og merkes «Ingen gruppe» i `--ax-color-warning-700` halvfet. Det er listens viktigste jobb: å gjøre hullene synlige uten at du må lete.

### Skjemaet
Bevisst kort — bare det som styrer kalenderen:
1. `TextField` «Navn»
2. `TextField` «Team»
3. **Åpningstidsgruppe-kortet** (aksentramme, `--ax-bg-accent-soft`): `Select` med label «Åpningstidsgruppe» og description «Gruppen bestemmer alt kalenderen viser for denne tjenesten. En tjeneste kan kobles til én gruppe.», pluss lenke «Se gruppens regler og rekkefølge»
4. `Alert variant=warning size=small`: «Velger du «Ingen gruppe», kan vi ikke vise åpningstider for Dagpengesøknad. Brukeren får en advarsel i kalenderen i stedet.»
5. `Button` «Lagre tjenesten», `Button variant=tertiary` «Avbryt», `Button variant=danger` «Slett tjenesten»

**Merk:** type, beskrivelse og lenkene til overvåkning og logger er tatt ut av skjemaet etter avklaring med designeier, men står fortsatt i datamodellen og i tjenestetabellen. Avklar om de skal forvaltes et annet sted eller fjernes fra modellen.

---

## Skjerm 11 — Sletting av noe som er i bruk

API-et svarer med konflikt (409) og en liste over hvor elementet brukes. Dialogen viser den listen **før** du bekrefter. **Er noe i bruk, er sletting sperret — ikke bare frarådet.**

### Dialog A — i bruk
- Advarselsikon + `Heading` «Regelen kan ikke slettes ennå»
- «"Ordinær åpningstid" brukes i 2 grupper, som til sammen brukes av 7 tjenester. Sletter du regelen, mister disse tjenestene åpningstidene sine.»
- **Konfliktlisten** — «Brukes her», med typepille (Gruppe / Tjeneste), navn som lenke og kontekst («Prioritet 4 av 7 · brukt av 5 tjenester»)
- «Fjern regelen fra gruppene først. Da kan du slette den uten at noe endrer seg for brukerne.»
- `Button` «Gå til gruppene» + `Button variant=tertiary` «Avbryt»
- **Det finnes ingen «slett likevel»-knapp i denne tilstanden.**

### Dialog B — ikke i bruk
- `Heading` «Vil du slette «Sommeråpningstid 2024»?»
- «Regelen er ikke i bruk i noen grupper. Ingen tjenester påvirkes.»
- «Slettingen kan ikke angres.»
- `Button variant=danger` «Slett regelen» + `Button variant=tertiary` «Avbryt»

### Dialogmønster
Overskriften stiller spørsmålet og navngir elementet. **Konsekvensen står i første avsnitt, ikke i knappeteksten.** Knappen sier hva som skjer — «Slett regelen», aldri «OK». Avbryt er tertiær og har fokus når dialogen åpnes.

### Tilgjengelighet
`Modal` med `aria-labelledby` på overskriften. Fokus fanges i dialogen, Escape avbryter, og fokus går tilbake til «Slett»-lenken i raden. Konfliktlisten er en vanlig `<ul>` og kan leses element for element.

### Sletting av gruppe
Samme mønster, men konfliktlisten viser både tjenester som er koblet til gruppen og foreldregrupper som inneholder den. Reglene i gruppen slettes **aldri** sammen med den — dialogen sier eksplisitt «De 7 reglene i gruppen blir ikke slettet».

### Etter sletting
Tilbake til listen med `Alert variant="success"`: «Regelen «Sommeråpningstid 2024» er slettet.» Ingen angremulighet loves, siden API-et ikke har det.

---

## Feilmeldinger fra API-et

Hver HTTP-tilstand oversettes til én setning som navngir problemet og én som sier hva du kan gjøre.

| Status | Situasjon | Tekst i grensesnittet |
| --- | --- | --- |
| 400 | Ugyldig regeluttrykk | «Klokkeslettet må skrives som 08:00-15:30.» Vises ved feltet, ikke som varsel på siden |
| 401 | Sesjonen er utløpt | «Du er ikke lenger innlogget. Logg inn på nytt for å lagre endringene.» Skjemaets innhold beholdes gjennom ny pålogging |
| 403 | Mangler tilgang | «Du har ikke tilgang til å endre regler for dette teamet. Kontakt teamets eier for tilgang.» |
| 404 | Finnes ikke | «Regelen finnes ikke lenger. Den kan ha blitt slettet av noen andre.» Med lenke tilbake til listen |
| 409 | I bruk, eller endret av andre | Ved sletting: konfliktdialogen over. Ved lagring: «Noen andre har endret denne regelen mens du redigerte. Last inn på nytt for å se den nyeste versjonen.» Endringene dine vises ved siden av |
| 422 | Sirkelreferanse i gruppetre | «Denne undergruppen inneholder allerede Selvbetjening ordinær. Velg en annen gruppe.» |
| 500 / 503 | Tjenesten svarer ikke | «Vi klarte ikke å lagre endringen. Prøv igjen om litt. Hvis det fortsetter, meld saken i #openinghours.» Med «Prøv igjen»-knapp og korrelasjons-ID under «Vis teknisk informasjon» |

Ingen av meldingene bruker ordene «ugyldig», «feilet» eller «systemfeil» alene, og ingen legger skylden på brukeren. Alle nevner en handling — prøv igjen, fjern først, last inn på nytt, meld saken.

**Teknisk melding og korrelasjons-ID** legges under `ReadMore` «Vis teknisk informasjon» — tilgjengelig når du skal melde saken videre, skjult ellers.

---

## Tilstander (gjelder alle skjermbilder)

### Lasting
Rutenettet beholder sin geometri som skjelett (`Skeleton`), så layouten ikke hopper. Tar kallet mer enn **600 ms**, legges `Loader` over med «Henter åpningstider». Under 600 ms vises ingenting — det unngår blinking.

### Tom tilstand
`Heading size=small` «Ingen tjenester matcher søket» → `BodyLong` «Prøv et kortere søkeord, eller nullstill filtrene for team og type.» → `Button variant=secondary` «Nullstill filtrene». Tomme tilstander navngir alltid årsaken — søk eller filter — og tilbyr veien tilbake. **Ingen illustrasjon.**

### Feiltilstand
`Alert variant=error`: «Vi klarte ikke å hente åpningstidene for mai 2026. Prøv igjen om litt. Hvis det fortsetter, meld saken til teamet som eier tjenesten.» Med `Button variant=secondary` «Prøv igjen». **API-ets tekniske melding vises aldri direkte.**

### Delvis data — tjeneste uten åpningstidsgruppe
`Alert variant=warning`: «Sykepengesøknad har ingen åpningstidsgruppe ennå. Vi kan derfor ikke si når tjenesten er åpen.»

Kalenderen vises fortsatt, med alle dagene i advarselstilstand — det er mer opplysende enn en tom side, og gjør omfanget av mangelen tydelig. `Button size=small` «Koble til en gruppe» finnes **bare for innloggede**.

### Uinnlogget vs. innlogget
| | Uinnlogget | Innlogget |
| --- | --- | --- |
| Tjenesteoversikt og kalender | Full bredde | Samme |
| Adminlenker i header | Rendres ikke | «Administrasjon» |
| Snarveier til overvåkning/logger | Rendres ikke | Per tjeneste, ny fane |
| Regelnavn og regeluttrykk i detaljpanel | Skjult | Vist, med lenke til regelen |
| Dager med `onlyShowForNavEmployees` | Skjult, faller tilbake på neste regel | Vist, merket «Intern» |
| Header-handling | `Button variant=secondary` «Logg inn som ansatt» | Navn + «Logg ut» |

Innloggingen gir ikke en annen kalender — den gir mer informasjon om samme kalender.

---

## Komponentliste med Aksel-mapping

### Del 1 — kalender og oversikt

| Element i designet | Aksel-komponent | Merknad |
| --- | --- | --- |
| Statusmerke i dagcellen | **Egen: Statusmerke** | `Tag` tar ikke ikon i prototypen, og statusen må ha ikon for å ikke hvile på farge. Sjekk om ekte `Tag` støtter ikon; ellers bygg av `Tag`-tokenene med et Aksel-ikon foran teksten |
| Tidsstrek med pauser | **Egen: Åpningsstrek** | Ingen motpart i Aksel. success-500 på neutral-100, samme geometri i celle, uke og sammenligning |
| Månedsrutenett | **Egen: Månedskalender** | `DatePicker` velger en dato — den viser ikke innhold per dag. Rutenettet må bygges, men arver `DatePicker`s tastaturmønster og fokusramme |
| Årsoversikt | **Egen: Årsstripe** | Tolv komprimerte månedsrutenett. Rene tokens; helligdager markeres med vekt og understrek i tillegg til bakgrunn |
| Tjenestetabell | `Table` | Sortering på navn og team. `ExpansionRow` brukes ikke — detaljene hører til kalendersiden |
| Visningsvelger måned/uke/år | `Tabs` | `Tabs`, ikke `ToggleGroup`: visningene er sidevisninger som skal ligge i URL-en |
| Søk og filtre | `Search`, `Select`, `Chips` | `Select` på desktop, `Chips.Toggle` i mobilens filtermeny |
| Detaljpanel for dagen | `Box`, `Heading`, `BodyLong` | Vanlig panel på desktop. På mobil `Modal` med bunnplassering |
| Varsler og feil | `Alert` | `warning` for manglende gruppe, `error` for feilede kall, `info` for «kun for Nav-ansatte» |
| Regeluttrykk | `CopyButton` + egen kodevisning | Fast bredde med kopiknapp. Aksel har ingen kodekomponent |
| Lasting | `Loader`, `Skeleton` | `Skeleton` beholder rutenettets geometri; `Loader` først etter 600 ms |
| Månedsnavigasjon | `Button` med Aksel-ikoner | `secondary` for forrige/neste, `tertiary` for «I dag» |
| Månedsvelger | `MonthPicker` | Dekker direktevalg av måned uten tilpasning |
| Header og innlogging | `InternalHeader` | For innloggede. Uinnlogget en enklere variant uten meny |
| Ikoner | `@navikt/aksel-icons` | `CheckmarkCircleIcon`, `MinusCircleIcon`, `ExclamationmarkTriangleIcon`, `ClockIcon`, `FlagIcon`, `ChevronLeftIcon`, `ChevronRightIcon` |

### Del 2 — administrasjon

| Element i designet | Aksel-komponent | Merknad |
| --- | --- | --- |
| Regelveiviser (fire felt + streng) | **Egen: Regelbygger** | Ingen motpart. Bygget av `TextField`, `Select` og en kodevisning, med toveis kobling mellom feltene og strengen |
| Gruppetre med dra-rekkefølge | **Egen: Prioritetsliste** | Nøstet `ol` med dra-håndtak, prioritetsnummer og tastaturflytting. Bruker Aksels fokusramme og spacing |
| Live forhåndsvisning | `Box`, `Heading`, `BodyLong` | Vanlig panel. Statusmerket er samme egendefinerte komponent som i kalenderen |
| Sidemeny i admin | `InternalHeader` + egen sidemeny | Aksel har ingen ferdig admin-sidemeny. Enkel liste med aktiv markering, bygget av tokens |
| Regel- og tjenestetabeller | `Table` | Radhandlinger som `Link`, ikke ikonknapper — teksten «Slett» er tryggere enn et ikon |
| Bekreftelsesdialoger | `Modal` | Fokusfelle og Escape følger med. Konfliktlisten er en `ul` inne i dialogen |
| Skjemafelt | `TextField`, `Textarea`, `Select`, `Checkbox` | Alle med `description` for hjelpetekst og `error` for validering. Ingen egne feltvarianter |
| Legg til medlem | `Modal` + `Search` + `Checkbox` | Søkbar liste. Medlemmer som allerede er i gruppen er avkrysset og deaktivert |
| Mønsterknapper | `Chips.Toggle` | Chips, ikke knapper: de setter en tilstand i skjemaet og er ikke handlinger med bivirkning |
| Flagg i regellisten | `Tag` | Uten ikon — flaggene er ikke status, og fargen bærer ingen mening alene |
| Varsler og kvitteringer | `Alert` | `success` etter lagring og sletting, `warning` for avvik, `error` for feilede kall, `info` for prioritetsforklaringen |
| Teknisk informasjon | `ReadMore` | Skjuler korrelasjons-ID og API-melding bak «Vis teknisk informasjon» |
| Endringslogg | `Table` eller enkel liste | Aksels `Timeline` er laget for datoperioder, ikke hendelser. Enkel liste er riktigere her |
| Destruktive handlinger | `Button variant="danger"` | Finnes i `@navikt/ds-react`, men ikke i prototypens bundel — der er de håndstylte. Bruk den ekte varianten |

Seks egne komponenter i alt. Alle bygges av Aksels tokens — ingen nye farger, ingen nye radier, ingen ny typografi.

---

## State management

| State | Hvor | Kilde |
| --- | --- | --- |
| `søk`, `team`, `type` | Tjenesteoversikt | URL query |
| `tjenesteId` | Kalender | URL path |
| `visning` (`maned` / `uke` / `aar`) | Kalender | URL query, standard `maned` |
| `måned` (år + måned) | Kalender | URL query, standard inneværende måned i `Europe/Oslo` |
| `valgtDato` | Kalender | URL query, standard i dag |
| `fokusertDato` | Rutenettet | Lokal state (roving tabindex) |
| `panelÅpent` | Detaljpanel | Lokal state; alltid åpent på desktop, `Modal` på mobil |
| `nå` | Nå-indikator | Timer, oppdateres hvert minutt |
| `regelfelt` + `regelstreng` | Regelskjema | Én kilde, toveis derivert (se skjerm 8) |
| `forhåndsvisningsdato` | Regelskjema | Lokal state |
| `medlemsrekkefølge` | Gruppetre | Lokal state til «Lagre rekkefølgen» trykkes |
| `testdato` | Gruppetre | Lokal state |
| `innlogget`, `bruker` | Global | Azure AD-sesjon |

**Datahenting:** tjenestelisten hentes én gang med «åpen nå»-status per tjeneste. Kalenderen henter dagsdata **for hele måneden i ett kall** per tjeneste (se åpne spørsmål). Sammenligningsvisningen henter én dato for N tjenester. Regelforhåndsvisningen evaluerer én regel mot én dato — avklar om API-et har et endepunkt for dette, eller om evalueringen må skje i frontend.

Alle datoer og klokkeslett beregnes i `Europe/Oslo`, uavhengig av klientens tidssone. Uke starter mandag. ISO-ukenummer der uke vises.

---

## Design tokens

Prototypen bruker rekonstruerte hexverdier. **Bruk `@navikt/ds-tokens` i produksjon** — listen under er for gjenkjennelse, ikke for innliming.

**Farger**
- Nøytral: `neutral-50 #f7f8fa`, `100 #eff1f3`, `200 #e2e6e9`, `500 #8c97a1`, `600 #68727b`, `700 #4d565e`, `800 #353c42`, `900 #23282d`
- Aksent: `accent-500 #0067c5`, `600 #0056b4`, `700 #004a93`, `900 #00243a`, `soft #e6f0ff`
- Success: `500 #06893a`, `700 #006128`, `soft #e6f5ec`
- Warning: `700 #6b4200`, `soft #fcf0ce`
- Danger: `500 #c30000`, `600 #a40000`, `soft #fbeaea`

**Semantiske tokens i bruk:** `--ax-bg-sunken` (sidebakgrunn), `--ax-bg-raised` (kort og celler), `--ax-border-neutral-subtle` (kortrammer), `--ax-border-neutral` (inputrammer), `--ax-text-neutral`, `--ax-text-neutral-subtle`, `--ax-text-accent`, `--ax-border-focus`, `--ax-shadow-dialog`, samt `--ax-bg-*-soft` / `--ax-text-*` / `--ax-border-*-subtle` for de fire statusrollene.

**Typografi** — Source Sans 3, kun 400 og 600.
Sidetittel 52 px / 1.1 · seksjonstittel 32 px / 1.2 · skjermtittel 30 px / 1.2 · panelttittel 24 px / 1.25 · ingress 18–21 px / 1.5–1.6 · brødtekst 15–17 px / 1.6 · dagnummer 17 px / 600 · klokkeslett i celle 13 px / 600 · underlinje i celle 12 px / 1.35 · tabellhode 13 px / 600 · label 12 px / 600 uppercase, `letter-spacing: 0.05em` · minimånedsdag 11 px

**Spacing** — Aksels 4-baserte skala. I bruk: 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 28, 32, 40, 56, 72.

**Radius** — 8 px kort og knapper, 4 px input, celler, statusmerker og varsler, 2 px minimånedsdager, `full` på piller.

**Skygge** — ingen på flate flater. `--ax-shadow-dialog` kun på `Modal` og popover.

**Fokus** — 3 px `--ax-border-focus` med 1 px offset på alt fokuserbart. Ikke fjern eller dempe den.

**Breakpoints i bruk** — 640 px (tabell → kort), 720 px (rutenett → agendaliste), 900 px (årsoversikt 6 → 2 kolonner), 1000 px (admin tokolonne → én), 1100 px (detaljpanel under rutenettet).

---

## Assets

Ingen bilder eller illustrasjoner. Prototypens ikoner er inline SVG-er håndtilpasset Aksels stil fordi `@navikt/aksel-icons` ikke var tilgjengelig i prototypemiljøet — **erstatt dem med npm-pakken**, se mappingtabellene. Nav-logoen i headeren er en plassholder («N» i en firkant); bruk `InternalHeader` med riktig logo.

---

## Files

- **`OpeningHours designforslag.dc.html`** — del 1. Navigasjonsstruktur, statussystem med kontrasttall, tjenesteoversikt (desktop + mobil), månedskalender med detaljpanel, ukevisning, sammenligning, årsoversikt, tilstander, komponentmapping. Spesifikasjonsseksjon per skjerm.
  Interaktivt: klikk på en dag i månedskalenderen oppdaterer detaljpanelet. Bryteren øverst i tjenesteoversikten veksler innlogget/uinnlogget gjennom hele dokumentet.

- **`OpeningHours admin.dc.html`** — del 2. Adminoversikt, regelliste, regelskjema med veiviser, gruppetre, tjenester, slettedialoger, feilmeldingstabell, komponentmapping.
  Interaktivt: **regelveiviseren** — fyll ut de fire feltene og se strengen skrives, valideringen slå inn og forhåndsvisningen svare for den valgte datoen; mønsterknappene fyller alle fire feltene; tomt klokkeslettfelt gir «stengt». **Gruppetreet** — velg en dato i høyre panel og se hvilket medlem som vinner, med begrunnelse per steg.

Begge filene åpnes direkte i nettleser og krever mappen `_ds/aksel-design-system-nav-71e0d16a-00d1-4188-92e4-1c83abdaef41/` fra prosjektet for stiler og komponenter. Er den ikke med, mister sidene Aksel-stilene, men strukturen og all tekst er fortsatt lesbar.

---

## Åpne spørsmål som må avklares før implementasjon

1. **Støtter regeluttrykket flere intervaller i én regel (komma-separert), eller må lunsjpausen uttrykkes som to regler?** Designet forutsetter det første, både i kalendercellen og i veiviserens klokkeslettfelt. Er det ikke støttet, må veiviseren skrive to regler og gruppen holde dem sammen — det endrer skjemaet betydelig.
2. **Er regelsyntaksen i eksempeldataene riktig?** De fire feltene (dato, dag-i-måned, ukedag, klokkeslett) er utledet fra oppgavebeskrivelsen, ikke fra API-dokumentasjon. Veiviserens hele struktur hviler på at feltene stemmer.
3. **Finnes et endepunkt som evaluerer én regel mot én dato?** Den live forhåndsvisningen trenger det. Uten det må regelmotoren dupliseres i frontend, som er en kilde til avvik mellom det du ser og det brukeren får.
4. **Kommer røde dager fra en helligdagskilde, eller må hver dato legges inn manuelt per år?** Årsoversikten er bygget for å avdekke hull, men manuelt vedlikehold av 13 datoer per år bør heller løses med en generert regel.
5. **Returnerer API-et dagsdata for et helt månedsintervall i ett kall, eller én dato per kall?** Månedsvisningen forutsetter ett kall per måned.
6. **Skal en tjeneste kunne arve gruppen fra en annen tjeneste, eller er koblingen alltid direkte?** Avgjør om tjenesteskjemaet trenger et arvebegrep.
7. **Hvor dypt kan gruppetreet være i praksis?** Designet viser tre nivåer utvidet og krever navigasjon dypere. Er fem nivåer vanlig, trenger treet en annen visning.
8. **Har API-et optimistisk låsing (ETag eller versjonsnummer)?** 409-håndteringen ved samtidig redigering forutsetter at frontend kan oppdage at noen andre har endret elementet.
