# Bestillinger fra fil

## Bruk bulk-API-et

Applikasjonen har `POST /api/v1/skattekort/bestillingbulk/{forsystem}/{inntektsaar}` for tekst med
én personidentifikator per linje. Endepunktet krever OBO-token med `admin-scope`.
Ikke bruk databaseimport som standardmetode for nye bulkbestillinger.

1. Avklar bestillingsgrunnlag, riktig miljø, forsystem, inntektsår, forventet volum og ansvarlig for oppfølging.
2. Bruk godkjent lagrings- og overføringsmåte for filen. Ikke legg filen i repositoryet, Slack eller GitHub.
   Ikke anta at lokal lagring av produksjonsopplysninger er tillatt; det må følge Navs gjeldende regler.
3. Kontroller at filen har én identifikator per linje og bare inneholder nødvendige opplysninger.
4. Send filinnholdet til riktig miljø og API med godkjent klient og tokenhåndtering.
   Se [OpenAPI](../../src/main/resources/openapi/skattekort-v1-swagger.yaml) og
   [rutens implementasjon](../../src/main/kotlin/no/nav/sokos/skattekort/api/SkattekortApi.kt).
5. Følg opp status og feil, og kontroller restanser før filen eventuelt sendes på nytt.
6. Fjern arbeidskopier etter godkjent rutine og oppbevar bare nødvendig, ikke-sensitiv dokumentasjon om kjøringen.

Koden fjerner blanke linjer og duplikater og validerer identifikatorene for miljøet før bakgrunnsjobben startes.
Et valideringsavvik kan avvise hele innsendelsen.

## Oppfølging og begrensninger

`202 Accepted` betyr at innsendelsen er akseptert, ikke at alle bestillingene er registrert eller skattekort levert.
Hver identifikator behandles i en bakgrunnsoppgave, og feil logges per person.
En podrestart kan avbryte behandlingen; det finnes ikke en varig importjobb med samlet ferdigstatus.

Følg opp med status-API-et (`status`/`statuser` med `status-scope`), administrasjonsoversikter og logger.
Avstem innsendt antall mot faktisk behandling i sikre verktøy.
Ved store volum må kapasitet og tidsvindu vurderes på forhånd; dokumentasjonen oppgir ingen verifisert maksimal filstørrelse.

## Historisk import via forespoersel_input

Tidligere ble bestillinger importert som SQL til `forespoersel_input` og aktivert med en funksjonsbryter.
Tabellen finnes fortsatt i migreringene, men dagens `JobTaskConfig` og `UnleashIntegration` har ingen innkoblet
jobb eller bryter som behandler den. En vellykket SQL-import er derfor ikke en fungerende bestillingsprosess.

Ikke bruk det tidligere 2025-spesifikke skriptet eller `forespoerselinput.enabled` som driftsrutine.
Hvis en annen importmekanisme er nødvendig, må den implementeres, testes og godkjennes særskilt.

## Historisk nullstilling av database

Tidligere dokumenterte `TRUNCATE ... CASCADE`-kommandoer var en destruktiv nullstilling, ikke en import eller
feilretting. De er ikke en generell driftsrutine og skal ikke brukes for å fjerne restanser.
Sletting kan fjerne abonnementer, historikk og grunnlag som ikke kan gjenskapes ved bare å bestille på nytt.

Et eventuelt behov for nullstilling krever en separat plan med faglig konsekvensvurdering, godkjenning,
sikkerhetskopi, avgrensning, gjenoppbygging og verifikasjon.
Se [datamodellen](../database/main.md) og [gjenopprettingsrutinen](redeploy.md).
