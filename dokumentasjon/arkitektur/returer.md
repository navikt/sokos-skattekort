# Returer gitt i forskjellige situasjoner

```mermaid
stateDiagram-v2
    state "Forespørsel mottatt" as fm
    state "Endringer etterspørres periodisk" as endring
    state "FNR ikke godtatt" as not_fnr
    state "Ingenting returneres - normaltilstand" as ok
    state "Bestilling gjort mot skatt" as best_sendt
    state "Bestilling feiler" as best_feiler
    state "Ingenting returneres - errorlogg" as error
    state "Henting av resultat feiler" as henting_feiler
    state "Henting av resultat ok" as henting_ok
    state "Ingen gyldige trekkoder for NAV, ingen tilleggsinfo" as tomt_uten_tillegg
    state "ikkeSkattekort" as ikkeSkattekort
    state "ikkeTrekkpliktig" as ikkeTrekkplikt
    state "Tilleggsinfo oppgitt" as tomt_med_tillegg
    state "Syntetisk skattekort returneres for svalbard, tiltakssone" as synt
    state "Frikort uten beløpsgrense returneres" as synt_frikort
    state "Skattekort returneres" as retur

    [*] --> fm
    [*] --> endring
    endring --> best_sendt
    fm --> not_fnr
    not_fnr --> ok
    fm --> best_sendt
    best_sendt --> best_feiler
    best_feiler --> error
    best_sendt --> henting_feiler
    henting_feiler --> error
    best_sendt --> henting_ok
    henting_ok --> ikkeSkattekort
    ikkeSkattekort --> ok
    henting_ok --> tomt_uten_tillegg
    tomt_uten_tillegg --> ok
    ikkeSkattekort --> tomt_med_tillegg
    tomt_med_tillegg --> synt
    henting_ok --> retur
    henting_ok --> ikkeTrekkplikt
    ikkeTrekkplikt --> synt_frikort
    ok --> [*]
    error --> [*]
    retur --> [*]
    synt --> [*]
    synt_frikort --> [*]
```