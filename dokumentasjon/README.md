# Dokumentasjon for sokos-skattekort

Dokumentasjonen beskriver implementasjonen i dette repositoryet. Den dokumenterer ikke alene faktisk oppetid,
forretningsmessig måloppnåelse eller godkjente tjenestenivåer.

## Systemdokumentasjon

| Dokument | Innhold |
|----------|---------|
| [Systembeskrivelse](system/main.md) | Forretningsformål, brukere, funksjoner, avgrensninger og grunnlag for vurdering av forretningsmessig egnethet |
| [Arkitekturoversikt](arkitektur/main.md) | Inngang til teknisk dokumentasjon |
| [Arkitektur og grensesnitt](arkitektur/arkitektur.md) | Avhengighetskart, API-er, MQ og tilgangskontroll |
| [Funksjonelle prosesser](arkitektur/workflows.md) | Bestilling, oppdatering, utsending og periodiske jobber |
| [Resultater og statuser](arkitektur/returer.md) | Skatteetatens svar, leveranse til Oppdrag Z og bestillingsstatus |
| [Datamodell](database/main.md) | Tabeller, relasjoner, historikk og sletting |

## Driftsdokumentasjon

| Dokument | Innhold |
|----------|---------|
| [Driftshåndbok](drift/main.md) | Miljøer, logger, alarmer, overvåkning og hendelseshåndtering |
| [Feilsituasjoner](drift/feil.md) | Feilsøking, nye forsøk og avgrensede manuelle tiltak |
| [Konfigurasjon](drift/konfigurasjon.md) | Profiler, konfigurasjonskilder, funksjonsbrytere og kjøreplan |
| [Deploy og gjenoppretting](drift/redeploy.md) | Produksjonssetting, tilbakeføring og beredskap |
| [Bestillinger fra fil](drift/bestillingsfiler.md) | Bulk-API, oppfølging og behandling av personopplysninger |

## Utvikling og vedlikehold

Se [README](../README.md) for oppsett, bygg og kontakt. Systembeskrivelsen har en
[oversikt over eksisterende tester](system/main.md#kvalitet-og-verifikasjon).
Oppdater berørte dokumenter når funksjoner, kontrakter, datamodell eller driftsrutiner endres.