# sokos-skattekort

Sokos-skattekort er en erstatning for os-eskatt, som brukte altinn 2 til å hente skattekort. I løpet av høsten 2025 vil skatteetaten tilby et nytt grensesnitt, separat fra altinn, for å
tilby samme funksjonalitet.

## Funksjonell workflow

```mermaid
flowchart TD
    Start --> ArenaBestilling
    Start --> OppdragZBestilling
    Start --> PocBestilling
    ArenaBestilling -- JMS - bestilling i XML - format --> SkattekortbestillingsService
    OppdragZBestilling -- JMS - bestilling i copybook - format --> SkattekortbestillingsService
    PocBestilling -- JMS - bestilling i copybook - format --> SkattekortbestillingsService
    SkattekortbestillingsService -- bestilling --> BestDb[(BestDb)]
    SkattekortbestillingsService -- systeminteresse --> person[(person)]
    BestDb -- Samler opp og batcher bestillinger --> Bestiller
    Bestiller -- Lagrer bestillingsreferanse --> BestDb
    Bestiller -- eksternt kall --> Skatt
    Bestiller -- teknisk status --> Micrometer
    BestDb --> Henter
    Henter -- eksternt kall --> Skatt
    Henter -- lagrer bevisdata --> KortDb[(KortDb)]
    Henter -- teknisk status --> Micrometer
    KortDb -- feil fra skatt --> AdminGui
    KortDb -- ok skattekort --> Sender
    Sender --> SKDb[(SkatteKortDb)]
    person -- systeminteresse --> Sender
    Sender -- JMS --> OppdragZ
    Sender -- hvis poc - interesse, JMS? Rest? --> POC
    SkatteKortDb --> AdminGui
    BestDb --> AdminGui
    person --> AdminGui
```

## Workflows

1. [Deploy alerts](.github/workflows/alerts.yaml) -> For å pushe alarmer for dev og prod
    1. Denne workflow trigges bare hvis det gjøres endringer i [alerts-dev.yaml](.nais/alerts-dev.yaml) og [alerts-prod.yaml](.nais/alerts-prod.yaml)
2. [Deploy application](.github/workflows/deploy.yaml) -> For å bygge/teste prosjektet, bygge/pushe Docker image og deploy til dev og prod
    1. Denne workflow trigges når kode pushes i `main` branch
3. [Build/test PR](.github/workflows/build-pr.yaml) -> For å bygge og teste alle PR som blir opprettet og gjør en sjekk på branch prefix og title
    1. Denne workflow kjøres kun når det opprettes pull requester
4. [Security](.github/workflows/codeql-trivy-scan.yaml) -> For å skanne kode og docker image for sårbarheter. Kjøres hver morgen kl 06:00
    1. Denne kjøres når [Deploy application](.github/workflows/deploy.yaml) har kjørt ferdig
5. [Deploy application manual](.github/workflows/manual-deploy.yaml) -> For å deploye applikasjonen manuelt til ulike miljøer
    1. Denne workflow trigges manuelt basert på branch og miljø

## Bygge og kjøre prosjekt

