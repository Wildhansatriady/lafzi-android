package org.lafzi.lafzi.filters;

import android.app.Activity;
import android.database.sqlite.SQLiteDatabase;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.View;
import android.widget.Filter;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.lafzi.android.R;
import org.lafzi.lafzi.adapters.AyatAdapter;
import org.lafzi.lafzi.helpers.ArabicHelper;
import org.lafzi.lafzi.helpers.database.DbHelper;
import org.lafzi.lafzi.helpers.database.dao.AyatQuranDao;
import org.lafzi.lafzi.helpers.database.dao.AyatQuranDaoFactory;
import org.lafzi.lafzi.helpers.database.dao.IndexDao;
import org.lafzi.lafzi.helpers.database.dao.IndexDaoFactory;
import org.lafzi.lafzi.helpers.database.dao.QuranTextDao;
import org.lafzi.lafzi.helpers.database.dao.QuranTextDaoFactory;
import org.lafzi.lafzi.helpers.preferences.Preferences;
import org.lafzi.lafzi.models.AyatQuran;
import org.lafzi.lafzi.models.FoundDocument;
import org.lafzi.lafzi.models.HasilAkhirModel;
import org.lafzi.lafzi.models.QuranText;
import org.lafzi.lafzi.utils.HighlightUtil;
import org.lafzi.lafzi.utils.QueryUtil;
import org.lafzi.lafzi.utils.SearchUtil;
import org.lafzi.lafzi.utils.TrigramUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import info.debatty.java.stringsimilarity.CharacterSubstitutionInterface;
import info.debatty.java.stringsimilarity.Levenshtein;
import info.debatty.java.stringsimilarity.WeightedLevenshtein;

import static org.lafzi.lafzi.utils.Constants.ADD_LEFT_RIGHT_CROSS;
import static org.lafzi.lafzi.utils.Constants.ADD_RIGHT_LEFT_CROSS;
import static org.lafzi.lafzi.utils.Constants.FAIR_ADDING;
import static org.lafzi.lafzi.utils.Constants.FAIR_CROSS;
import static org.lafzi.lafzi.utils.Constants.LEFT_ADDING;
import static org.lafzi.lafzi.utils.Constants.LEFT_CROSS;
import static org.lafzi.lafzi.utils.Constants.MID_ADDING;
import static org.lafzi.lafzi.utils.Constants.RIGHT_ADDING;
import static org.lafzi.lafzi.utils.Constants.RIGHT_CROSS;

/**
 * Created by alfat on 21/04/17.
 */

public class AyatAdapterFilter extends Filter {

    private final AyatQuranDao ayatQuranDao;
    private final IndexDao indexDao;
    private final QuranTextDao quranTextDao;

    private final Activity activity;
    private final AyatAdapter adapter;

    private int maxScore;
    private static final int GRAM_COUNT = 3;

    public AyatAdapterFilter(final Activity activity, final AyatAdapter adapter){
        final DbHelper dbHelper = DbHelper.getInstance();
        final SQLiteDatabase db = dbHelper.getReadableDatabase();

        ayatQuranDao = AyatQuranDaoFactory.createAyatDao(db);
        indexDao = IndexDaoFactory.createIndexDao(db);
        quranTextDao = QuranTextDaoFactory.createQuranTextDao(db);

        this.adapter = adapter;
        this.activity = activity;
    }

    @Override
    protected FilterResults performFiltering(CharSequence constraint) {
        List<AyatQuran> ayatQurans;
        if (!ArabicHelper.textIsLatin(constraint)) {
            ayatQurans = searchArabic(constraint);
            final FilterResults results = new FilterResults();
            results.values = ayatQurans;
            results.count = ayatQurans.size();
            return results;
        } else {
            return searchLatin(constraint);
        }
    }

