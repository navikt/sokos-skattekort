# Feilsituasjoner

## Feil i forespørsler

## Feil under interaksjon med PDL

### Error running kafka consumer for ${kafkaConfig.topic}, unsubscribing and waiting $DELAY_ON_ERROR_SECONDS seconds for retry

Ukjent situasjon, exception i loggen gir forhåpentligvis mer informasjon.

## Feil under bestilling av skattekort

### Kunne ikke hente accessToken, se sikker log for meldingen som string

Se sikker logg, som meldingen sier. Vi har trøbbel med å hente et maskinporten-token, som er nødvendig for å kunne kommunisere med skatteetaten. Ukjent situasjon, må debugges manuelt.

### Feil fra tokenprovider, Token: $jwtAssertion, Feilmelding: $feilmelding

Vi klarer ikke lage et maskinporten-token, som er en forutsetning for å kommunisere med skatt. Ukjent situasjon, og må debugges manuelt.

### Bestillingsbatch $batchId feilet med UGYLDIG_INNTEKTSAAR. Dette skulle ikke ha skjedd, og batchen må opprettes på nytt. Bestillingene har blitt tatt vare på for å muliggjøre manuell håndtering

Av en eller annen grunn liker ikke skatteetaten bestillingen. Vi får ikke bestille skattekort for neste år før 15. desember, og ikke etter juni for foregående år.

Vurder situasjonen. Veier ut:
- slett bestillingene og batchen. Dette vil potensielt medføre 50% skatt for brukerne det gjelder, og bør godkjennes av noen som jobber i linja
- bestillingsbatchen kan utføres på nytt ved å slette bestillingsbatch-innslaget i databasen

### Bestillingsbatch feilet: ${ex.message}

Dette er en catch-all for feil under oppretting av bestilling. Situasjonen er ukjent, og må debugges manuelt.

### Bestillingsbatch $batchId feilet: ${response.status}

Dette er en generell catch-all for feil under henting av en bestilling. Situasjonen er ukjent, og må debugges manuelt.

## Feil under utsending av skattekort

