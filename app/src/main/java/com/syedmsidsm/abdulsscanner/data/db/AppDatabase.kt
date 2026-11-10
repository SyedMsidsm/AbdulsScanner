package com.syedmsidsm.abdulsscanner.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.syedmsidsm.abdulsscanner.data.db.dao.DocumentDao
import com.syedmsidsm.abdulsscanner.data.db.dao.PageDao
import com.syedmsidsm.abdulsscanner.data.db.dao.QrDao
import com.syedmsidsm.abdulsscanner.data.db.dao.SearchDao
import com.syedmsidsm.abdulsscanner.data.db.dao.SignatureDao
import com.syedmsidsm.abdulsscanner.data.db.dao.TagDao
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentEntity
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentSearchFts
import com.syedmsidsm.abdulsscanner.data.db.entity.DocumentTagCrossRef
import com.syedmsidsm.abdulsscanner.data.db.entity.PageEntity
import com.syedmsidsm.abdulsscanner.data.db.entity.QrHistoryEntity
import com.syedmsidsm.abdulsscanner.data.db.entity.SignatureEntity
import com.syedmsidsm.abdulsscanner.data.db.entity.TagEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        DocumentEntity::class,
        PageEntity::class,
        TagEntity::class,
        DocumentTagCrossRef::class,
        DocumentSearchFts::class,
        QrHistoryEntity::class,
        SignatureEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao
    abstract fun pageDao(): PageDao
    abstract fun tagDao(): TagDao
    abstract fun searchDao(): SearchDao
    abstract fun qrDao(): QrDao
    abstract fun signatureDao(): SignatureDao

    companion object {
        const val DATABASE_NAME = "abdulsscanner.db"

        val STARTER_TAGS = listOf(
            "Business Card",
            "ID Card",
            "Note",
            "PPT",
            "Whiteboard"
        )

        fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Insert starter tags
                        val now = System.currentTimeMillis()
                        for (tag in STARTER_TAGS) {
                            db.execSQL(
                                "INSERT OR IGNORE INTO tags (name, createdAt) VALUES ('$tag', $now)"
                            )
                        }
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        db.execSQL("PRAGMA foreign_keys=ON;")
                    }
                })
                .build()
        }
    }
}
