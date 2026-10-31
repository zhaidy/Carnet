package com.personal.carnet.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.personal.carnet.domain.Currency

class Converters {
    @TypeConverter
    fun currencyToCode(currency: Currency?): String? = currency?.code

    @TypeConverter
    fun codeToCurrency(code: String?): Currency? = Currency.of(code)
}

@Database(
    entities = [Account::class, Ledger::class, Entry::class, Settlement::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class CarnetDatabase : RoomDatabase() {
    abstract fun dao(): CarnetDao

    companion object {
        fun open(context: Context): CarnetDatabase =
            Room.databaseBuilder(context, CarnetDatabase::class.java, "carnet.db").build()
    }
}
