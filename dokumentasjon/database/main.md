### Databaseskjema

#### Person og bestilling

```mermaid
erDiagram
    person ||--o{ person_fnr: har
    person ||--o{ person_audit: har
    person ||--o{ bestillinger: har
    bestillinger_batch |o--o{ bestillinger: inneholder
    person {
    }
    person_fnr {
        text fnr "fnr/pid/dnr etc"
        date gjelder_fom "Når byttet aktøren fra det andre til dette(?)"
    }
    person_audit {
        text tag "Maskinlesbar kategorisering av auditlinjer, for f.eks. å kunne finne alle bestillinger"
        text informasjon "Menneskelesbar tekst som beskriver endringen"
        text bruker "Bruker eller system som forårsaket endringen"
    }
    bestillinger {
        smallint aar
        text fnr "Fødselsnummer brukt i bestilling."
    }
    bestillinger_batch {
        text status "Tracker status på bestilling mot skatteetaten"
        text bestillingreferanse "Referanse mottatt fra skatteetaten ved registrert bestilling"
        text dialogreferanse "Referanse mottatt fra skatteetaten, til dialogporten"
    }
```

Designnotater:

- vi kan få skattekort uten skattekort-deler. Det gjelder typisk personer som har tilleggsopplysninger.
- tanken bak skattekort-tabellen er at den er immuterbar. Vi endrer ikke eksisterende innslag, vi lager nye.

## Relasjonsmodell

```mermaid
erDiagram
    Forespoersler ||--|{ Abonnementer: ""
    Personer ||--|{ Skattekort: ""
    Foedselsnummer }|--|| Personer: ""
    Bestillinger |{--o| Bestillingsbatcher: ""
    Abonnementer ||--o| Utsendinger: ""
    Personer ||--|{ Abonnementer: ""
    Forespoersler {
        string request
        string forsystem
    }

    Foedselsnummer {
        string fnr UK
        date gjelder_fom
    }

    Abonnementer {
        string fnr
    }

    Skattekort {
    }

    Bestillinger {
        string fnr UK
        int inntektsaar
    }

    Bestillingsbatcher {
        string bestillingsreferanse UK
        int inntektsaar
    }

    Utsendinger {
        string fnr
        string forsystem
        int inntektsaar
    }
```

#### Skattekort (skisse)

```mermaid
erDiagram
    person ||--o{ skattekort: har
    person ||--o{ skattekort_data: har
    skattekort ||--o{ forskuddstrekk: har
    skattekort ||--o{ skattekort_tilleggsopplysninger: har
    skattekort {
        smallint inntektsaar
        date utstedt_dato "Felt satt av skatteetaten"
        text identifikator "Skatteetatens id for skattekortet"
        text kilde "Angir kilde for skattekortet: skatt, syntetisert, manuelt"
        timestamptz opprettet "Vår egen dato for oppretting av skattekortet"
    }
    forskuddstrekk {
        text trekk_kode "PENSJON, PENSJON_FRA_NAV, etc. Satt av skatteetaten, angir bruksområde for forskuddstrekken"
        text type "frikort, tabell, prosent"
        int frikort_beloep "Beløpsgrense for frikort, eller null dersom ikke frikort eller ingen grense"
        text tabell_nummer "Tabellangivelse. Tabeller oppdateres en gang pr år"
        decimal prosentsats "Prosentsats, kan være en fraksjonell prosent for kildeskatt"
        decimal antall_mnd_for_trekk "Antall måneder trekk skal utføres for, typisk 12, 10.5"
    }
    skattekort_tilleggsopplysninger {
        text opplysning "kildeskattPaaPensjon, kildeskattPaaLoenn etc"
    }
    skattekort_data {
        timestamptz created
        text data_mottatt "Payload mottatt, uten behandling. For debuggingsformål"
        smallint aar
        timestamptz opprettet
    }

```
