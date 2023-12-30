package io.github.sauvio.ocr.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.Nullable
import androidx.preference.PreferenceManager

/**
 * A util class for SharedPreferences
 */
class SpUtil private constructor() {

    private var mContext: Context? = null
    private var mPref: SharedPreferences? = null

    init {
        // private constructor
    }

    companion object {
        private var mInstance: SpUtil? = null

        /**
         * A factory method for
         *
         * @return a instance of this class
         */
        fun getInstance(): SpUtil {
            if (null == mInstance) {
                synchronized(SpUtil::class.java) {
                    if (null == mInstance) {
                        mInstance = SpUtil()
                    }
                }
            }
            return mInstance!!
        }
    }

    /**
     * initialization of context, use only first time later it will use this again and again
     *
     * @param context app context: first time
     */
    fun init(context: Context) {
        if (mContext == null) {
            mContext = context
        }
        if (mPref == null) {
            mPref = PreferenceManager.getDefaultSharedPreferences(context)
        }
    }

    fun putString(key: String, value: String) {
        val editor = mPref!!.edit()
        editor.putString(key, value)
        editor.apply()
    }

    fun putLong(key: String, value: Long) {
        val editor = mPref!!.edit()
        editor.putLong(key, value)
        editor.apply()
    }

    fun putInt(key: String, value: Int) {
        val editor = mPref!!.edit()
        editor.putInt(key, value)
        editor.apply()
    }

    fun putFloat(key:String, value: Float) {
        val editor = mPref!!.edit()
        editor.putFloat(key, value)
    }

    fun putBoolean(key: String, value: Boolean) {
        val editor = mPref!!.edit()
        editor.putBoolean(key, value)
        editor.apply()
    }

    fun getBoolean(key: String): Boolean {
        return mPref!!.getBoolean(key, false)
    }

    fun getBoolean(key: String, def: Boolean): Boolean {
        return mPref!!.getBoolean(key, def)
    }

    @Nullable
    fun getString(key: String): String? {
    return mPref!!.getString(key, "")
    }

    @Nullable
    fun getString(key: String, def: String): String? {
    return mPref!!.getString(key, def)
    }

    fun getStringSet(key: String, def: Set<String>): Set<String>? {
        return mPref!!.getStringSet(key, def)
    }

    fun getLong(key: String): Long {
        return mPref!!.getLong(key, 0)
    }

    fun getLong(key: String, defInt: Int): Long {
        return mPref!!.getLong(key, defInt.toLong())
    }

    fun getFloat(key: String): Float {
        return mPref!!.getFloat(key, 0f)
    }

    fun getFloat(key: String, defFloat: Float): Float {
        return mPref!!.getFloat(key, defFloat)
    }

    fun getInt(key: String): Int {
        return mPref!!.getInt(key, 0)
    }

    fun getInt(key: String, defInt: Int): Int {
        return mPref!!.getInt(key, defInt)
    }

    fun contains(key: String): Boolean {
        return mPref!!.contains(key)
    }

    fun remove(key: String) {
        val editor = mPref!!.edit()
        editor.remove(key)
        editor.apply()
    }

    fun clear() {
        val editor = mPref!!.edit()
        editor.clear()
        editor.apply()
    }
}
