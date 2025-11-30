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

1. Bygg prosjektet ved å kjøre `./gradlew clean build shadowJar`

### Lokal utvikling

2. Start appen lokalt ved å kjøre main metoden i ***Application.kt***
3. For å kjøre tester i IntelliJ IDEA trenger du [Kotest IntelliJ Plugin](https://plugins.jetbrains.com/plugin/14080-kotest)

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

- Spørsmål knyttet til koden eller prosjektet utenfra NAV kan stilles som issues her på github.
- Henvendelser om endringer kan gjøres i henhold til [dokumentasjonen av grensesnittene](dokumentasjon/arkitektur/arkitektur.md).

# Periodiske tester

## Sjekk at auditlogging fungerer

Vi har ikke testdekning for at oppsettet av audit-logging faktisk når frem til audit-systemet. Dette må testes manuelt jevnlig.
# Feilsituasjoner

## Feil i forespørsler

## Feil under interaksjon med PDL

### Error running kafka consumer for ${kafkaConfig.topic}, unsubscribing and waiting $DELAY_ON_ERROR_SECONDS seconds for retry

Ukjent situasjon, exception i loggen gir forhåpentligvis mer informasjon.

## Feil under bestilling av skattekort

### Kunne ikke hente accessToken, se sikker log for meldingen som string

Se sikker logg, som meldingen sier. Vi har trøbbel med å hente et maskinporten-token, som er nødvendig for å kunne kommunisere med skatteetaten. Ukjent situasjon, må debugges manuelt.

### Feil fra tokenprovider, Token: $jwtAssertion, Feilmelding: $feilmelding

Vi klarer ikke lage et maskinporten-token, som er en forutsetning for å kommunisere med skatt. Ukjent situasjon, og må debugges manuelt.

### Bestillingsbatch $batchId feilet med UGYLDIG_INNTEKTSAAR. Dette skulle ikke ha skjedd, og batchen må opprettes på nytt. Bestillingene har blitt tatt vare på for å muliggjøre manuell håndtering

Av en eller annen grunn liker ikke skatteetaten bestillingen. Vi får ikke bestille skattekort for neste år før 15. desember, og ikke etter juni for foregående år.

Vurder situasjonen. Veier ut:
- slett bestillingene og batchen. Dette vil potensielt medføre 50% skatt for brukerne det gjelder, og bør godkjennes av noen som jobber i linja
- bestillingsbatchen kan utføres på nytt ved å slette bestillingsbatch-innslaget i databasen

### Bestillingsbatch feilet: ${ex.message}

Dette er en catch-all for feil under oppretting av bestilling. Situasjonen er ukjent, og må debugges manuelt.

### Bestillingsbatch $batchId feilet: ${response.status}

Dette er en generell catch-all for feil under henting av en bestilling. Situasjonen er ukjent, og må debugges manuelt.

## Feil under utsending av skattekort


