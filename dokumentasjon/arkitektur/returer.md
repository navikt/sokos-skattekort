# Resultater og statuser

Skatteetatens batchstatus, resultatet for en person, lokal bestillingsstatus og MQ-leveranse beskriver ulike trinn.
En vellykket batch betyr ikke at alle personene har et skattekort som kan sendes til Oppdrag Z.

## Svar fra Skatteetaten

| Svar ved henting | Lokal behandling |
|-----------------|------------------|
| HTTP 204 eller 503 | Behandles som at svaret ikke er klart; forsøk igjen ved senere kjøring |
| `FORESPOERSEL_OK` | Behandle personresultater og marker batchen `FERDIG` |
| `INGEN_ENDRINGER` | Marker batchen `FERDIG`, uten nye skattekort |
| Andre resultatstatuser | Marker batchen `FEILET` og logg avviket |
| Tekniske unntak | Kan gi `RETRY` eller `FEILET`, avhengig av feil og batchens alder |

Den konkrete håndteringen ligger i
[BestillingService](../../src/main/kotlin/no/nav/sokos/skattekort/skattekorthenting/BestillingService.kt).

## Resultat for den enkelte person

| Resultat | Behandling |
|----------|------------|
| `skattekortopplysningerOK` | Lagre mottatte data og opprett skattekort med trekkopplysninger |
| `ikkeSkattekort` | Lagre resultatet; dette betyr ikke automatisk en MQ-leveranse |
| `ikkeTrekkplikt` | Lagre resultatet og syntetiser frikort uten beløpsgrense for Nav-trekkodene |
| `utgaattDnummerSkattekortForFoedselsnummerErLevert` | Lagre mottatte data for videre behandling |
| `ugyldigFoedselsEllerDnummer` | Flagg personen og lagre resultatet for videre behandling |
| `ugyldigOrganisasjonsnummer` | Avbryt behandling med feil; batchen markeres `FEILET` |

Tilleggsopplysninger kan utløse syntetisering etter
[Syntetisering](../../src/main/kotlin/no/nav/sokos/skattekort/skattekort/Syntetisering.kt):

- Opphold på Svalbard gir egne satser for år som koden støtter.
- Kildeskatt på pensjon supplerer manglende trekkoder for pensjon og uføretrygd.
- Opphold i tiltakssone kan formidles som tilleggsopplysning i MQ-formatet, men utløser ikke alene syntetisering.

## Leveranse til Oppdrag Z

Formatteren tar med trekkodene `LOENN_FRA_NAV`, `PENSJON_FRA_NAV` og `UFOERETRYGD_FRA_NAV`.
For `ikkeTrekkplikt` lager formatteren frikort uten beløpsgrense.
Hvis formatteren ikke har representerbare trekk, returnerer den tom tekst.

Tom tekst sendes ikke til MQ. Utsendingen fjernes, og personhistorikken får en forklaring om at formatet ikke kan
uttrykke innholdet. Dette registreres med `UTSENDING_OK`, men betyr ikke at en MQ-melding faktisk er sendt.
Les derfor informasjonsteksten sammen med taggen ved feilsøking.

Se [UtsendingService](../../src/main/kotlin/no/nav/sokos/skattekort/utsending/UtsendingService.kt)
og [formattereren](../../src/main/kotlin/no/nav/sokos/skattekort/utsending/oppdragz/SkattekortFixedRecordFormatter.kt).
REST-klienter kan slå opp lagrede skattekort selv om Oppdrag Z-formatet ikke gir en melding.

## Lokal bestillingsstatus

Status-API-et vurderer regler i prioritert rekkefølge i
[StatusRegelsett](../../src/main/kotlin/no/nav/sokos/skattekort/skattekortbestilling/status/StatusRegelsett.kt):

| Status | Betydning |
|--------|-----------|
| `UGYLDIG_FNR` | Identifikatoren er ikke gyldig for miljøet |
| `UGYLDIG_FORSYSTEM` | Forsystemet er ukjent |
| `IKKE_FORESPURT` | Personen finnes ikke lokalt |
| `IKKE_BESTILT` | Bestilling finnes, men er ikke knyttet til en batch |
| `BESTILT` | Tilknyttet batch har status `NY` |
| `FEILET_I_BESTILLING` | Tilknyttet batch har status `FEILET` |
| `VENTER_UTSENDING` | En utsending finnes |
| `ABONNERER` | Abonnement finnes, og ingen tidligere regel traff |
| `ABONNERER_IKKE` | Abonnement mangler, og ingen tidligere regel traff |
| `UKJENT` | Ingen regel traff |

Før regelsettet kjøres kan enkeltstatusoppslaget returnere `SKJERMET` hvis Tilgangsmaskinen avviser saksbehandlerens
tilgang. Bulkstatus bruker en egen detaljert svarmodell og er ikke samme regelvurdering.

Dette er ikke en kvittering fra forsystemet. Særlig `RETRY`-batcher og utsendinger som har nådd feilgrensen må undersøkes
med batch- og utsendingsoversiktene, ikke bare med samlet status.
