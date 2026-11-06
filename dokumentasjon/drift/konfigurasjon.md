# Konfigurasjon

Konfigurasjonen leses gjennom
[PropertiesConfig](../../src/main/kotlin/no/nav/sokos/skattekort/config/PropertiesConfig.kt).
Tabellene beskriver nøkler og miljøvariabler, ikke hemmelige verdier.

## Profiler og prioritet

`APPLICATION_ENV` velger profil. Verdien leses fra miljøvariabel, deretter JVM-systemegenskap,
deretter `NAIS_CLUSTER_NAME` fra miljø eller systemegenskap, og til slutt `APPLICATION_ENV` i innlest konfigurasjon.
Navnet normaliseres til små bokstaver og delen før `-`, slik at `dev-gcp` gir `dev`.
Uten verdi brukes `local`.

Ved oppløsning har miljøvariabler prioritet over JVM-systemegenskaper, deretter valgt profil og grunnkonfigurasjon.
Miljøvariabler brukes gjennom substitusjonene som er deklarert i HOCON; navn i en gammel dokumentasjonstabell
oppretter ikke automatisk en slik kobling.

| Profil | Fil | Bruk |
|--------|-----|------|
| `dev` | [application-dev.conf](../../src/main/resources/application-dev.conf) | Q2, kunstige identifikatorer, Skatteetatens test-API |
| `q1` | [application-q1.conf](../../src/main/resources/application-q1.conf) | Q1, egne MQ-køer og PDL Q1 |
| `prod` | [application-prod.conf](../../src/main/resources/application-prod.conf) | Reelle identifikatorer og produksjonstjenester |
| `local` | [application-local.conf](../../src/main/resources/application-local.conf) | Arver `dev`, leser `defaults.properties`, deaktiverer Kafka |
| `test` | [application-test.conf](../../src/main/resources/application-test.conf) | Automatiserte tester med testavhengigheter |

