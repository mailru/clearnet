package clearnet.android

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import clearnet.interfaces.ICacheProvider

open class SqliteCacheProvider(context: Context?, name: String, version: Int) : SQLiteOpenHelper(context, name, null, version), ICacheProvider {
    companion object {
        private const val T_CACHE = "cache"
        private const val C_KEY = "c_key"
        private const val C_VALUE = "c_value"
        private const val C_EXPIRES = "c_expires"
    }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $T_CACHE ($C_KEY TEXT PRIMARY KEY, $C_VALUE TEXT, $C_EXPIRES INTEGER);")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $T_CACHE")
        onCreate(db)
    }

    override fun store(key: String, value: String, expiresAfter: Long) {
        val values = ContentValues(3)
        values[C_KEY] = key
        values[C_VALUE] = value

        var expires = System.currentTimeMillis() + expiresAfter
        if(expires < expiresAfter) expires = Long.MAX_VALUE
        values[C_EXPIRES] = expires
        writableDatabase.insertWithOnConflict(T_CACHE, "", values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun obtain(key: String): String? {
        try {
            val cursor = readableDatabase.rawQuery(
                    "SELECT $C_VALUE FROM $T_CACHE WHERE $C_KEY = ? AND $C_EXPIRES > ${System.currentTimeMillis()} LIMIT 1",
                    arrayOf(key)
            )

            cursor.use {
                return if (cursor.count == 0) {
                    null
                } else {
                    cursor.moveToFirst()
                    cursor.getString(cursor.getColumnIndex(C_VALUE))
                }
            }
        } catch (e: SQLiteException) {
            return null
        }
    }

    fun clean(){
        writableDatabase.execSQL("DELETE FROM $T_CACHE WHERE $C_EXPIRES < ${System.currentTimeMillis()}")
    }

    fun clear(){
        writableDatabase.execSQL("DELETE FROM $T_CACHE")
    }
}
