# Krav til å kunne sette en applikasjon i produksjon

- Det må være mulig å enkelt kunne finne og gjenskape eventuelle feature switcher som må konfigureres i unleash fra koden
- Backup må være konfigurert, og de som har ansvar for å drifte løsningen har forsøkt å gjøre en restore i henhold til dokumentasjonen
- Løsningen må ha dokumentasjon av tjenester som tilbys og som konsumeres. Løsningen bør legge til rette for å kunne komme i kontakt med brukere av eksponerte tjenester effektivt slik at endringer i infrastruktur kan håndteres effektit.
- Dersom løsningen bruker feature switches må løsningen forsøkes deployet med "ødelagt" feature-switch service. Applikasjonen skal ikke krasje, og feature switcher skal ha en sannsynlig default.
- Det skal eksistere
  - ROS-analyse
  - Utfylte etterlevelseskrav
  - Driftshåndbok