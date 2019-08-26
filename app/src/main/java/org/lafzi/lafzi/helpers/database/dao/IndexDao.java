package org.lafzi.lafzi.helpers.database.dao;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lafzi.lafzi.models.Index;

/**
 * Created by alfat on 21/04/17.
 */

public class IndexDao {

    private final SQLiteDatabase db;

    public IndexDao(SQLiteDatabase db) {
        this.db = db;
    }

    public Index getIndexByTrigramTerm(final String term, final boolean isVocal) throws JSONException {

        final String tableName = isVocal ? Index.VOCAL_INDEX_TABLE_NAME : Index.NONVOCAL_INDEX_TABLE_NAME;

        final String[] projection = {
                Index.TERM,
                Index.POST
        };

        final String selection = Index.TERM + " = ?";
        final String[] selectionArgs = { term };

        final Cursor cursor = db.query(tableName, projection, selection, selectionArgs, null, null, null);

        if (cursor.moveToNext()){
            try{
                return new Index(
                        term,
                        readPost(cursor)
                );
            } finally {
                cursor.close();
            }
        }

        return null;
    }

    public JSONArray getNgramById(int id){
        JSONArray ngrams = new JSONArray();
        final String tableName =  Index.SUGGEST_VOCAL_INDEX;
        final String[] projection = {
                Index.NGRAMS
        };
        final String selection = Index.ID + " = ?";
        final String[] selectionArgs = { String.valueOf(id) };
        final Cursor cursor = db.query(tableName, projection, selection, selectionArgs, null, null, null);
        if(cursor.moveToNext()){
            try {
                ngrams = getNgrams(cursor);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return ngrams;
    }
    private JSONObject readPost(final Cursor cursor) throws JSONException {

        final String postStr = cursor
                .getString(cursor.getColumnIndexOrThrow(Index.POST));
        return new JSONObject(postStr);
    }
    private JSONArray getNgrams(final Cursor cursor) throws JSONException {

        final String postStr = cursor
                .getString(cursor.getColumnIndexOrThrow(Index.NGRAMS));
        return new JSONArray(postStr);
    }
}
