# Redeploy på annen infrastruktur

## Forutsetninger

Løsningen forutsetter et fungerende NAIS-oppsett som kan deploye til fungerende infrastruktur.

Løsningen forutsetter at utviklere kan logge inn på sitt utstyr, og aksessere drifts- og utviklingsverktøyene det er behov for derfra.

## Database

### Gjenoppretting av database på ny infrastruktur

### Restore av database

## Deploy av applikasjon på ny infrastruktur

### Opprett ny infrastruktur

#### Plattform

#### Tilgang til Kafka/PDL

#### Tilgang til skatteetaten

#### Tilgang til IBM MQ

### Rekonfigurer github-deployment-actions til å peke på ny infrastruktur

### Varsle klienter av tjenester om eventuelt nye endepunkter for tjenester

## Overvåkning og varsling

### Verifiser logg-tilgang i loggverktøy

Applikasjonen har to typer logger - "normale" og "sikre". Disse er en sentral del av driftsoppsettet, og det er viktig at logger kommer inn i logghåndteringssystemene våre.

### Gjenopprett grafana-board, rekonfigurer datakilder

### Gjenopprett logg-varslinger

Applikasjonen varsler om logginnslag på #team-mob-alerts-dev og #team-mob-alerts-prod. Dette oppsettet må gjenskapes eller eventuelt pekes i retning av ny logghåndteringsløsning/database

## Støttende infrastruktur

### Unleash/feature switches

- Applikasjonen trenger tilgang til en unleash-instans. Denne deployes fra NAIS-konsollet, fra menyen på venstre side.
- Alle feature switchene i applikasjonen må konfigureres i Unleash. For sokos-skattekort vil klassen UnleashIntegration inneholde nok informasjon til å kunne rekonfigurere tjenesten.