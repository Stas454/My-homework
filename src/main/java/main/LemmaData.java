
package main;

public class LemmaData implements Comparable<LemmaData> {

    int idLemma;
    int quantityPagesWithLemma;

    LemmaData(int idLemma, int quantityPagesWithLemma) {
        this.idLemma = idLemma;
        this.quantityPagesWithLemma = quantityPagesWithLemma;
    }

    @Override
    public int compareTo(LemmaData lemmaData) {
        return idLemma - lemmaData.idLemma;
    }

    @Override
    public String toString() {
        return "lemma id: " + idLemma;
    }

}
