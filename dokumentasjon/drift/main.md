# Driftshåndbok

### Logging

Feilmeldinger og infomeldinger som ikke innheholder sensitive data logges til `Grafana Loki`.
https://grafana.nav.cloud.nais.io/a/grafana-lokiexplore-app/explore/service_name/sokos-skattekort/logs?var-filters=service_name%7C%3D%7Csokos-skattekort

- For Produksjon
    * Datasource: prod-gcp-loki

- For Dev
    * Datasource: dev-gcp-loki

Sensitive meldinger logges til `Securelogs` [Team Logs](https://console.cloud.google.com/logs/query;query=sokos%20skattekort).

- For Produksjon
    * Project: okonomi-prod

- For Dev
    * Project: okonomi-dev

### Alarmer

Vi bruker [Grafana alerts](https://grafana.nav.cloud.nais.io/alerting/list?search=sokos-skattekort) for å sette opp alarmer.

### Grafana

[Grafana prod](https://grafana.nav.cloud.nais.io/d/8c975fff-46f6-4eb3-8dac-df4a47425f3a/sokos-skattekort?var-interval=2m&orgId=1&from=now-12h&to=now&timezone=browser&var-datasource=000000021&var-app=sokos-skattekort&var-namespace=okonomi&var-memory_pool_heap=$__all&refresh=30s)
[Grafana test](https://grafana.nav.cloud.nais.io/d/8c975fff-46f6-4eb3-8dac-df4a47425f3a/sokos-skattekort?var-interval=2m&orgId=1&from=now-12h&to=now&timezone=browser&var-datasource=000000020&var-app=sokos-skattekort&var-namespace=okonomi&var-memory_pool_heap=$__all&refresh=30s)

### Foreslått rutine for drift ved ekstra overvåkning av applikasjonen, f.eks. ved årsskifte

- Dersom disse punktene tilsier at noe må gjøres kan det bli kluss om flere forsøker å rette samme feil. Flagg korrektive tiltak i slack-kanalen slik at vi ikke går i beina på hverandre.
- sjekk "eldste bestillinger" og "timer siden siste skattekort ble lagret" i grafana: https://grafana.nav.cloud.nais.io/d/8c975fff-46f6-4eb3-8dac-df4a47425f3a/sokos-skattekort?var-interval=2m&orgId=1&from=now-12h&to=now&timezone=browser&var-datasource=000000025&var-app=&var-namespace=okonomi&var-memory_pool_heap=$__all&refresh=30s
- ```select * from bestillingsbatcher where status = 'FEILET';```
  disse må kanskje re-kjøres. Vurder årsak, og oppdater eventuelt status-feltet til "NY". Vurder om det blir kollisjoner dersom batchen er gammel/vi henter inn utdaterte skattekort.
- Sjekk cpu-bruk på databaseserver. Det har skjedd at applikasjonen spinner av gårde, og det synes ved at databasen bruker jevnt mye CPU
  tiltak - logg kjørende query på databaseserveren, logg situasjonen i driftshåndboka, stopp/restart kjørende poder med "kubectl delete pod <podnavn>".
- Sjekk backout-køer: http://10.33.43.58:8000/mq/admin/queues/MPLS02/P_SKATT*
  backoutkøene skal ikke ha mye data. om det skjer må situasjonen vurderes. husk å oppdatere driftshåndbok om relevant.
- Sjekk slack: #team-mob-alerts-prod
- Logg at disse sjekkene er gjort slik at ikke andre sløser bort tid ved å repetere de


---

- [Kjente feilsituasjoner](feil.md)
- [Rutine for redeploy](redeploy.md)
- [Konfigurasjon](konfigurasjon.md)
- [Import av bestillings-filer](bestillingsfiler.md)