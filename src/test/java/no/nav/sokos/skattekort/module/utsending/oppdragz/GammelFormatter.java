package no.nav.sokos.skattekort.module.utsending.oppdragz;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.*;
import static org.apache.commons.lang3.StringUtils.SPACE;
import static org.apache.commons.lang3.StringUtils.leftPad;
import static org.apache.commons.lang3.StringUtils.rightPad;

public class GammelFormatter
{
    private static final String UTSTEDT_DATO_IKKE_SKATTEPLIKT_POSTFIX = "-01-01";
    private Skattekortmelding skattekortmelding;
    private List<Forskuddstrekk> gyldigeForskuddstrekk;
    private boolean erIkkeTrekkPliktig;
    private boolean inneholderSkattekort;
    private String inntektsaar;

    private static DecimalFormat dfProsentsats;
    private static DecimalFormat dfAntallMndTrekk;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator(',');
        dfProsentsats = new DecimalFormat("000.00", symbols);
        dfAntallMndTrekk = new DecimalFormat("00.0", symbols);
    }

    GammelFormatter(Skattekortmelding skattekortmelding, String inntektsaar) {
        this.skattekortmelding = skattekortmelding;
        this.inntektsaar = inntektsaar;
        if (skattekortmelding != null) {
            this.inneholderSkattekort = skattekortmelding.getSkattekort() != null;
            this.erIkkeTrekkPliktig = Resultatstatus.IKKE_TREKKPLIKT.equals(skattekortmelding.getResultatPaaForespoersel());
        }
    }

    String format() {
        StringBuilder frSkattekort = new StringBuilder();
        if ((inneholderSkattekort || erIkkeTrekkPliktig) && finnesGyldigTrekkode()) {
            frSkattekort.append(formaterFnr())
                    .append(formaterResultatPaaForesporsel())
                    .append(rightPad(inntektsaar, 4))
                    .append(formaterUtstedtDato())
                    .append(formaterSkattekortidentifikator())
                    .append(formaterTilleggsopplysning())
                    .append(formaterAntallSkattekortMedIMelding())
                    .append(formaterForskuddstrekk());
        }
        return frSkattekort.toString();
    }

    public Skattekortmelding getSkattekortmelding() {
        return skattekortmelding;
    }

    private String formaterFnr() {
        return rightPad(skattekortmelding.getArbeidstakeridentifikator(), 11);
    }

    private String formaterResultatPaaForesporsel() {
        String resultat = skattekortmelding.getResultatPaaForespoersel().getValue();
        // Maks 40 posisjoner i fixedfield format til OS
        if (resultat.length() > 40) {
            return substring(resultat, 0, 40);
        } else {
            return rightPad(resultat, 40);
        }
    }

    private String formaterUtstedtDato() {
        if (erIkkeTrekkPliktig && !inneholderSkattekort) {
            String utstedtDato = inntektsaar + UTSTEDT_DATO_IKKE_SKATTEPLIKT_POSTFIX;
            return rightPad(utstedtDato, 10);
        } else if (Resultatstatus.IKKE_SKATTEKORT.equals(skattekortmelding.getResultatPaaForespoersel())) {
            return rightPad("", 10);
        }
        return rightPad(skattekortmelding.getSkattekort().getUtstedtDato().toString(), 10);
    }

    private String formaterSkattekortidentifikator() {
        String skattekortidentifikator;
        if ((erIkkeTrekkPliktig && !inneholderSkattekort) || Resultatstatus.IKKE_SKATTEKORT.equals(skattekortmelding.getResultatPaaForespoersel())) {
            skattekortidentifikator = "";
        } else {
            skattekortidentifikator = Long.toString(skattekortmelding.getSkattekort().getSkattekortidentifikator());
        }
        return rightPad(skattekortidentifikator, 10);
    }

    private String formaterTilleggsopplysning() {
        List<Tilleggsopplysning> tilleggopplysninger = skattekortmelding.getTilleggsopplysning();
        return rightPad(tilleggopplysninger.isEmpty() ? "" : filterTilleggsopplysning(tilleggopplysninger), 50);
    }

    private String filterTilleggsopplysning(List<Tilleggsopplysning> tilleggsopplysninger) {
        List<String> brukesTO = new ArrayList<String>();
        tilleggsopplysninger.forEach(
                to -> {
                    if (to.getValue().equals("kildeskattpensjonist")) brukesTO.add("kildeskattpensjonist");
                    if (to.getValue().equals("oppholdPaaSvalbard")) brukesTO.add("oppholdPaaSvalbard");
                }
        );
        return brukesTO.isEmpty() ? "" : brukesTO.get(0);
    }

    private String formaterAntallSkattekortMedIMelding() {
        int antallSkattekort = gyldigeForskuddstrekk.size();
        return rightPad(Integer.toString(antallSkattekort), 1);
    }
    //end-header

    private String formaterForskuddstrekk() {
        StringBuilder sb = new StringBuilder();

        for (Forskuddstrekk skt : gyldigeForskuddstrekk) {
            if (skt instanceof Trekktabell) {
                Trekktabell trekktabell = (Trekktabell) skt;
                sb.append(rightPad("Trekktabell", 12));
                sb.append(rightPad(skt.getTrekkode().getValue(), 55));
                sb.append(rightPad(trekktabell.getTabellnummer(), 4));
                sb.append(rightPad(formaterProsentsats(trekktabell.getProsentsats()), 6));
                sb.append(rightPad("", 7));
                sb.append(rightPad(formaterAntallManederTrekk(trekktabell.getAntallMaanederForTrekk()), 4));

            } else if (skt instanceof Trekkprosent) {
                Trekkprosent trekkprosent = (Trekkprosent) skt;
                sb.append(rightPad("Trekkprosent", 12));
                sb.append(rightPad(trekkprosent.getTrekkode().getValue(), 55));
                sb.append(rightPad("", 4));
                sb.append(rightPad(formaterProsentsats(trekkprosent.getTrekkprosent()), 6));
                sb.append(leftPad("", 7));
                sb.append(rightPad(formaterAntallManederTrekk(trekkprosent.getAntallMaanederForTrekk()), 4));

            } else if (skt instanceof Frikort) {
                Frikort frikort = (Frikort) skt;
                sb.append(rightPad("Frikort", 12));
                sb.append(rightPad(frikort.getTrekkode().getValue(), 55));
                sb.append(rightPad("", 4));
                sb.append(rightPad("", 6));
                sb.append(finnFrikortbeloep(frikort));
                sb.append(rightPad("", 4));
            }
        }
        return sb.toString();
    }

    private String formaterProsentsats(BigDecimal prosentsats) {
        return dfProsentsats.format(prosentsats);
    }

    private String formaterAntallManederTrekk(BigDecimal antallManederTrekk) {
        if (antallManederTrekk == null) {
            return "";
        }
        return dfAntallMndTrekk.format(antallManederTrekk);
    }

    private boolean finnesGyldigTrekkode() {
        gyldigeForskuddstrekk = new ArrayList<>();
        if (erIkkeTrekkPliktig && !inneholderSkattekort) {
            Forskuddstrekk simulertFrikort = new Frikort(Trekkode.LOENN_FRA_NAV, null);
            gyldigeForskuddstrekk.add(simulertFrikort);
        } else {
            List<Forskuddstrekk> forskuddstrekkListe = skattekortmelding.getSkattekort().getForskuddstrekk();
            for (Forskuddstrekk forskuddstrekk : forskuddstrekkListe) {
                Trekkode trekkode = forskuddstrekk.getTrekkode();
                if (Trekkode.LOENN_FRA_NAV.equals(trekkode)
                        || Trekkode.PENSJON_FRA_NAV.equals(trekkode)
                        || Trekkode.UFOERETRYGD_FRA_NAV.equals(trekkode)) {
                    gyldigeForskuddstrekk.add(forskuddstrekk);
                }
            }
        }
        return !gyldigeForskuddstrekk.isEmpty();
    }

    private String finnFrikortbeloep(Frikort frikort) {
        BigDecimal frikortbeloep = frikort.getFrikortbeloep();
        boolean harFrikortbelop = frikortbeloep == null;
        return leftPad(harFrikortbelop ? "" : frikortbeloep.toString(), 7, harFrikortbelop ? SPACE : "0");
    }

}
