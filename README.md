# sokos-skattekort

Sokos-skattekort er en erstatning for os-eskatt, som brukte altinn 2 til å hente skattekort. I løpet av høsten 2025 vil skatteetaten tilby et nytt grensesnitt, separat fra altinn, for å 
tilby samme funksjonalitet.

## Funksjonell workflow

```mermaid
flowchart TD
    Start --> ArenaBestilling
    Start --> OppdragZBestilling
    Start --> PocBestilling
    ArenaBestilling -- JMS-bestilling i XML-format --> SkattekortbestillingsService
    OppdragZBestilling -- JMS-bestilling i copybook-format --> SkattekortbestillingsService
    PocBestilling -- JMS-bestilling i copybook-format --> SkattekortbestillingsService
    SkattekortbestillingsService -- bestilling --> BestDb[(BestDb)]
    SkattekortbestillingsService -- systeminteresse --> Aktoer[(Aktoer)]
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
    Aktoer -- systeminteresse --> Sender
    Sender -- hvis arena-interesse, SFTP --> Arena
    Sender -- alltid, JMS --> OppdragZ
    Sender -- hvis poc-interesse, JMS? Rest? --> POC
    SkatteKortDb --> AdminGui
    BestDb --> AdminGui
    Aktoer --> AdminGui
```

## Workflows

1. [Deploy alerts](.github/workflows/alerts.yaml) -> For å pushe alarmer for dev og prod
   1. Denne workflow trigges bare hvis det gjøres endringer i [alerts-dev.yaml](.nais/alerts-dev.yaml) og [alerts-prod.yaml](.nais/alerts-prod.yaml)
2. [Deploy application](.github/workflows/deploy.yaml) -> For å bygge/teste prosjektet, bygge/pushe Docker image og deploy til dev og prod
   1. Denne workflow trigges når kode pushes i `main` branch
3. [Build/test PR](.github/workflows/build-pr.yaml) -> For å bygge og teste alle PR som blir opprettet og gjør en sjekk på branch prefix og title
   1. Denne workflow kjøres kun når det opprettes pull requester
4. [Security](.github/workflows/security.yaml) -> For å skanne kode og docker image for sårbarheter. Kjøres hver morgen kl 06:00
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
    space              space        hent("hent-skattekort") space space             space space
    Arena_inn("Arena") space        space      space space             space space
    space              space        bestilling space space             space space 
    OS_inn("OppdragZ") space        space      space space             space Arena("Arena (SFTP)")
    space              space        space      space applikasjon       space space 
    space              space        space      space space             space OS("OppdragZ")
    space              space        space      space db[("Database")]  space space 
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

| Funksjon       | Type      | Nåværende versjon | Kanal for funksjonelle ønsker | Kanal for varslinger om versjoner        | Kanal for drifts- eller utviklingsrelatert kommunikasjon |
|----------------|-----------|-------------------|-------------------------------|------------------------------------------|----------------------------------------------------------|
| bestillinger   | MQ        | TBD               | #utbetaling                   | #utbetaling-sokos-skattekort-announcements | #utbetaling-sokos-skattekort                               |
| Arena          | Filområde | TBD               | #utbetaling                   | #utbetaling-sokos-skattekort-announcements | #utbetaling-sokos-skattekort                               |
| OppdragZ       | MQ        | TBD               | #utbetaling                   | #utbetaling-sokos-skattekort-announcements | #utbetaling-sokos-skattekort                               |

TBD Hva er url til swagger i Lokal, dev og prod? Dok for grensesnitt.

### Statemaskin for bestillinger

#### bestilling
```mermaid
stateDiagram-v2
    [*]-->ny
    ny-->bestilt
    bestilt-->?
    note right of ?: Avhenger av design på nytt API
    ?-->mottatt
    note right of mottatt: Bestillingen kan slettes etter at skattekort er mottatt ok
```

### Databaseskjema

#### Aktør og bestilling

```mermaid
erDiagram
    aktoer ||--o{ aktoer_offnr: har
    aktoer ||--o{ aktoer_audit: har
    aktoer ||--o{ aktoer_bestiltfra: har
    aktoer ||--o{ bestillinger: har
    bestillinger_batch |o--o{ bestillinger: inneholder
    aktoer{
    }
    aktoer_audit{
        text tag "Maskinlesbar kategorisering av auditlinjer, for f.eks. å kunne finne alle bestillinger"
        text informasjon "Menneskelesbar tekst som beskriver endringen"
        text bruker "Bruker eller system som forårsaket endringen"
    }
    aktoer_bestiltfra {
        smallint aar "Årstall for bestilling"
        text     bestiller "Oppdragz, arena, .... Brukes til å trigge avlevering oå riktig format"
    }
    bestillinger {
        smallint aar
        text     fnr "Fødselsnummer brukt i bestilling."
    }
    bestillinger_batch {
        text    status "Tracker status på bestilling mot skatteetaten"
        text    bestillingreferanse "Referanse mottatt fra skatteetaten ved registrert bestilling"
        text    dialogreferanse "Referanse mottatt fra skatteetaten, til dialogporten"
    }
```

#### Skattekort (skisse)

```mermaid
erDiagram
    aktoer ||--o{ skattekort: har
    aktoer ||--o{ skattekort_raw: har
    skattekort {
        smallint aar 
    }
    skattekort_raw {
        timestamptz  created
        text         body "Payload mottatt, uten behandling. For debuggingsformål"
    }
    TBD
```


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
- [appavn](url)
---

## Henvendelser og tilgang
- Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på github.
- Funksjonelle interne henvendelser kan sendes via Slack i kanalen [#utbetaling](https://nav-it.slack.com/archives/CKZADNFBP)
- Utvikler-til-utviklerkontakt internt i NAV skjer på Slack i kanalen TBD
