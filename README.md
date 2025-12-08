# sokos-skattekort

Sokos-skattekort er en erstatning for os-eskatt, som brukte altinn 2 til å hente skattekort. I løpet av høsten 2025 vil skatteetaten tilby et nytt grensesnitt, separat fra altinn, for å
tilby samme funksjonalitet.

## Dokumentasjon

[Dokumentasjon](dokumentasjon/README.md) finnes i dette repositoryet.

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

1. Bygg prosjektet ved å kjøre `./gradlew clean build`

### Lokal utvikling

1. Kjør `nais auth login`
2. nais postgres grant sokos-skattekort
3. nais postgres proxy sokos-skattekort
4. Start appen lokalt ved å kjøre main metoden i ***Application.kt***
5. For å kjøre tester i IntelliJ IDEA trenger du [Kotest IntelliJ Plugin](https://plugins.jetbrains.com/plugin/14080-kotest)

### Patching av biblioteker

Vi har ikke testdekning på IBM MQ-bibliotekene (gruppe "com.ibm.mq" i build.gradle.kts) fordi vi kjører activemq i stedet for ibm mq i test-modus.
Vi må teste oppgradering av dette/disse biblitekene manuelt.

## Deployment

Distribusjon av tjenesten er gjort med bruk av Github Actions.
[sokos-skattekort CI / CD](https://github.com/navikt/sokos-skattekort/actions)

Push/merge til main branch vil teste, bygge og deploye til produksjonsmiljø og testmiljø.

## Autentisering

Applikasjonen bruker [AzureAD](https://docs.nais.io/security/auth/azure-ad/) autentisering

## Drift og støtte

Applikasjonen driftes av utviklerteamet under en devops-modell.

Applikasjonen kjører onprem.

Applikasjonen bruker feature toggles. API for å sette og inspisere disse ligger på https://okonomi-unleash-web.iap.nav.cloud.nais.io/projects/default?query=sokos-skattekort

[Driftshåndbok](dokumentasjon/drift/main.md)

## Henvendelser og tilgang

- Spørsmål knyttet til koden eller prosjektet utenfra NAV kan stilles som issues her på github.
- Henvendelser om endringer kan gjøres i henhold til [dokumentasjonen av grensesnittene](dokumentasjon/arkitektur/arkitektur.md).

