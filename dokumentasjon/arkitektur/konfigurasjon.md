# Konfigurasjon – hvordan applikasjonen laster oppsettet sitt

Denne siden forklarer *mekanikken* bak konfigurasjonslasting i sokos-skattekort: hvilke filer som
faktisk brukes, i hvilken rekkefølge, og hvorfor kjøring fra IntelliJ/Gradle oppfører seg annerledes
enn kjøring i Docker/Nais. For en oversikt over enkeltverdier (miljøvariabler, hva de betyr, hvem som
setter dem), se [driftsdokumentasjonen for konfigurasjon](../drift/konfigurasjon.md).

## Kort oppsummert

All applikasjonskonfigurasjon går gjennom ett sted: `PropertiesConfig` (`config/PropertiesConfig.kt`).
Denne holder på et Ktor `ApplicationConfig`-objekt, og alle andre deler av koden leser konfigurasjon
via `PropertiesConfig.xxxProperties`, aldri direkte fra `environment.config` eller `System.getenv()`.

```mermaid
flowchart TB
    subgraph Kilde["Hvor kommer HOCON-filen fra?"]
        direction TB
        A["IntelliJ / `./gradlew run`
        (kjører på TEST-classpath)"] -->|"finner"| A1["src/test/resources/application.conf"]
        B["Kotest-testsuite"] -->|"ProjectConfig.beforeProject()
        laster eksplisitt"| B1["src/test/resources/application-test.conf"]
        C["Docker (dev/q1/prod)"] -->|"Dockerfile kopierer inn"| C1[".nais/&lt;miljø&gt;/application.conf
        til /app/application.conf,
        som legges FØRST på classpath"]
    end

    A1 --> D["ApplicationConfig(\"application.conf\")
    lastes av Application.module()"]
    C1 --> D
    B1 --> E["PropertiesConfig.load(...)
    (satt direkte, uten om module())"]

    D --> F["PropertiesConfig.load(applicationConfig)"]
    F --> G["PropertiesConfig.xxxProperties
    brukes av resten av applikasjonen"]
    E --> G
```

## De tre kjøreformene i detalj

### 1. Docker / Nais (dev, q1, prod)

Det finnes **ingen** `application.conf` i `src/main/resources`. I stedet gjør
`.nais/<miljø>/Dockerfile` dette:

```dockerfile
COPY build/install/*/lib /lib
COPY .nais/dev/application.conf /app/
...
ENTRYPOINT ["java", "-cp", "/app:/lib/*", "no.nav.sokos.skattekort.ApplicationKt"]
```

`.nais/<miljø>/application.conf` kopieres altså inn som en frittstående fil i `/app`, og `/app` legges
**foran** jar-ene på classpath (`-cp /app:/lib/*`). Når koden gjør
`ApplicationConfig("application.conf")` finner Ktor derfor denne filen på disk – ikke noe som er pakket
inn i jar-filen. De miljøspesifikke verdiene (`profile`, køer, scopes, url-er) er stort sett
hardkodet rett i disse filene, mens hemmeligheter (passord, secrets) hentes fra `${?MILJØVARIABEL}`,
som Nais/Kubernetes fyller inn via `naiserator-<miljø>.yaml`.

### 2. IntelliJ og `./gradlew run` ("lokal" kjøring)

Her kjøres applikasjonen med **test-modulens** classpath, ikke bare `main`:

- `.run/Application as Local.run.xml` peker eksplisitt på modulen `sokos-skattekort.test`.
- Gradle-tasken `run` er satt opp i `build.gradle.kts` til å legge til
  `sourceSets.test.output.resourcesDir` på classpath'en til `main`-koden.

Konsekvensen er at `ApplicationConfig("application.conf")` her finner
**`src/test/resources/application.conf`** – ikke en egen "lokal"-fil, og ikke `.nais/dev/application.conf`.
Denne filen er ment å ligne på nais-miljøene, og forventer at utvikleren har miljøvariabler satt lokalt
(f.eks. via naisdevice/vault) for hemmeligheter og eksterne URL-er.

> ⚠️ **Kjent feil (se "Mistanke om død/feil kode" under):** `src/test/resources/application.conf`
> mangler `profile`-nøkkelen under `application`-blokken. Siden `ApplicationProperties.profile` er
> obligatorisk, feiler `PropertiesConfig.applicationProperties` med
> `MissingFieldException` med en gang den slås opp — verifisert ved å laste denne filen direkte og
> kalle `getAs<ApplicationProperties>()`. Lokal oppstart via `main()`/IntelliJ ser dermed ut til å
> være i stykker slik koden står i dag.

### 3. Kotest-testsuiten (enhets- og ende-til-ende-tester)

Testene går utenom mekanismen over. `ProjectConfig` (Kotest sin `AbstractProjectConfig`) kjører én
gang før hele testsuiten og laster konfigurasjonen eksplisitt:

```kotlin
class ProjectConfig : AbstractProjectConfig() {
    override suspend fun beforeProject() {
        PropertiesConfig.load(ApplicationConfig("application-test.conf"))
    }
}
```

`application-test.conf` er en fullstendig, selvstendig fil med trygge testverdier for alt (databasenavn,
kø-navn, azureAd-placeholder-verdier osv.) — den er ikke avhengig av miljøvariabler for å fungere.

