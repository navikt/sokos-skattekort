# Feilsituasjoner og nye forsøk

Koordiner manuelle tiltak i teamet. Finn riktig miljø, tidspunkt, batch-ID og siste vellykkede behandling før endringer.
Detaljer med personopplysninger og tokens skal bare undersøkes i godkjente sikre verktøy.
Se [driftshåndboken](main.md) for logger, alarmer og innledende kontroller.

## Forespørsler og MQ-mottak

| Symptom | Undersøk | Videre håndtering |
|---------|----------|-------------------|
| Ingen nye forespørsler fra Oppdrag Z | Inngangskø, MQ-tilkobling og `<appnavn>.forespoersel-listener.enabled` | Avklar om avsender har sendt; kontroller at riktig miljø/kø er valgt |
| `Send to BOQ with messageId` | Unntaket og meldingens format i sikre verktøy | Meldingen er forsøkt flyttet til inngangskøens `_BOQ`; rett årsaken og avtal kontrollert tilbakeføring |
| Ugyldig identifikator eller XML-format | Miljøets `application.gyldigeFnr` og innsendt format | Slike forespørsler kan avsluttes uten lagring og uten BOQ; be avsender rette grunnlaget |
| Bulk-kall har svart 202, men bestillinger mangler | Bakgrunnsfeil, eventuell restart og status for de innsendte identifikatorene | 202 er ikke ferdigkvittering; identifiser ubehandlede personer før eventuell ny innsending |

Ikke tøm eller flytt hele backout-køen uten å avklare innhold, årsak, omfang og mulig dobbeltbehandling med ansvarlig for MQ
og forsystemet.

## PDL og Kafka

Ved `Error running kafka consumer ... unsubscribing and waiting 60 seconds for retry` forsøker konsumenten igjen etter
60 sekunder. Undersøk unntaket, Kafka-tilgang, sertifikater og PDL-kall. Ved vedvarende feil må konsekvensen for
identifikatorendringer vurderes. Ikke nullstill consumer-offset som standardtiltak.

PDL-oppslag kan også påvirke mottak og personavklaring. Kontroller riktig PDL-miljø, token og nettverkstilgang.

## Maskinporten og Skatteetatens API

Ved feil i tokenhenting: undersøk sikker logg uten å kopiere token eller nøkkelmateriale.
Kontroller Maskinporten-konfigurasjon, scope, systembruker og samsvar med bestillingsorganisasjon.
Ved API-feil: skill mellom nettverksfeil, autentiseringsfeil, midlertidig utilgjengelighet og avvist forretningsinnhold.

Skatteetaten-klienten har en circuit breaker som ved åpning venter fem minutter før et begrenset nytt forsøk.
Kontroller denne tilstanden før restart eller manuelle nye bestillinger.

## Bestillingsbatcher

| Tilstand | Betydning og tiltak |
|----------|---------------------|
| `NY` | Hentes automatisk. HTTP 204/503 tolkes som «svar ikke klart», og batchen blir liggende |
| `RETRY` | Hentes ved senere kjøring. Generelle unntak for batcher yngre enn én time kan gi denne statusen |
| `FERDIG` | Svaret er behandlet eller det var ingen endringer; undersøk skattekortdata og utsendinger hvis leveransen mangler |
| `FEILET` | Tas ikke med i automatisk henting. Finn årsaken før et eksplisitt nytt forsøk |

For gamle batcher med tomt svar logges avvik, men dette gir ikke i seg selv en ny ekstern bestilling.
`UGYLDIG_INNTEKTSAAR` krever kontroll av år og dato: neste år åpner 15. desember, foregående år kan bestilles til og med juni.
En gammel bestillingsreferanse med ugyldig grunnlag blir ikke nødvendigvis gyldig av et nytt henteforsøk.
Avklar ny bestilling med teamet og forretningen. Ikke slett bestillinger eller batcher som standardtiltak.

### Nytt forsøk via administrasjons-API

Når årsaken er rettet og nytt henteforsøk er vurdert som riktig, bruk
`PATCH /api/v1/admin/bestillingsbatcher/{id}` med `admin-scope`.
Endepunktet endrer bare en batch med status `FEILET` til `RETRY`.
Det oppretter ikke en ny bestilling hos Skatteetaten.

Noter batch-ID, årsak, godkjenning og tidspunkt. Kontroller deretter at batchen behandles og at etterfølgende
skattekort og utsendinger får forventet resultat.
Ikke sett batchen direkte til `NY` i databasen som alternativ til dette endepunktet.

## Mottatte data blir ikke til skattekort

Undersøk feil fra `SkattekortDataService`, personkobling og ubehandlede rader i `skattekort_data`.
En batch kan være `FERDIG` før dataene er konvertert til skattekort.
Ved feil om manglende Svalbardsatser må inntektsåret få støttede og faglig avklarte satser i koden;
ikke omgå feilen ved å merke data som behandlet.

## Utsending av skattekort

Ved feil økes `fail_count` og siste feilmelding lagres.
Utsendinger med `fail_count < 3` kan forsøkes igjen automatisk; ved `fail_count >= 3` stopper automatisk behandling
for de aktuelle radene.

Undersøk riktig MQ-kø, tilkobling, feilmelding og eventuell delvis levering før nytt forsøk.
Administrasjons-API-et kan vise utsendingene, men har ikke et endepunkt som nullstiller feiltelleren.
Hvis en databasekorrigering er nødvendig, må den avgrenses til kontrollerte ID-er, godkjennes og dokumenteres,
og mulige dobbeltleveranser må avklares med mottaker. Ikke nullstill alle feiltellere.

Manglende MQ-melding er ikke alltid teknisk feil: formatteren sender ikke kort uten representerbare trekk.
Kontroller informasjonsteksten i personhistorikken, ikke bare `UTSENDING_OK`. Se [resultater](../arkitektur/returer.md).

## Høy databasebelastning eller manglende fremdrift

Kontroller aktive spørringer, låser, tilkoblinger, volum og nylige endringer med godkjent databasetilgang.
Sammenhold dette med kjørende jobber og metrikker. Ta vare på observasjonene før restart.
Avklar eventuell stans i bestilling, oppdatering eller utsending via funksjonsbrytere.
Bryterne stopper ikke nødvendigvis arbeid som allerede er i gang, og de stopper ikke alle behandlingstrinn.

Hvis restart er nødvendig, følg [deploy- og tilbakeføringsrutinen](redeploy.md).
Kontroller fremdrift, restanser og mulige eksterne sideeffekter etterpå.
