package com.jarvismobile.ia;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class JarvisDbHelper extends SQLiteOpenHelper {
    private static final String DB = "jarvis.db";

    public JarvisDbHelper(Context context) {
        super(context, DB, null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE history(id INTEGER PRIMARY KEY AUTOINCREMENT,role TEXT,content TEXT,media TEXT,created_at INTEGER)");
        db.execSQL("CREATE TABLE settings(k TEXT PRIMARY KEY,v TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public void add(String role, String content, String media) {
        ContentValues values = new ContentValues();
        values.put("role", role);
        values.put("content", content);
        values.put("media", media);
        values.put("created_at", System.currentTimeMillis());
        getWritableDatabase().insert("history", null, values);
    }

    public Cursor history() {
        return getReadableDatabase().query("history", null, null, null, null, null, "created_at ASC");
    }

    public void put(String key, String value) {
        ContentValues values = new ContentValues();
        values.put("k", key);
        values.put("v", value);
        getWritableDatabase().insertWithOnConflict(
                "settings", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public String get(String key) {
        Cursor cursor = getReadableDatabase().query(
                "settings", new String[]{"v"}, "k=?", new String[]{key},
                null, null, null);
        try {
            return cursor.moveToFirst() ? cursor.getString(0) : "";
        } finally {
            cursor.close();
        }
    }

    public void clearHistory() {
        getWritableDatabase().delete("history", null, null);
    }
}