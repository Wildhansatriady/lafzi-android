package org.lafzi.lafzi.models;

import java.util.List;

public class HasilAkhirModel {
    private String suggestion;
    private List<AyatQuran> ayatQurans;
    private double score;

    public HasilAkhirModel(String suggestion, List<AyatQuran> ayatQurans) {
        this.suggestion = suggestion;
        this.ayatQurans = ayatQurans;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public String getSuggestion() {
        return suggestion.replace("X", "'");
    }

    public List<AyatQuran> getAyatQurans() {
        return ayatQurans;
    }
}
