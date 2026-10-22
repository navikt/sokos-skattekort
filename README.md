# sokos-skattekort

Henter, lagrer og formidler skattekort fra Skatteetaten til Navs økonomisystemer.

Tjenesten erstatter integrasjonen i `os-eskatt` mot Altinn 2. Den mottar forespørsler fra Oppdrag Z og REST-klienter,
bestiller skattekort asynkront fra Skatteetatens API og distribuerer skattekort og senere endringer til abonnenter.

## Dokumentasjon

- [Dokumentasjonsoversikt](dokumentasjon/README.md)
- [Systemdokumentasjon: formål, funksjoner og vurderingsgrunnlag](dokumentasjon/system/main.md)
- [Arkitektur og grensesnitt](dokumentasjon/arkitektur/main.md)
- [Datamodell](dokumentasjon/database/main.md)
- [Driftsdokumentasjon](dokumentasjon/drift/main.md)

## Utviklingsmiljø

### Forutsetninger

* Java 25
* Docker for tester med Testcontainers
* Tilgang til Navs utviklingsmiljø for lokal kjøring mot eksterne tjenester
* `nais`, `kubectl` og `gcloud` for oppsett mot utviklingsmiljøet
* Valgfritt: [Kotest IntelliJ Plugin](https://plugins.jetbrains.com/plugin/14080-kotest) for kjøring av tester i IDE

### Bygge og teste

Bruk Gradle-wrapperen i repositoryet:

```shell
./gradlew build
```

`./gradlew test` kjører testene uten et fullt bygg. `./gradlew installDist` lager en kjørbar distribusjon.
Prosjektet bruker Kotlin, Ktor/Netty, PostgreSQL med Flyway og Kotliquery, IBM MQ, Kafka og db-scheduler.

### Lokal utvikling

Standard lokal profil arver Q2-konfigurasjonen og leser `defaults.properties`.
Den kan derfor kontakte delte utviklingstjenester og behandle data i utviklingsmiljøet.

1. Kjør `nais auth login`.
2. Les [setupLocalEnvironment.sh](setupLocalEnvironment.sh) før kjøring. Skriptet bytter til `dev-gcp`/`okonomi`
   og henter miljøvariabler fra første pod med `sokos-skattekort` i navnet. Kontroller at dette er riktig app og miljø.
3. Kjør `./setupLocalEnvironment.sh`. Filen `defaults.properties` inneholder hemmeligheter og skal ikke deles eller committes.
4. Opprett nødvendig databasetilgang med `nais postgres grant sokos-skattekort` i riktig kontekst,
   og start `nais postgres proxy sokos-skattekort` i en egen terminal.
5. Kontroller at `postgres.host`, `postgres.port`, `postgres.name`, `postgres.username` og `postgres.password`
   samsvarer med proxy og tildelt bruker. Verdiene hentet fra poden er ikke nødvendigvis riktige for lokal tilgang.
6. Start `main` i [Application.kt](src/main/kotlin/no/nav/sokos/skattekort/Application.kt), eller kjør `./gradlew run`.

Flyway kjøres ved oppstart også lokalt. Lokal profil deaktiverer Kafka, men ikke scheduler eller MQ-lytter.
Avklar derfor kjøring mot delt database og køer med teamet. Se [konfigurasjon](dokumentasjon/drift/konfigurasjon.md).

### Patching av biblioteker

MQ-testene bruker ActiveMQ Artemis i stedet for IBM MQ. Oppgradering av IBM MQ-klienten må derfor også
verifiseres manuelt mot IBM MQ i testmiljø. Se [kontroll før produksjonssetting](dokumentasjon/drift/redeploy.md).

## Deployment

GitHub Actions bygger og tester applikasjonen og deployer til Nais:

| Miljø | Cluster | Applikasjon | Manifest |
|-------|---------|-------------|----------|
| Q2 | `dev-gcp` | `sokos-skattekort` | [Q2](.nais/dev/naiserator-q2.yaml) |
| Q1 | `dev-gcp` | `sokos-skattekort-q1` | [Q1](.nais/dev/naiserator-q1.yaml) |
| Produksjon | `prod-gcp` | `sokos-skattekort` | [Produksjon](.nais/prod/naiserator-prod.yaml) |

Alle miljøene bruker namespace `okonomi`. [Deploy-workflowen](.github/workflows/deploy.yaml) deployer
kodeendringer på `main` til Q2, deretter Q1 og produksjon. Rene dokumentasjonsendringer er unntatt.
[Manuell deploy](.github/workflows/manual-deploy.yaml) støtter Q2 (`dev`) og Q1, ikke produksjon.

## Autentisering og grensesnitt

REST-API-ene bruker Azure AD/Entra ID med scopes for brukerkall og roller for støttede maskinkall.
Oppslag på reelle skattekort krever saksbehandlerkontekst; et maskintoken alene er ikke tilstrekkelig.
Tilgangsmaskinen brukes til personrelatert tilgangskontroll. Utgående kall til Skatteetaten bruker Maskinporten.

Se [grensesnitt og OpenAPI](dokumentasjon/arkitektur/arkitektur.md) for API-versjoner, MQ-kontrakter og tilgangskrav.

## Drift og støtte

Applikasjonen forvaltes av utviklerteamet og kjører på Nais i GCP. PostgreSQL kjører i Cloud SQL;
IBM MQ er en ekstern avhengighet utenfor GCP.

[Driftshåndboken](dokumentasjon/drift/main.md) beskriver overvåkning, alarmer, hendelseshåndtering og driftskontroller.
Funksjonsbrytere administreres i [Unleash](https://okonomi-unleash-web.iap.nav.cloud.nais.io/projects/default?query=sokos-skattekort).

## Henvendelser og tilgang

- Kodeeier: [@navikt/okonomi](CODEOWNERS).
- Interne henvendelser: [#utbetaling](https://nav-it.slack.com/archives/CKZADNFBP).
- Eksterne spørsmål kan stilles som GitHub-issues. Ikke legg ved personopplysninger, tokens eller andre hemmeligheter.
- Endringer i kontrakter må avklares med berørte konsumenter, se [grensesnittoversikten](dokumentasjon/arkitektur/arkitektur.md).
