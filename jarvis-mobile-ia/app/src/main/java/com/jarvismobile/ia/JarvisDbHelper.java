package com.jarvismobile.ia;
import android.content.*; import android.database.sqlite.*; import android.database.Cursor;
public class JarvisDbHelper extends SQLiteOpenHelper{
 private static final String DB="jarvis.db"; public JarvisDbHelper(Context c){super(c,DB,null,1);}
 public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE history(id INTEGER PRIMARY KEY AUTOINCREMENT,role TEXT,content TEXT,media TEXT,created_at INTEGER)");db.execSQL("CREATE TABLE settings(k TEXT PRIMARY KEY,v TEXT)");}
 public void onUpgrade(SQLiteDatabase db,int a,int b){}
 public void add(String r,String c,String m){ContentValues v=new ContentValues();v.put("role",r);v.put("content",c);v.put("media",m);v.put("created_at",System.currentTimeMillis());getWritableDatabase().insert("history",null,v);}
 public Cursor history(){return getReadableDatabase().query("history",null,null,null,null,null,"created_at ASC");}
 public void put(String k,String v){ContentValues x=new ContentValues();x.put("k",k);x.put("v",v);getWritableDatabase().insertWithOnConflict("settings",x,null,SQLiteDatabase.CONFLICT_REPLACE);}
 public String get(String k){Cursor c=getReadableDatabase().query("settings",new String[]{"v"},"k=?",new String[]{k},null,null,null);try{return c.moveToFirst()?c.getString(0):"";}finally{c.close();}}
 public void clearHistory(){getWritableDatabase().delete("history",null,null);}
}