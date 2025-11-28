# Driftshåndbok

### Logging

Feilmeldinger og infomeldinger som ikke innheholder sensitive data logges til `Grafana Loki`.
https://grafana.nav.cloud.nais.io/a/grafana-lokiexplore-app/explore/service_name/sokos-skattekort/logs?from=now-15m&to=now&var-filters=service_name%7C%3D%7Csokos-skattekort

Sensetive meldinger logges til `Securelogs` [Team Logs](https://console.cloud.google.com/logs/query;query=SEARCH%2528%22%60sokos-skattekort%60%22%2529).

- Filter for Produksjon
    * application:sokos-skattekort AND envclass:p

- Filter for Dev
    * application:sokos-skattekort AND envclass:q

### Kubectl

TBD

### Alarmer

Vi bruker [Grafana alerts](https://docs.nais.io/observability/alerting/how-to/grafana/?h=alert) for å sette opp alarmer.

### Grafana

---

- [Kjente feilsituasjoner](feil.md)
- [Rutine for redeploy](redeploy.md)
- [Teknisk sjekkliste for go-live - hører hjemme annetsteds senere](golive.md)