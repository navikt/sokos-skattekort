#Forespørsel av skattekort fra fil

Noen ganger er det nødvendig å håndtere bestillinger av skattekort på bakgrunn av lister med personnummer. Dette er prosessen for å håndtere dette.

1. Finn fil. Det er ok å lagre filer med personnummer på arbeidsstasjoner midlertidig for å utføre prosesser som dette.
2. Lag SQL. Ved go-live fikk vi en fil som inneholdt personnummer som de første 11 tegnene per linje. Dette skriptet vil lage SQL som fungerer:
   ```shell
      cat K231MFSP_Offnr_Sort.txt | cut -c 1-11 | perl -nle "chomp; my \$fnr = \$_; print q/INSERT INTO forespoersel_input (forsystem, inntektsaar, fnr) VALUES ('OS', 2025, '/.\$fnr.q/');/;" - > input.sql
   ```
3. Kjør denne SQL-en mot databasen. Ikke gjør det med en lokal klient, men bruk import-knappen på https://console.cloud.google.com/sql/instances/sokos-skattekort/overview?project=okonomi-prod-7c4c . Husk å velge din egen bruker.
4. Vent til importen har kjørt ferdig, slå så på featureswitch for lesing av forespoersel_input: https://okonomi-unleash-web.iap.nav.cloud.nais.io/projects/default/features/sokos-skattekort.forespoerselinput.enabled
5. Vent til alle radene har blitt lest fra forespoersel_input, slå av feature switch igjen.
5. Slett bøtta SQL-fila ligger i hos Google
6. Slett import-filene fra lokal arbeidsstasjon
7. Slett import-filene fra eventuelle andre lagringsplasser

# Fjerne alle skattekort, abonnementer fra database, behold personer

NB! Denne prosessen, slik den er dokumentert, vil fjerne alle skattekort. Det kan være nødvendig å ta grep før dette gjøres for å finne ut hvilke skattekort som skal foreligge for hvilke personer for hvilke år.

NB! Gå gjennom AuditTag for å se om flere tags bør fjernes.

```sql
begin transaction read write;
truncate table forespoersler cascade;
truncate table bevis_sending;
truncate table bestillingsbatcher cascade;
truncate table skattekort cascade;
truncate table skattekort_data;
truncate table utsendinger;
delete from person_audit where tag in ('BESTILLING_FEILET', 'BESTILLING_SENDT', 'BESTILLING_ETTERLATT','HENTING_AV_SKATTEKORT_FEILET', 'MOTTATT_FORESPOERSEL', 'SKATTEKORTINFORMASJON_MOTTATT', 'SYNTETISERT_SKATTEKORT');
```

Dersom resultatet ser bra ut kan en "commit" gjennomføres.