1. Bygg prosjektet ved å kjøre `./gradlew clean build shadowJar`
2. Start appen lokalt ved å kjøre main metoden i ***Application.kt***
3. For å kjøre tester i IntelliJ IDEA trenger du [Kotest IntelliJ Plugin](https://plugins.jetbrains.com/plugin/14080-kotest)

## Utviklingsmiljø

### Forutsetninger

* Java 21
* [Gradle >= 8.9](https://gradle.org/)
* [Kotest IntelliJ Plugin](https://plugins.jetbrains.com/plugin/14080-kotest)

### Bygge prosjekt

1. Bygg prosjektet ved å kjøre `./gradlew clean build shadowJar`

### Lokal utvikling

2. Start appen lokalt ved å kjøre main metoden i ***Application.kt***
3. For å kjøre tester i IntelliJ IDEA trenger du [Kotest IntelliJ Plugin](https://plugins.jetbrains.com/plugin/14080-kotest)

### Patching av biblioteker

Vi har ikke testdekning på IBM MQ-bibliotekene (gruppe "com.ibm.mq" i build.gradle.kts) fordi vi kjører activemq i stedet for ibm mq i test-modus.
Vi må teste oppgradering av dette/disse biblitekene manuelt.

## Programvarearkitektur

### Oversikt

```mermaid
block-beta
    columns 7
    space space hent("hent-skattekort") space space space space
    space space bestilling space space space space
    space space space space applikasjon space space
    space space space space space space OS("OppdragZ")
    space space space space db[("Database")] space space
    Arena_inn --> bestilling
    OS_inn --> bestilling
    space bestilling --> applikasjon
    space applikasjon --> Arena
    space applikasjon --> OS
    space applikasjon --> db
    hent --> applikasjon

```

Applikasjonen integrerer også med drifts- og observabilitetsverktøy.

### Interne grensesnitt

Ingen

### Versjonerte grensesnitt

| Funksjon                         | Type      | Navn QA                           | Nåværende versjon | Kanal for funksjonelle ønsker | Kanal for varslinger om versjoner          | Kanal for drifts- eller utviklingsrelatert kommunikasjon |
|----------------------------------|-----------|-----------------------------------|-------------------|-------------------------------|--------------------------------------------|----------------------------------------------------------|
| bestillinger fra arena, OppdragZ | MQ        | QA.Q1_OS_ESKATT.FRA_FORSYSTEM_ALT | TBD               | #utbetaling                   | #utbetaling-sokos-skattekort-announcements | #utbetaling-sokos-skattekort                             |
| "store bestillinger" (ved nyttår | MQ        |                                   | TBD               | #utbetaling-sokos-skattekort  | #utbetaling-sokos-skattekort-announcements | #utbetaling-sokos-skattekort                             |
| Arena                            | Filområde |                                   | TBD               | #utbetaling                   | #utbetaling-sokos-skattekort-announcements | #utbetaling-sokos-skattekort                             |
| Skattekort til OppdragZ          | MQ        | QA.Q1_231.OB04_FRA_OS_ESKATT      | TBD               | #utbetaling-sokos-skattekort  | #utbetaling-sokos-skattekort-announcements | #utbetaling-sokos-skattekort                             |
| Hent skattekort (salesforce)     | Rest      |                                   | V1                | #utbetaling                   | #utbetaling-sokos-skattekort-announcements | #utbetaling-sokos-skattekort                             |

Swagger

- [Dev-fss](https://sokos-skattekort.intern.dev.nav.no/api/v1/skattekort/docs)
- [Lokalt](http://0.0.0.0:8080/api/v1/skattekort/docs)

### Maskinporten og systembrukere

Systembrukere er objekter som eies på NAV-nivå, og føringer/ideer fra NAV sentralt har fått oss til å håndtere systembrukere
som [delt konfigurasjon](https://confluence.adeo.no/x/Av8ML) i seksjon utbetaling.

### Statemaskin for bestillinger

#### bestilling

```mermaid
stateDiagram-v2
    [*] --> ny
    ny --> bestilt
    bestilt --> ?
    note right of ?: Avhenger av design på nytt API
    ? --> mottatt
    note right of mottatt: Bestillingen kan slettes etter at skattekort er mottatt ok
```

### Databaseskjema

#### Person og bestilling

```mermaid
erDiagram
    person ||--o{ person_fnr: har
    person ||--o{ person_audit: har
    person ||--o{ bestillinger: har
    bestillinger_batch |o--o{ bestillinger: inneholder
    person {
    }
    person_fnr {
        text fnr "fnr/pid/dnr etc"
        date gjelder_fom "Når byttet aktøren fra det andre til dette(?)"
    }
    person_audit {
        text tag "Maskinlesbar kategorisering av auditlinjer, for f.eks. å kunne finne alle bestillinger"
        text informasjon "Menneskelesbar tekst som beskriver endringen"
        text bruker "Bruker eller system som forårsaket endringen"
    }
    bestillinger {
        smallint aar
        text fnr "Fødselsnummer brukt i bestilling."
    }
    bestillinger_batch {
        text status "Tracker status på bestilling mot skatteetaten"
        text bestillingreferanse "Referanse mottatt fra skatteetaten ved registrert bestilling"
        text dialogreferanse "Referanse mottatt fra skatteetaten, til dialogporten"
    }
```

Designnotater:

- vi kan få skattekort uten skattekort-deler. Det gjelder typisk personer som har tilleggsopplysninger.
- tanken bak skattekort-tabellen er at den er immuterbar

## Deployment

Distribusjon av tjenesten er gjort med bruk av Github Actions.
[sokos-skattekort CI / CD](https://github.com/navikt/sokos-skattekort/actions)

Push/merge til main branch vil teste, bygge og deploye til produksjonsmiljø og testmiljø.

## Autentisering

Applikasjonen bruker [AzureAD](https://docs.nais.io/security/auth/azure-ad/) autentisering

## Drift og støtte

Applikasjonen driftes av utviklerteamet under en devops-modell.

Applikasjonen kjører onprem.

### Logging

https://logs.adeo.no.

Feilmeldinger og infomeldinger som ikke innheholder sensitive data logges til data view `Applikasjonslogger`.  
Sensetive meldinger logges til data view `Securelogs` [sikker-utvikling/logging](https://sikkerhet.nav.no/docs/sikker-utvikling/logging)).

- Filter for Produksjon
    * application:sokos-skattekort AND envclass:p

- Filter for Dev
    * application:sokos-skattekort AND envclass:q

### Kubectl

TBD

### Alarmer

Vi bruker [nais-alerts](https://doc.nais.io/observability/alerts) for å sette opp alarmer.
Disse finner man konfigurert i [.nais/alerts-dev.yaml](.nais/alerts-dev.yaml) filen og [.nais/alerts-prod.yaml](.nais/alerts-prod.yaml)

### Grafana

---

## Henvendelser og tilgang

- Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på github.
- Funksjonelle interne henvendelser kan sendes via Slack i kanalen [#utbetaling](https://nav-it.slack.com/archives/CKZADNFBP)
- Utvikler-til-utviklerkontakt internt i NAV skjer på Slack i kanalen TBD

## Prosess 1: Motta forespørsler og opprette Personer, Abonnementer, Bestillinger og Utsendinger

```mermaid
flowchart TD
    A[Mottatt forespørsel på kø] --> F(Lagre Forespørsel)
    A --> P(Hent eller opprett Person for hvert fnr)
    A --> SF(Opprett Abonnement for hvert fnr)
    SF -->|Mangler skattekort| B(Opprett Bestilling for fnr)
    SF --> U(Opprett Utsending for Fnr til gitt forsystem)
```

## Prosess 2: Bestille skattekort fra skatteetaten

```mermaid
flowchart TD
    B["Plukk ut n Bestillinger (unike på fnr/inntektsår)"] --> BB(Opprett Bestillingsbatch og få bestillingsreferanse fra SKD) --> OB(Oppdater Bestillinger med Bestillingsbatchid)
```

## Prosess 3: Hent skattekort fra skatteetaten

```mermaid
flowchart TD
    BB[Ta tak i en passende Bestillingsbatch] --> HS(Kall HentSkattekort hos Skatteetaten for aktuell bestillingsreferanse)
    HS -->|For hver Bestilling| SK{Har vi fått skattekort?} -->|Ja| L(Lagre Skattekort i databasen) --> SLETT(Slett bestillinger som vi har fått skattekort for)
    SK -->|Nei| RESET(Slett bestillingsbatchid fra bestilling)
    SK -->|Feil FNR| FLAGG(Flagg FNR) --> SKRIK(Rop høyt et sted så noen hører) --> SLETT2(Slett Bestillinger og Abonnementer som feilet)
```

## Prosess 4: Send skattekort til Forsystem

```mermaid
flowchart TD
    U(Hent utsendinger som skal til Forsystem) -->|For hvert unike fnr| S(Hent de skattekortene vi har)
    S --> SO(Send skattekort til Forsystem) --> SLETT(Slett Utsendinger som vi har sendt Skattekort for)
```

## Prosess 5: Motta oppdaterte skattekort

```mermaid
flowchart TD
    SKD(Sjekk om SKD har oppdatert noen skattekort) --> L(Lagre Skattekort i databasen)
    L --> A(Sjekk hvilke Abonnementer som finnes for Fnr)
    A --> U(Opprett Utsending til alle forsystemer som abonnerer på fnr)
```

## Prosess 7: Slette gamle data

1. Delete from skattekort where inntektsaar < currentYear - 1
2. Delete from abonnementer where inntektsaar < currentYear - 1
3. Delete from person where not exists (select 1 from abonnementer where abonnementer.fnr = person.fnr)
4. etc

## Prosess 8: Sjekk bestillingsstatus for FNR og inntektsår

```mermaid
flowchart TD
    S{Finnes Skattekort?} -->|Nei| A{Finnes Abonnement} -->|Ja| BB{Finnes bestillingsbatch} -->|Ja| S1[Status: Venter på svar fra Skatteetaten]
    S -->|Ja| S2[Status: Har Skattekort]
    A -->|Nei| S3[Status: Aldri forespurt]
    BB -->|Nei| B{Finnes Bestilling?} -->|Ja| S5[Status: Venter på at Batchtoget skal gå]
    B -->|Nei| S4["Feil som må håndteres: 
                    Vi har et abonnnement, men har ikke skattekort
og heller ikke planlagt å bestille det"]

```

## Relasjonsmodell

```mermaid
erDiagram
    Forespoersler ||--|{ Abonnementer: ""
    Personer ||--|{ Skattekort: ""
    Foedselsnummer }|--|| Personer: ""
    Bestillinger |{--o| Bestillingsbatcher: ""
    Abonnementer ||--o| Utsendinger: ""
    Personer ||--|{ Abonnementer: ""
    Forespoersler {
        string request
        string forsystem
    }

    Foedselsnummer {
        string fnr UK
        date gjelder_fom
    }

    Abonnementer {
        string fnr
    }

    Skattekort {
    }

    Bestillinger {
        string fnr UK
        int inntektsaar
    }

    Bestillingsbatcher {
        string bestillingsreferanse UK
        int inntektsaar
    }

    Utsendinger {
        string fnr
        string forsystem
        int inntektsaar
    }
```

#### Skattekort (skisse)

```mermaid
erDiagram
    person ||--o{ skattekort: har
    person ||--o{ skattekort_data: har
    skattekort ||--o{ forskuddstrekk: har
    skattekort ||--o{ skattekort_tilleggsopplysninger: har
    skattekort {
        smallint inntektsaar
        date utstedt_dato "Felt satt av skatteetaten"
        text identifikator "Skatteetatens id for skattekortet"
        text kilde "Angir kilde for skattekortet: skatt, syntetisert, manuelt"
        timestamptz opprettet "Vår egen dato for oppretting av skattekortet"
    }
    forskuddstrekk {
        text trekk_kode "PENSJON, PENSJON_FRA_NAV, etc. Satt av skatteetaten, angir bruksområde for forskuddstrekken"
        text type "frikort, tabell, prosent"
        int frikort_beloep "Beløpsgrense for frikort, eller null dersom ikke frikort eller ingen grense"
        text tabell_nummer "Tabellangivelse. Tabeller oppdateres en gang pr år"
        decimal prosentsats "Prosentsats, kan være en fraksjonell prosent for kildeskatt"
        decimal antall_mnd_for_trekk "Antall måneder trekk skal utføres for, typisk 12, 10.5"
    }
    skattekort_tilleggsopplysninger {
        text opplysning "kildeskattPaaPensjon, kildeskattPaaLoenn etc"
    }
    skattekort_data {
        timestamptz created
        text data_mottatt "Payload mottatt, uten behandling. For debuggingsformål"
        smallint aar
        timestamptz opprettet
    }

```