Enkelte ende-til-ende-tester (f.eks. `MottaBestillingEndToEndTest` via `TestUtils.withFullTestApplication`)
starter i tillegg en ekte Ktor-applikasjon via `Application.module(applicationConfig)`. For at
`module()` skal se riktig konfigurasjon (f.eks. en `MockOAuth2Server` sin faktiske well-known-URL i
stedet for en placeholder), bygges det en overstyring:

```kotlin
private fun testEnvironmentConfig(authServer: MockOAuth2Server): ApplicationConfig =
    PropertiesConfig.config.mergeWith(
        MapApplicationConfig(
            "azureAd.wellKnownUrl" to authServer.wellKnownUrl("default").toUrl().toString(),
        ),
    )
```

`ApplicationConfig.mergeWith` er ikke helt intuitiv: **argumentet** til `mergeWith` har prioritet, ikke
mottakeren (`this`). Det vil si `a.mergeWith(b)` betyr "bruk verdier fra `b` der de finnes, ellers fall
tilbake til `a`" — motsatt av hva navnet skulle tilsi ut fra Ktor sin egen dokumentasjon. Dette er
verifisert empirisk (se historikk for `TestUtils.kt`). 

## `Application.module()` sin rolle

```kotlin
fun Application.module(applicationConfig: ApplicationConfig = loadEnvironmentConfig()) {
    ...
    PropertiesConfig.load(applicationConfig)
    ...
}
```

- I produksjon/dev/q1 kalles `module()` uten parameter (`embeddedServer(Netty, port = 8080, module = Application::module)`),
  og default-verdien `loadEnvironmentConfig()` (som er `ApplicationConfig("application.conf")`) brukes.
  `embeddedServer(...)`-varianten som brukes her setter **ikke** opp noen egen HOCON-lasting av
  `environment.config` slik `EngineMain`/`commandLineEnvironment` ville gjort — det er nettopp derfor
  koden manuelt laster `application.conf` selv i stedet for å stole på `environment.config`.
- I tester kalles `module(testEnvironmentConfig(authServer))` eksplisitt, og denne verdien brukes nå
  (se historikk — det var tidligere en bug her, se under).

### Historisk bug (rettet)

Frem til nylig ignorerte `module()` parameteren sin fullstendig:

```kotlin
// FEIL (tidligere kode):
fun Application.module(applicationConfig: ApplicationConfig = environment.config) {
    ...
    PropertiesConfig.load(loadEnvironmentConfig()) // <-- laster alltid application.conf på nytt,
                                                    //     uansett hva som ble sendt inn
```

Dette gjorde at overstyringer sendt inn fra tester (bl.a. mock-OAuth2-serverens URL) aldri nådde
`PropertiesConfig`, og førte til at `MottaBestillingEndToEndTest` feilet på oppstart
(`NPE`/manglende well-known-URL). Løsningen var å faktisk bruke parameteren:
`PropertiesConfig.load(applicationConfig)`, med default-verdi `loadEnvironmentConfig()` i stedet for
`environment.config`, slik at produksjonsoppførselen beholdes uendret mens testkode nå kan overstyre.

## Mistanke om død/feil kode i konfigurasjonen

Følgende ble oppdaget under arbeidet med denne dokumentasjonen og bør ryddes opp i ved senere
anledning:

1. **`APPLICATION_ENV` er en død miljøvariabel.** Den settes i alle tre
   `.nais/<miljø>/naiserator-*.yaml`-filer (`DEV`, `Q1`, `PROD`), men leses aldri av applikasjonen —
   verken via en `${?APPLICATION_ENV}`-referanse i noen `.conf`-fil, eller direkte i Kotlin-kode.
   `application.profile` settes i stedet hardkodet direkte i hver `.nais/<miljø>/application.conf`.
   Og det er kanskje fint at applikasjonen oppfører seg likt i alle konfigurasjoner?
2. **`src/test/resources/application.conf` mangler `application.profile`.** Dette gjør at
   `PropertiesConfig.applicationProperties` feiler med `MissingFieldException` så snart den
   brukes, siden `profile` er et påkrevd felt uten default-verdi. Dette rammer i praksis kun lokal
   kjøring via IntelliJ/`./gradlew run` (se punkt 2 over) — Kotest-testene påvirkes ikke, siden de
   bruker `application-test.conf` via `ProjectConfig`.
3. **`dokumentasjon/drift/konfigurasjon.md` beskrev flere miljøvariabler som ikke finnes i kildekoden**
   (`ENVIRONMENT`, `MQ_LISTENER_ENABLED`, `SCHEDULER_ENABLED`, `KAFKA_CONSUMER_ENABLED`,
   `AZURE_APP_PROVIDER_NAME`/`AZURE_APP_AUTH_PROVIDER_NAME`). De tilsvarende bryterne
   (`application.mqListenerEnabled`, `scheduler.enabled`, `kafka.enabled`, `azureAd.providerName`) er i
   virkeligheten hardkodet direkte i hver miljøs `.conf`-fil, og styres ikke av miljøvariabler i det
   hele tatt. Dokumentet er oppdatert til å reflektere dette, men det er verdt å vurdere om disse
   burde vært reelle miljøvariabler (for enklere skru av/på uten ny utrulling), eller om
   dokumentasjonen bare var utdatert.
4. DatabaseConfig og DataSource har blitt splittet. Det gjør at DatabaseConfig nå kanskje mer er en
   vedlikehold-dataskjemaet-komponent?

Disse punktene er ikke rettet som en del av dette dokumentasjonsarbeidet — de flagges her for videre
oppfølging.
