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

