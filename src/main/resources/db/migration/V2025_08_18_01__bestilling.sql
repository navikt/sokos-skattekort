SET lock_timeout = '5s';

CREATE TABLE IF NOT EXISTS BESTILLING
(
    ID                       NUMBER(38),
    FNR                      CHAR(11) not null,
    INNTEKTSAAR              CHAR(4)  not null,
    SKATTEKORT_IDENTIFIKATOR NUMBER(38),
    DATO_MOTTATT             DATE,
    DATO_OPPDATERT           DATE,
    DATO_ABONNENT            DATE,
    DATO_SENDT_ABONNENT      DATE,
    RESULTAT_PAA_FORESPORSEL VARCHAR2(50),
    DATO_RETURNERT_OS        DATE,
    DATA_MOTTATT             XMLTYPE,
    ERSTATTES_AV_FNR         CHAR(11)     default NULL,
    DATO_OPPRETTET           TIMESTAMP(6) default current_timestamp,
    constraint PK_BESTILLING
        primary key (FNR, INNTEKTSAAR)
)