    private List<AyatQuran> searchArabic(CharSequence constraint) {
        List<QuranText> quranTexts = quranTextDao.searchArabic(constraint.toString());
        List<AyatQuran> results = new ArrayList<>();
        for (QuranText quranText : quranTexts) {
            final AyatQuran aq = ayatQuranDao.getAyatQuran(quranText.getDocId(), true);
            aq.highlightPositions = HighlightUtil.longestHighlightLookforward(quranText.getPos(), 6);
            results.add(aq);
        }
        return results;
    }

    private FilterResults searchLatin(CharSequence constraint) {
        double maxTreshold = 0.0;
        final IndexDao indexDao;
        final AyatQuranDao ayatQuranDao;
        final DbHelper dbHelper = DbHelper.getInstance();
        final SQLiteDatabase db = dbHelper.getReadableDatabase();
        indexDao = IndexDaoFactory.createIndexDao(db);
        ayatQuranDao = AyatQuranDaoFactory.createAyatDao(db);
        Map<Integer, FoundDocument> matchedDocs = null;        double threshold = 0.9;
        boolean isVocal = Preferences.getInstance().isVocal();
        final String queryFinal = QueryUtil.normalizeQuery(constraint.toString(), isVocal);
        maxScore = queryFinal.length() - 2;

        do {
            try {
                matchedDocs = SearchUtil.searchLafziPlus(
                        queryFinal,
                        isVocal,
                        true,
                        true,
                        threshold,
                        indexDao);
            } catch (JSONException e) {
                Log.e("error", "Index JSON cannot be parsed", e);
            }
            threshold -= 0.1;
        } while ((matchedDocs.size() < 1) && (threshold >= 0.7));

        List<FoundDocument> matchedDocsValue;
        List<AyatQuran> ayatQurans = new ArrayList<>();

        if (matchedDocs.size() > 0) {

            HighlightUtil.highlightPositions(matchedDocs, isVocal, ayatQuranDao);
            matchedDocsValue = getMatchedDocsValues(matchedDocs);
            Collections.sort(matchedDocsValue, new Comparator<FoundDocument>() {
                @Override
                public int compare(FoundDocument o1, FoundDocument o2) {
                    if (o1.getScore() == o2.getScore()) {
                        return o1.getAyatQuranId() - o2.getAyatQuranId();
                    }

                    return o1.getScore() < o2.getScore() ? 1 : -1;
                }
            });

            maxTreshold = matchedDocsValue.get(0).getScore() / TrigramUtil.nGramsSorted(GRAM_COUNT, queryFinal).size();

            ayatQurans = getMatchedAyats(matchedDocsValue);
        }
        Map<Integer, FoundDocument> unfilteredDocs = SearchUtil.getUnfilteredDocs();
        String suggestion = "";
        if (unfilteredDocs.size() != 0) {
            Map<Integer, FoundDocument> temp = new HashMap<>();
            int currentMaxTrigramCounts = -1;
            for (Map.Entry<Integer, FoundDocument> data : unfilteredDocs.entrySet()) {
                int tempValue = data.getValue().getMatchedTermsOrderScore();
                if (tempValue >= currentMaxTrigramCounts) {
                    int values = Math.abs(tempValue - currentMaxTrigramCounts);
                    if (values <= 5) {
                        currentMaxTrigramCounts = data.getValue().getMatchedTermsOrderScore();
                        temp.put(data.getKey(), data.getValue());
                    } else {
                        temp.clear();
                        currentMaxTrigramCounts = data.getValue().getMatchedTermsOrderScore();
                        temp.put(data.getKey(), data.getValue());
                    }
                }
            }
            suggestion = getSuggestion(temp, queryFinal, indexDao);
        }

        HasilAkhirModel hasilAkhirModel = new HasilAkhirModel(suggestion, ayatQurans);
        hasilAkhirModel.setScore(maxTreshold);
        final FilterResults results = new FilterResults();
        results.values = hasilAkhirModel;
        results.count = hasilAkhirModel.getAyatQurans().size();

        return results;
    }

