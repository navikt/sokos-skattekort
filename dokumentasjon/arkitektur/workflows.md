## Funksjonell workflow

```mermaid
flowchart LR
    Start --> OppdragZBestilling
    Start --> PocBestilling
    OppdragZBestilling -- JMS - bestilling i copybook - format --> SkattekortbestillingsService
    PocBestilling -- Kaller REST-endepunkt --> SkattekortbestillingsService
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
4. [Security](.github/workflows/codeql-scan.yaml) -> For å skanne kode for sårbarheter. Kjøres hver morgen kl 06:00
    1. Denne kjøres når [Deploy application](.github/workflows/deploy.yaml) har kjørt ferdig
5. [Deploy application manual](.github/workflows/manual-deploy.yaml) -> For å deploye applikasjonen manuelt til ulike miljøer
    1. Denne workflow trigges manuelt basert på branch og miljø

## Prosess 1: Motta forespørsler og opprette Personer, Abonnementer, Bestillinger og Utsendinger

```mermaid
flowchart LR
    Start(Start) --> FNR{Finnes \nperson?}
    FNR -->|Nei| OPPRETT_PERSONID --> OPPRETT_ABONNEMENT
    FNR -->|Ja| OPPRETT_ABONNEMENT --> SJEKK_SKATTEKORT{Har Skattekort?}
    SJEKK_SKATTEKORT -->|Ja| OPPRETT_UTSENDING
    SJEKK_SKATTEKORT -->|Nei| FNR_KAN_BESTILLE{FNR kan bestille \nfra Skatteetaten?}
    FNR_KAN_BESTILLE --> |Nei| VENT_PAA_MANUELT_SKATTEKORT
    FNR_KAN_BESTILLE --> |Ja| OPPRETT_BESTILLING
```

## Prosess 2: Bestille skattekort fra skatteetaten

```mermaid
flowchart TD
    B["Plukk ut n Bestillinger (unike på fnr/inntektsår)"] --> BB(Opprett Bestillingsbatch og få bestillingsreferanse fra SKD) --> OB(Oppdater Bestillinger med Bestillingsbatchid)
```

## Prosess 2.5: Bestill oppdateringer

```mermaid
flowchart TD
    A[Sjekk om det finnes en oppdateringsbatch som er NY, RERUN eller FEILET] -->|Hvis nei| B[Bestill oppdateringer]
```

## Prosess 3: Hent skattekort fra skatteetaten

```mermaid
flowchart LR
    BB[Ta tak i en Bestillingsbatch med status NY] --> HS(Kall HentSkattekort hos Skatteetaten) -->
    SVAR{Responskode} -->|200 OK| OK -->|For hvert mottatte skattekort| L(Lagre Skattekort i skattekortdata)
    SVAR -->|Andre| FEIL(Feil som må undersøkes)
```

## Prosess 3.5: Ta Skattekortdata og lag skattekort
```mermaid
flowchart TD
    B["For hver skattekortdata"] --> BB(Opprett skattekort) --> OB(Opprett utsendinger til alle abonnenter)
```

## Prosess 4: Send skattekort til Forsystem

```mermaid
flowchart TD
    U(Hent utsendinger) --> S(Hent skattekortene som vi vet vi har)
    S --> SO(Send skattekort forsystemet) --> SLETT(Slett Utsendinger som vi har sendt Skattekort for)
```

## Prosess 5: Motta oppdaterte skattekort

```mermaid
flowchart TD
    SKD(Sjekk om Skatteetaten har oppdatert noen skattekort) --> L(Lagre Skattekortdata)
```

## Prosess 7: Slette gamle data(Ikke laget ennå!)

1. Delete from skattekort where inntektsaar < currentYear - 1
2. Delete from abonnementer where inntektsaar < currentYear - 1
3. Delete from person where not exists (select 1 from abonnementer where abonnementer.fnr = person.fnr)
4. etc

## Sjekk bestillingsstatus for FNR og inntektsår

```mermaid
flowchart LR
    Start(Start) --> FNR{Finnes \nperson?}
    FNR -->|Nei| IKKE_FORESPURT[IKKE_FORESPURT]
    FNR -->|Ja| SJEKK_SKATTEKORT{Har Skattekort}
    SJEKK_SKATTEKORT -->|Ja| SJEKK_UTSENDING{Finnes Utsending?}
    SJEKK_UTSENDING -->|Nei| SJEKK_ABONNEMENT{Finnes Abonnement?}
    SJEKK_UTSENDING -->|Ja| VENTER_UTSENDING
    SJEKK_ABONNEMENT{Finnes Abonnement?} -->|Nei| IKKE_ABONNENT
    SJEKK_ABONNEMENT -->|Ja| ABONNERER
    SJEKK_SKATTEKORT -->|Nei| SJEKK_BESTILLING{Finnes bestilling?}
    SJEKK_BESTILLING -->|Ja| SJEKK_BATCH{Finnes batch?}
    SJEKK_BATCH -->|Ja| BESTILT
    SJEKK_BATCH -->|Nei| IKKE_BESTILT
    SJEKK_BESTILLING -->|Nei| SJEKK_ABONNEMENT
```
