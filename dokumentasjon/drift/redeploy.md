# Deploy, tilbakeføring og gjenoppretting

## Forutsetninger

Deploy krever tilgang til repositoryets GitHub Actions og målmiljøet i Nais.
Manuelle produksjonstiltak skal ha ansvarlig, avklart konsekvens og sporbar godkjenning etter teamets rutiner.
Ikke kjør øvelser eller gjenoppretting direkte mot produksjon uten særskilt avklaring.

## Ordinær deploy

[Deploy-workflowen](../../.github/workflows/deploy.yaml) kjører for relevante endringer på `main`.
Den bygger/tester, lager miljøtilpassede containerbilder og deployer til Q2, deretter Q1 og til slutt produksjon.
Dokumentasjonsendringer er unntatt.
[Manuell deploy](../../.github/workflows/manual-deploy.yaml) kan velge Q2 (`dev`) eller Q1 fra en valgt branch.
Den har ikke et produksjonsvalg.

Flyway kjøres ved applikasjonsoppstart. Kontroller derfor migreringer og bakoverkompatibilitet før deploy,
ikke bare at applikasjonskoden kan rulles tilbake.

## Kontroll før produksjonssetting

1. Kjør `./gradlew build` med Java 25 og Docker tilgjengelig.
2. Vurder endringer i grensesnitt, datamodell, rettigheter, årsregler og eksterne klientbiblioteker.
3. Kontroller relevant flyt i test: mottak, bestillingsreferanse, henting, skattekort, utsending og status.
   Bruk bare godkjente testidentifikatorer og riktige miljøkøer.
4. Ved endringer i IBM MQ-klient eller formatter: verifiser mot IBM MQ og avklar format med Oppdrag Z.
   Testene bruker ActiveMQ Artemis og dokumenterer ikke full kompatibilitet med IBM MQ.
5. Ved årsskifte: kontroller neste års støtte, Svalbardsatser, planlagte volum og bemanning.
6. Avklar forventet effekt, observasjonstid og eventuell tilbakeføring med berørte konsumenter.

## Kontroll etter deploy

- Bekreft at riktig image er deployet og at podene blir klare uten gjentatte restarter.
- Kontroller Flyway-logg og at forventede tjenester, MQ-lytter og Kafka-konsument er startet.
- Kontroller funksjonsbrytere, tokenhenting og tilgang til avhengighetene.
- Følg gjennomføringen av relevante bestillinger og leveranser, ikke bare HTTP-helse.
- Kontroller Grafana, logger, backout-kø og feiltellere. Dokumenter avvik og tiltak.

## Restart og tilbakeføring

Restart kan avbryte bakgrunnsbehandling. En bulkbestilling har ikke en varig importjobb som garanterer at resten
gjenopptas etter restart. Kontroller slike bestillinger før og etter tiltaket.

Ved en feilaktig release: identifiser sist kjente fungerende image og sammenlign databaseendringer.
Bruk teamets godkjente Nais-deployrutine for det konkrete miljøet og manifestet.
Repositoryet har ikke en egen produksjonsworkflow for rollback; ikke anta at manuell testdeploy kan brukes til dette.

Ikke reverser eller rediger allerede kjørte Flyway-migreringer.
Hvis gammel kode ikke er kompatibel med dagens skjema, må en fremoverrettet feilretting eller særskilt
migreringsplan vurderes. Undersøk mulige eksterne sideeffekter før meldinger eller batcher kjøres på nytt.

## Databasegjenoppretting

Produksjonsmanifestet konfigurerer Cloud SQL med høy tilgjengelighet og point-in-time recovery.
Dette er ikke i seg selv en testet gjenopprettingsprosedyre eller et løfte om gjenopprettingstid.
Faktiske sikkerhetskopier, tilgjengelig gjenopprettingsvindu og tilgang må kontrolleres i målmiljøet.

Ved behov for restore:

1. Avklar hendelsesomfang, målpunkt og akseptabelt datatap med teamet og forretningsansvarlig.
2. Avklar stans av skrivende prosesser og mottak. Funksjonsbrytere alene stopper ikke all skriving.
3. Bevar opprinnelig database og gjenopprett til en separat instans etter gjeldende Nais/Cloud SQL-prosedyre,
   med hjelp fra plattformansvarlige ved behov.
4. Kontroller skjema, Flyway-historikk, radantall, siste behandlingspunkter og forventede fagdata i et isolert oppsett.
5. Avstem tilstanden mot Skatteetaten og MQ før gjenopptakelse. En database-restore ruller ikke tilbake eksterne
   bestillinger eller meldinger som allerede er levert.
6. Avklar tilkoblingsbytte og start behandling kontrollert. Følg restanser, duplikater og leveranser.
7. Dokumenter faktisk datatap, tidsbruk, kontroller og eventuelle avvik.

Mål for gjenopprettingstid (RTO), akseptabelt datatap (RPO) og resultat fra restore-øvelser er ikke dokumentert her.
Disse må fastsettes og prøves sammen med forretning og drift før beredskapen kan regnes som verifisert.

## Flytting til annen infrastruktur

Flytting krever en egen gjennomføringsplan. Før trafikk flyttes, kontroller:

| Område | Nødvendig avklaring |
|--------|--------------------|
| Plattform og database | Nais-ressurser, versjon, kapasitet, restore og kompatibelt skjema |
| Identitet og tilgang | Azure-klient, scopes/roller, innkommende klienter og Tilgangsmaskinen |
| Skatteetaten | Maskinporten, systembruker, organisasjonsnummer og nettverk |
| MQ | Tilkobling, credentials, inngangs-/leveransekøer og backout-kø |
| PDL og Kafka | Miljø, topic, consumer group, offset og sertifikater; ingen ukontrollert offset-reset |
| Unleash | API-tilgang, appnavnprefiks og riktige brytere |
| Observabilitet | Vanlige og sikre logger, metrikker, Grafana-datakilder, alarmer og varslingskanaler |
| Konsumenter og deploy | Endepunkter, kontrakter, manifeststier, workflow-mål og avtalt overgang |

Start ikke gammel og ny installasjon parallelt mot samme køer eller skrivende datagrunnlag uten en avklart plan.
Se [konfigurasjonsoversikten](konfigurasjon.md) og [avhengighetskartet](../arkitektur/arkitektur.md).