Grunnverdier ligger i [application.conf](../../src/main/resources/application.conf).
Lokalt oppsett kan kontakte delte utviklingstjenester; se [README](../../README.md#lokal-utvikling).
Ikke commit `defaults.properties`, tokens eller passord.

## Applikasjon og behandling

| HOCON-nøkkel | Overstyring / standard | Beskrivelse |
|--------------|-----------------------|-------------|
| `application.appName` | `NAIS_APP_NAME`, ellers `sokos-skattekort` | Brukes blant annet som prefiks på funksjonsbrytere |
| `application.podName` | `NAIS_POD_NAME` | Identifiserer instansen |
| `application.gyldigeFnr` | `GYLDIGE_FNR`, ellers profilens verdi | `ALLE`, `GYLDIGE` eller `KUNSTIGE_FNR`, se `Foedselsnummerkategori` |
| `application.bestillingOrgnr` | Verdi i profil | Organisasjonen bestillingene gjøres for; produksjon er forskjellig fra test |
| `application.mqListenerEnabled` | Finnes i modellen | Oppstarten styrer faktisk MQ-lytter gjennom Unleash; ikke bruk denne som driftsbryter |
| `scheduler.enabled` | `true` i grunnkonfigurasjonen | Om scheduler opprettes ved oppstart |
| `kafka.enabled` | `true` i grunnkonfigurasjonen, `false` lokalt | Om Kafka-konsumenten opprettes |

`ENVIRONMENT`, `SCHEDULER_ENABLED`, `MQ_LISTENER_ENABLED`, `KAFKA_CONSUMER_ENABLED` og `BESTILLING_ORGNR`
er ikke deklarert som miljøvariabelsubstitusjoner i dagens HOCON-filer.
Bruk de faktiske konfigurasjonsnøklene og kontroller resultatet, ikke tidligere dokumenterte variabelnavn.

## Periodiske jobber

Se [kjøreplanen](../arkitektur/workflows.md#kjøreplan) for registrerte jobber og standard cron-uttrykk.
Tidspunktene ligger under `scheduler.cronBestilling`, `cronUtsending`, `cronHentOppdaterte`, `cronFetchMetrics`
og `cronDeleteSkattekort`.

Det er ikke deklarert miljøvariabler som `SEND_BESTILLING_BATCH_CRON_EXPRESSION` for disse nøklene.
`scheduler.cronHentSkattekort` finnes i modellen uten en separat registrert jobb.
`FORESPOERSEL_INPUT_CRON_EXPRESSION` har ingen innkoblet jobb.

## Integrasjoner og hemmeligheter

| Område | Deklarerte miljøvariabler i grunnkonfigurasjonen | Merknad |
|--------|-------------------------|---------|
| Azure AD / Entra ID | `AZURE_APP_CLIENT_ID`, `AZURE_APP_WELL_KNOWN_URL`, `AZURE_APP_TENANT_ID`, `AZURE_APP_CLIENT_SECRET` | Leveres av Nais. Providernavn er konfigurert som `azureAd` |
| Maskinporten | `MASKINPORTEN_CLIENT_ID`, `MASKINPORTEN_WELL_KNOWN_URL`, `MASKINPORTEN_CLIENT_JWK`, `MASKINPORTEN_SCOPES`, `MASKINPORTEN_SYSTEM_BRUKER_CLAIM` | Systembrukerclaim settes også i profilene og må samsvare med bestillingsorganisasjonen |
| PostgreSQL | `DB_JDBC_URL`, `DB_DATABASE`, `DB_HOST`, `DB_PORT`, `DB_USERNAME`, `DB_PASSWORD` | Cloud SQL-oppsett med prefiks `DB` i Nais. Lokal profil bruker host/port/navn og bruker/passord, ikke bare JDBC-URL |
| IBM MQ-tilkobling | `MQ_HOSTNAME`, `MQ_PORT`, `MQ_QUEUE_MANAGER_NAME`, `MQ_CHANNEL_NAME`, `MQ_SERVICE_USERNAME`, `MQ_SERVICE_PASSWORD` | Vert, port og kanal har profilverdier; brukernavn/passord hentes fra secret |
| IBM MQ-køer | `MQ_FRA_FORSYSTEM_QUEUE_NAME`, `MQ_LEVERANSEKOE_OPPDRAGZ_SKATTEKORT`, `MQ_LEVERANSEKOE_OPPDRAGZ_SKATTEKORT_STOR` | Kønavn er miljøspesifikke |
| Skatteetaten | `SKATTEETATEN_API_URL` | Profilene angir test- eller produksjonsvert |
| PDL | `PDL_URL`, `PDL_SCOPE` | Profilene angir miljø og token-scope |
| Tilgangsmaskinen | `TILGANGSMASKIN_URL`, `TILGANGSMASKIN_SCOPE` | Brukes til personrelatert tilgangskontroll |
| DARE POC | `DAREPOC_URL`, `DAREPOC_SCOPE` | Klienten opprettes bare utenfor produksjon |
| Kafka | `KAFKA_BROKERS`, `KAFKA_SCHEMA_REGISTRY`, `KAFKA_SCHEMA_REGISTRY_USER`, `KAFKA_SCHEMA_REGISTRY_PASSWORD`, `KAFKA_TRUSTSTORE_PATH`, `KAFKA_CREDSTORE_PASSWORD`, `KAFKA_KEYSTORE_PATH` | Infrastrukturverdier fra Nais |
| Unleash | `UNLEASH_SERVER_API_URL`, `UNLEASH_SERVER_API_TOKEN`, `UNLEASH_SERVER_API_ENV` | `environment` leses i modellen, men settes ikke eksplisitt på Unleash-klienten i `UnleashIntegration`; verifiser faktisk miljø/token ved endringer |

Profilene kan erstatte grunnkonfigurasjonens substitusjoner med faste verdier, blant annet for MQ, API-verter og scopes.
En variabel i tabellen er derfor ikke nødvendigvis en virksom overstyring i alle profiler.
Kontroller den ferdig oppløste konfigurasjonen uten å skrive hemmeligheter til logg.

Kafka-topic er som standard `pdl.leesah-v1`, consumer group er `sokos-skattekort-group-id`, og `offsetReset` er
`earliest`. Disse er HOCON-nøkler under `kafka`, ikke substitusjoner for `KAFKA_CONSUMER_*`.

## Funksjonsbrytere

Bryterne finnes i [Unleash](https://okonomi-unleash-web.iap.nav.cloud.nais.io/projects/default?query=sokos-skattekort).
Det fulle navnet er `<application.appName>.<suffiks>`. Q1 bruker derfor prefikset `sokos-skattekort-q1`,
mens Q2 og produksjon bruker `sokos-skattekort`.

| Suffiks | Virkning |
|---------|----------|
| `forespoersel-listener.enabled` | Starter eller stopper MQ-lytter |
| `bestillinger.enabled` | Tillater nye ordinære bestillinger mot Skatteetaten |
| `oppdateringer.enabled` | Tillater nye oppdateringsbestillinger |
| `utsendinger.enabled` | Tillater behandling og utsending av ventende utsendinger |
| `lagre-mottatte-bestillinger.enabled` | Lagrer i tillegg hele mottatte batchsvaret i `bestillingsbatcher.data_mottatt` |

Bryterne for nye bestillinger og oppdateringer stopper ikke henting av eksisterende batcher.
De stopper heller ikke konvertering av mottatte data. MQ-bryteren stopper ikke REST-mottak.
Ingen av disse bryterne er en generell pause av hele applikasjonen.

Kilde: [UnleashIntegration](../../src/main/kotlin/no/nav/sokos/skattekort/infrastructure/UnleashIntegration.kt).
Den tidligere dokumenterte bryteren `forespoerselinput.enabled` er ikke implementert i denne klassen.
