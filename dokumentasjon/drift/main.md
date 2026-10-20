# Driftshåndbok

Tjenesten forvaltes av utviklerteamet med kodeeier [@navikt/okonomi](../../CODEOWNERS).
Koordiner hendelser og manuelle tiltak i [#utbetaling](https://nav-it.slack.com/archives/CKZADNFBP).
Avtal én ansvarlig for tiltak, slik at flere ikke endrer samme batch, kø eller database samtidig.

## Miljøer

| Miljø | Cluster | Namespace | Applikasjon | Ingress |
|-------|---------|-----------|-------------|---------|
| Q2 | `dev-gcp` | `okonomi` | `sokos-skattekort` | [Q2](https://sokos-skattekort.intern.dev.nav.no) |
| Q1 | `dev-gcp` | `okonomi` | `sokos-skattekort-q1` | [Q1](https://sokos-skattekort-q1.intern.dev.nav.no) |
| Produksjon | `prod-gcp` | `okonomi` | `sokos-skattekort` | [Produksjon](https://sokos-skattekort.intern.nav.no) |

Applikasjonen og databasen kjører i GCP. IBM MQ er en ekstern avhengighet.
Manifestene angir 1–2 replikaer. Produksjonsdatabasen har høy tilgjengelighet, automatisk diskutvidelse og
point-in-time recovery konfigurert. Dette er konfigurasjon, ikke dokumentasjon på en gjennomført gjenopprettingsøvelse.

## Daglig kontroll og ekstra overvåkning

Ved årsskifte og større bestillinger må kapasitet og oppfølging avklares på forhånd.
Følg også [årsavhengige begrensninger](../system/main.md#forretningsregler-og-avgrensninger).

1. Kontroller podstatus, restarttall og nylige deployer i riktig miljø.
2. Se i Grafana etter alder på eldste bestilling og usendte utsending, siste lagrede skattekort,
   feilede leveranser, JVM-belastning og databasebelastning. Vurder tallene mot forventet trafikk og kjøreplan.
3. Kontroller uferdige og feilede bestillingsbatcher, utsendinger med `fail_count >= 3` og MQ-backout-køen.
4. Kontroller alarmer og logger. Skille mellom et forsinket svar fra Skatteetaten, et avvist resultat og intern feil.
5. Noter tidspunkt, miljø, observasjoner, ansvarlig og eventuelle tiltak i hendelsen eller teamets driftslogg.
   Ikke legg personopplysninger i Slack eller åpne saker.

En grønn readiness-probe bekrefter ikke at hele bestillings- og leveransekjeden fungerer.
Ved avvik: bruk [feilsituasjoner](feil.md), og bekreft etter tiltak at køene faktisk tømmes og berørte leveranser fullføres.

## Logger

Vanlige logger uten sensitive data finnes i
[Grafana Loki](https://grafana.nav.cloud.nais.io/a/grafana-lokiexplore-app/explore/service_name/sokos-skattekort/logs?var-filters=service_name%7C%3D%7Csokos-skattekort).
Velg riktig cluster, namespace og applikasjon; Q1 har eget applikasjonsnavn.

Sensitive detaljer logges med `TEAM_LOGS_MARKER` til
[Team Logs](https://console.cloud.google.com/logs/query).
Velg riktig økonomi-prosjekt for utvikling eller produksjon og et avgrenset tidsrom.
Tilgang til sikre logger må være tildelt. Ikke kopier fødselsnumre, skattekortdata, tokens eller råmeldinger til
vanlige logger, Slack eller GitHub.

Bruk batch-ID, bestillingsreferanse, meldings-ID og tidspunkt der det er tilstrekkelig for feilsøkingen.

## Grafana, metrikker og alarmer

- [Grafana produksjon](https://grafana.nav.cloud.nais.io/d/8c975fff-46f6-4eb3-8dac-df4a47425f3a/sokos-skattekort?var-datasource=000000021)
- [Grafana test](https://grafana.nav.cloud.nais.io/d/8c975fff-46f6-4eb3-8dac-df4a47425f3a/sokos-skattekort?var-datasource=000000020)
- [Grafana-alarmer](https://grafana.nav.cloud.nais.io/alerting/list?search=sokos-skattekort)

Kontroller datakilde og miljø i dashboardet før tallene brukes til beslutninger.
Varslingskanalene er `#team-mob-alerts-dev` og `#team-mob-alerts-prod`.
Gjeldende alarmregler, terskler og kontaktpunkter må kontrolleres i Grafana; de ligger ikke som alarmmanifester her.

Applikasjonen eksponerer `/internal/metrics`. Fagmetrikker har prefikset `sokos_skattekort_`:

| Metrikk | Bruk |
|---------|------|
| `oldest_bestilling_seconds` med `type=eldste_usendt` / `eldste_sendt` | Oppdage bestillinger som blir liggende før eller etter ekstern bestilling |
| `oldest_usendt_utsending_seconds` | Oppdage leveranser som blir liggende |
| `timer_siden_skattekort` | Tid siden siste lagrede skattekort; koden leverer sekunder til tross for navnet, så kontroller dashboardets omregning og alarmterskler |
| `bestilte_skattekort` / `oppdaterte_skattekort` | Følge mottatte bestillinger og endringer |
| `utsendinger_oppdragz_total` / `utsendinger_oppdragz_feil_total` | Følge MQ-leveranser og feil |
| `utsendinger_i_koe` | Tilstand registrert under utsending; ikke en fullstendig opptelling av alle blokkerte utsendinger |

Se [MetricsService](../../src/main/kotlin/no/nav/sokos/skattekort/infrastructure/MetricsService.kt).
Periodisk innsamlede fagmetrikker oppdateres som standard hvert tiende minutt.
Bruk database/API og logger for ferskere eller mer detaljerte observasjoner.

## Podstatus og logger

Eksemplene under leser produksjonsstatus uten å endre kontekst eller ressurser:

```shell
kubectl --context prod-gcp -n okonomi get pods -l app=sokos-skattekort
kubectl --context prod-gcp -n okonomi logs -l app=sokos-skattekort -c sokos-skattekort --since=30m --tail=200 --prefix
kubectl --context prod-gcp -n okonomi get events --sort-by=.metadata.creationTimestamp
```

For Q2 brukes `dev-gcp`; for Q1 brukes i tillegg appnavnet `sokos-skattekort-q1`.
Ved krasj, finn konkret pod og les også forrige containers logger med `kubectl logs <podnavn> --previous`
og riktig kontekst, namespace og container.

Ikke restart som første feilsøkingstiltak. Ta vare på relevant feilinformasjonsgrunnlag og avklar om en pågående
bestilling eller leveranse kan ha blitt utført eksternt uten at lokal status er oppdatert.
Se [deploy og tilbakeføring](redeploy.md).

## Lesetilgang til database

Bruk godkjent tilgang via Nais og riktig cluster/namespace. Ikke del tilkoblingshemmeligheter.
Start med spørringer som ikke henter personopplysninger:

```sql
SELECT id, type, status, opprettet, oppdatert
FROM bestillingsbatcher
WHERE status <> 'FERDIG'
ORDER BY opprettet
LIMIT 100;

SELECT forsystem, fail_count, COUNT(*) AS antall
FROM utsendinger
GROUP BY forsystem, fail_count
ORDER BY forsystem, fail_count;
```

Ikke endre status eller slette rader for å fjerne en alarm.
Bruk administrasjons-API for nytt forsøk av feilede batcher etter årsaksvurdering.
Databaseendringer krever avgrenset utvalg, faglig konsekvensvurdering og dokumentert godkjenning.

## Hendelseshåndtering

Ved stans eller forsinkelser: avklar omfang, berørte forsystemer og tidskritiske utbetalinger.
Varsle berørte konsumenter gjennom teamets etablerte kanaler.
Registrer starttid, miljø, symptomer, ansvarlig og tiltak. Bruk [funksjonsbrytere](konfigurasjon.md#funksjonsbrytere)
bare når konsekvensen av å stoppe et behandlingstrinn er forstått.

Etter retting må berørte batcher, backout-meldinger og blokkerte utsendinger følges opp.
Lukk ikke hendelsen bare fordi podene er friske; bekreft faktisk fremdrift og avklar med forsystemet ved tvil om leveranse.

## Driftsrutiner

- [Feilsituasjoner og nye forsøk](feil.md)
- [Konfigurasjon og funksjonsbrytere](konfigurasjon.md)
- [Deploy, tilbakeføring og gjenoppretting](redeploy.md)
- [Bestillinger fra fil](bestillingsfiler.md)
