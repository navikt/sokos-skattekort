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

### Kubectl

TBD

### Alarmer

Vi bruker [Grafana alerts](https://grafana.nav.cloud.nais.io/alerting/list?search=sokos-skattekort) for å sette opp alarmer.

### Grafana

---

- [Kjente feilsituasjoner](feil.md)
- [Rutine for redeploy](redeploy.md)
- [Teknisk sjekkliste for go-live - hører hjemme annetsteds senere](golive.md)
- [Konfigurasjon](konfigurasjon.md)