    public static String getSuggestion(Map<Integer, FoundDocument> lineTerbanyakList, String query, IndexDao indexDao) {

        final List<String> ngrams = TrigramUtil.nGramsSorted(GRAM_COUNT, query);

        Map<Double, String> suggestionList = new HashMap<>();
        Levenshtein calc = new Levenshtein();

        for (Map.Entry<Integer, FoundDocument> lineTerbanyak : lineTerbanyakList.entrySet()) {


            int quranId = lineTerbanyak.getValue().getAyatQuranId();
            JSONArray array = indexDao.getNgramById(quranId);
            List<Integer> urutan = lineTerbanyak.getValue().getLis();

            int max = urutan.get(urutan.size() - 1);
            int min = urutan.get(0);
            int candidateLength = max - min;
            int inputTrigramLength = ngrams.size() - 1;

            int selisih = Math.abs(inputTrigramLength - candidateLength);
            for (int i = 0; i < 9; i++) {
                int tempMax = -1;
                int tempMin = -1;
                switch (i) {
                    case FAIR_ADDING:
                        for (int j = selisih; j >= 0; j--) {
                            tempMin = min - j;
                            tempMax = max + j;

                            if (tempMin < 0) {
                                tempMin = 0;
                            }

                            if (tempMax > array.length() - 1) {
                                tempMax = array.length() - 1;
                            }
                            addCandidate(tempMin, tempMax, array, query, suggestionList);
                        }
                        break;
                    case LEFT_ADDING:
                        for (int j = selisih; j >= 0; j--) {

                            tempMin = min - j;
                            tempMax = max;

                            if (tempMin < 0) {
                                tempMin = 0;
                            }
                            addCandidate(tempMin, tempMax, array, query, suggestionList);
                        }
                        break;
                    case RIGHT_ADDING:
                        for (int j = selisih; j >= 0; j--) {

                            tempMin = min;
                            tempMax = max + j;

                            if (tempMax > array.length() - 1) {
                                tempMax = array.length() - 1;
                            }
                            addCandidate(tempMin, tempMax, array, query, suggestionList);
                        }
                        break;
                    case MID_ADDING:
                        //mid adding
                        tempMax = max;
                        tempMin = min;
                        addCandidate(tempMin, tempMax, array, query, suggestionList);
                        break;
                    case LEFT_CROSS:
                        for (int j = selisih; j >= 0; j--) {

                            tempMin = min + j;
                            tempMax = max;

                            if (tempMin > array.length() - 1) {
                                tempMin = array.length() - 1;
                            }
                            addCandidate(tempMin, tempMax, array, query, suggestionList);
                        }
                        break;
                    case RIGHT_CROSS:
                        for (int j = selisih; j >= 0; j--) {

                            tempMin = min;
                            tempMax = max - j;

                            if (tempMax < 0) {
                                tempMax = 0;
                            }
                            addCandidate(tempMin, tempMax, array, query, suggestionList);
                        }
                        break;
                    case FAIR_CROSS:
                        for (int j = selisih; j >= 0; j--) {

                            tempMin = min + j;
                            tempMax = max - j;

                            if (tempMax < 0) {
                                tempMax = 0;
                            }

                            if (tempMin > array.length() - 1) {
                                tempMin = array.length() - 1;
                            }
                            addCandidate(tempMin, tempMax, array, query, suggestionList);
                        }
                        break;
                    case ADD_RIGHT_LEFT_CROSS:
                        for (int j = selisih; j >= 0; j--) {

                            tempMin = min + j;
                            tempMax = max + j;

                            if (tempMin > array.length() - 1) {
                                tempMin = array.length() - 1;
                            }

                            if (tempMax > array.length() - 1) {
                                tempMax = array.length() - 1;
                            }
                            addCandidate(tempMin, tempMax, array, query, suggestionList);
                        }
                        break;
                    case ADD_LEFT_RIGHT_CROSS:
                        for (int j = selisih; j >= 0; j--) {

                            tempMin = min - j;
                            tempMax = max - j;

                            if (tempMax < 0) {
                                tempMax = 0;
                            }

                            if (tempMin < 0) {
                                tempMin = 0;
                            }
                            addCandidate(tempMin, tempMax, array, query, suggestionList);
                        }
                        break;
                }

            }


        }
        String finalSuggestion = "";
        double lowestEditDistance = 999;
        List<Double> tampungEdit = new ArrayList<>();
        double avgDistance = 0;
        for (Map.Entry<Double, String> data : suggestionList.entrySet()) {
            if (data.getKey() < lowestEditDistance) {
                finalSuggestion = data.getValue();
                lowestEditDistance = data.getKey();
            }
            //if(data.getKey()){
            avgDistance += data.getKey();
            tampungEdit.add(data.getKey());
            //}
        }
        return finalSuggestion;
    }

