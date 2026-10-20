package com.scholia.reader.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastOpened DESC")
    fun observeRecent(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: Long): Book?

    @Query("SELECT * FROM books WHERE id = :id")
    fun observe(id: Long): Flow<Book?>

    @Query("SELECT * FROM books WHERE uri = :uri")
    suspend fun findByUri(uri: String): Book?

    @Insert
    suspend fun insert(book: Book): Long

    @Query("UPDATE books SET lastOpened = :time WHERE id = :id")
    suspend fun touch(id: Long, time: Long = System.currentTimeMillis())

    @Query("UPDATE books SET lastLocation = :location, progress = :progress, positionLabel = :label WHERE id = :id")
    suspend fun updatePosition(id: Long, location: String, progress: Float, label: String)

    @Query("UPDATE books SET title = :title, author = :author WHERE id = :id")
    suspend fun updateMetadata(id: Long, title: String, author: String)

    @Delete
    suspend fun delete(book: Book)
}

@Dao
interface HighlightDao {
    @Query("SELECT * FROM highlights WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observeForBook(bookId: Long): Flow<List<Highlight>>

    @Query("SELECT * FROM highlights WHERE id = :id")
    suspend fun get(id: Long): Highlight?

    @Query(
        "SELECT bookId, COUNT(*) AS count, " +
            "SUM(CASE WHEN note IS NOT NULL AND note != '' THEN 1 ELSE 0 END) AS notes " +
            "FROM highlights GROUP BY bookId"
    )
    fun observeCounts(): Flow<List<HighlightCount>>

    @Query(
        "SELECT h.bookId AS bookId, h.selectedText AS text, b.title AS title, b.author AS author, " +
            "h.createdAt AS createdAt FROM highlights h JOIN books b ON b.id = h.bookId " +
            "ORDER BY h.createdAt DESC LIMIT 1"
    )
    fun observeLatest(): Flow<LatestHighlight?>

    @Insert
    suspend fun insert(highlight: Highlight): Long

    @Update
    suspend fun update(highlight: Highlight)

    @Query("UPDATE highlights SET note = :note WHERE id = :id")
    suspend fun updateNote(id: Long, note: String?)

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun delete(id: Long)
}

class Converters {
    @TypeConverter fun formatToString(f: BookFormat): String = f.name
    @TypeConverter fun stringToFormat(s: String): BookFormat = BookFormat.valueOf(s)
}

@Database(entities = [Book::class, Highlight::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun books(): BookDao
    abstract fun highlights(): HighlightDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "scholia.db").build()
    }
}
