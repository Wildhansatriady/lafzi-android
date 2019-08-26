package org.lafzi.lafzi.models;

import android.provider.BaseColumns;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by alfat on 21/04/17.
 */

public class Index implements BaseColumns {

    public static final String VOCAL_INDEX_TABLE_NAME = "vocal_index";
    public static final String NONVOCAL_INDEX_TABLE_NAME = "nonvocal_index";
    public static final String TERM = "term";
    public static final String POST = "post";
    public static final String ID = "_id";
    public static final String NGRAMS = "ngrams";
    public static final String SUGGEST_VOCAL_INDEX = "suggest_vocal_index";


    private final String term;
    private final JSONObject post;
    private List<String> key = new ArrayList<>();
    public Index(String term,
                 JSONObject post) {
        this.term = term;
        this.post = post;
    }

    public String getTerm() {
        return term;
    }

    public JSONObject getPost() {
        return post;
    }

    public List<String> getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key.add(key);
    }

}