    private List<FoundDocument> getMatchedDocsValues(final Map<Integer, FoundDocument> matchedDocs){
        final List<FoundDocument> values = new LinkedList<>();
        for (Map.Entry<Integer, FoundDocument> entry : matchedDocs.entrySet()){
            values.add(entry.getValue());
        }

        return values;
    }

    @Override
    protected void publishResults(CharSequence constraint, FilterResults results) {
        adapter.clear();
        final HasilAkhirModel data = (HasilAkhirModel) results.values;

        ProgressBar pb = (ProgressBar) activity.findViewById(R.id.searching_progress_bar);
        pb.setVisibility(View.GONE);

        final SearchView searchView = (SearchView) activity.findViewById(R.id.search);
        searchView.clearFocus();
        TextView tvResultSuggestion = (TextView) activity.findViewById(R.id.result_suggestion);
        final TextView resultCounter = (TextView) activity.findViewById(R.id.result_counter);

        tvResultSuggestion.setText("Suggestion : " + data.getSuggestion());
        if (data.getScore() < 0.97)
            tvResultSuggestion.setVisibility(View.VISIBLE);
        else
            tvResultSuggestion.setVisibility(View.GONE);
        tvResultSuggestion.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                adapter.clear();
                searchView.setQuery(data.getSuggestion(), false);
                ProgressBar pb = (ProgressBar) activity.findViewById(R.id.searching_progress_bar);
                TextView tv = (TextView) activity.findViewById(R.id.empty_result);
                LinearLayout tvSearchHelp = (LinearLayout) activity.findViewById(R.id.search_help);

                pb.setVisibility(View.VISIBLE);
                tv.setVisibility(View.GONE);
                tvSearchHelp.setVisibility(View.GONE);
                adapter.getFilter().filter(data.getSuggestion());
            }
        });
        if (results.count > 0) {
            adapter.addAll(data.getAyatQurans());

            resultCounter.setText(activity.getString(R.string.search_result_count, results.count));
            resultCounter.setVisibility(View.VISIBLE);
        } else {
            adapter.setSuggestion(data.getSuggestion());
            resultCounter.setVisibility(View.GONE);
            TextView tvEmptyResult = (TextView) activity.findViewById(R.id.empty_result);
            tvEmptyResult.setVisibility(View.VISIBLE);
        }

        LinearLayout tvSearchHelp = (LinearLayout) activity.findViewById(R.id.search_help);
        tvSearchHelp.setVisibility(View.GONE);
    }
    private static void addCandidate(int tempMin, int tempMax, JSONArray array, String query, Map<Double, String> suggestionList) {
        String suggestion = getCandidateSuggestion(tempMin, tempMax, array);
        double value = getDistance(query, suggestion);
        //double value = new Levenshtein().distance(query, suggestion);
        suggestionList.put(value, suggestion);
    }
    static String getCandidateSuggestion(int min, int max, JSONArray array) {
        StringBuilder suggestion = new StringBuilder();
        for (int i = min; i < max; i++) {
            try {
                String currTerm = array.getString(i);
                if (i == min) {
                    suggestion.append(currTerm);
                } else {
                    suggestion.append(currTerm.charAt(currTerm.length() - 1));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

        }
        return suggestion.toString();
    }


    static double getDistance(String par1, String par2) {
        WeightedLevenshtein calc = new WeightedLevenshtein(new CharacterSubstitutionInterface() {
            @Override
            public double cost(char c, char c1) {
//                if (c == 'a' && (c1 == 's')) {
//                    return 0.5;
//                } else if (c == 'b' && (c1 == 'v' || c1 == 'n')) {
//                    return 0.5;
//                } else if (c == 'c' && (c1 == 'x' || c1 == 'v')) {
//                    return 0.5;
//                } else if (c == 'd' && (c1 == 's' || c1 == 'f')) {
//                    return 0.5;
//                } else if (c == 'e' && (c1 == 'w') || c1 == 'r') {
//                    return 0.5;
//                } else if (c == 'f' && (c1 == 'd') || c1 == 'g') {
//                    return 0.5;
//                } else if (c == 'g' && (c1 == 'f') || c1 == 'h') {
//                    return 0.5;
//                } else if (c == 'h' && (c1 == 'g') || c1 == 'j') {
//                    return 0.5;
//                } else if (c == 'i' && (c1 == 'u') || c1 == 'o') {
//                    return 0.5;
//                } else if (c == 'j' && (c1 == 'k') || c1 == 'h') {
//                    return 0.5;
//                } else if (c == 'k' && (c1 == 'j') || c1 == 'l') {
//                    return 0.5;
//                } else if (c == 'l' && (c1 == 'k')) {
//                    return 0.5;
//                } else if (c == 'm' && (c1 == 'n')) {
//                    return 0.5;
//                } else if (c == 'n' && (c1 == 'b') || c1 == 'm') {
//                    return 0.5;
//                } else if (c == 'o' && (c1 == 'i') || c1 == 'p') {
//                    return 0.5;
//                } else if (c == 'p' && (c1 == 'o')) {
//                    return 0.5;
//                } else if (c == 'q' && (c1 == 'w')) {
//                    return 0.5;
//                } else if (c == 'r' && (c1 == 'e') || c1 == 't') {
//                    return 0.5;
//                } else if (c == 's' && (c1 == 'a') || c1 == 'd') {
//                    return 0.5;
//                } else if (c == 't' && (c1 == 'r') || c1 == 'y') {
//                    return 0.5;
//                } else if (c == 'u' && (c1 == 'y') || c1 == 'i') {
//                    return 0.5;
//                } else if (c == 'v' && (c1 == 'c') || c1 == 'b') {
//                    return 0.5;
//                } else if (c == 'w' && (c1 == 'q') || c1 == 'e') {
//                    return 0.5;
//                } else if (c == 'y' && (c1 == 't') || c1 == 'u') {
//                    return 0.5;
//                } else if (c == 'z' && (c1 == 'x')) {
//                    return 0.5;
//                } else if (c == 'x' && c1 == '\'') {
//                    return 0;
//                }
                return 1.0;
            }
        });

        return calc.distance(par1.toLowerCase(), par2.toLowerCase());
    }
    private List<AyatQuran> getMatchedAyats(final List<FoundDocument> foundDocuments){

        final List<AyatQuran> ayatQurans = new LinkedList<>();
        for (FoundDocument document : foundDocuments){
            final double relevance = Math.min(Math.floor(document.getScore() / maxScore * 100), 100);

            final AyatQuran ayatQuran = document.getAyatQuran();
            ayatQuran.relevance = relevance;
            ayatQuran.highlightPositions = document.getHighlightPosition();

            ayatQurans.add(ayatQuran);
        }

        return ayatQurans;
    }
}
