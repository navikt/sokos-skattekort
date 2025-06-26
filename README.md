# sokos-lavendel

Sokos-lavendel er en erstatning for os-eskatt, som brukte altinn 2 til å hente skattekort. I løpet av høsten 2025 vil skatteetaten tilby et nytt grensesnitt, separat fra altinn, for å 
tilby samme funksjonalitet.

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

## Programvarearkitektur
Legg ved skissediagram for hvordan arkitekturen er bygget

### Oversikt

### Interne grensesnitt
TBD Hva er url til swagger i Lokal, dev og prod?

### Versjonerte grensesnitt

swagger

### Statemaskin for bestillinger



### Databaseskjema


## Deployment
Distribusjon av tjenesten er gjort med bruk av Github Actions.
[sokos-lavendel CI / CD](https://github.com/navikt/sokos-lavendel/actions)

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
    * application:sokos-lavendel AND envclass:p

- Filter for Dev
    * application:sokos-lavendel AND envclass:q

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
