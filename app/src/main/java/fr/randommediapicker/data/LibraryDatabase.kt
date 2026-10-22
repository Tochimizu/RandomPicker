package fr.randommediapicker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import fr.randommediapicker.domain.*

@Entity(tableName = "library_root")
data class LibraryRoot(
    @PrimaryKey val id: Int = 1,
    val treeUri: String,
    val displayName: String,
    val lastScanAt: Long,
)

@Entity(tableName = "folders", indices = [Index("parentId")])
data class FolderEntity(
    @PrimaryKey val id: String,
    val parentId: String?,
    val name: String,
    val depth: Int,
    val directCount: Int,
    val totalCount: Int,
    val selection: SelectionOverride = SelectionOverride.INHERIT,
) {
    fun node() = FolderNode(id, parentId, name, depth, directCount, totalCount, selection)
}

@Entity(tableName = "media", indices = [Index("folderId")])
data class MediaFile(
    @PrimaryKey val id: String,
    val folderId: String,
    val name: String,
    val relativePath: String,
    val mimeType: String,
    val kind: MediaKind,
    val sizeBytes: Long,
    val lastModified: Long,
    val accessible: Boolean = true,
)

class DatabaseConverters {
    @TypeConverter fun kindToString(value: MediaKind) = value.name
    @TypeConverter fun stringToKind(value: String) = MediaKind.valueOf(value)
    @TypeConverter fun selectionToString(value: SelectionOverride) = value.name
    @TypeConverter fun stringToSelection(value: String) = SelectionOverride.valueOf(value)
}

@Dao
interface LibraryDao {
    @Query("SELECT * FROM library_root LIMIT 1") fun observeRoot(): Flow<LibraryRoot?>
    @Query("SELECT * FROM library_root LIMIT 1") suspend fun root(): LibraryRoot?
    @Query("SELECT * FROM folders ORDER BY depth, name COLLATE NOCASE") fun observeFolders(): Flow<List<FolderEntity>>
    @Query("SELECT * FROM folders") suspend fun folders(): List<FolderEntity>
    @Query("SELECT * FROM media WHERE accessible = 1 ORDER BY id") fun observeMedia(): Flow<List<MediaFile>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveRoot(root: LibraryRoot)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertFolders(folders: List<FolderEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertMedia(media: List<MediaFile>)
    @Query("UPDATE folders SET selection = :selection WHERE id IN (:ids)")
    suspend fun select(ids: List<String>, selection: SelectionOverride)
    @Query("UPDATE media SET accessible = 0 WHERE id = :id") suspend fun invalidate(id: String)
    @Query("DELETE FROM media") suspend fun clearMedia()
    @Query("DELETE FROM folders") suspend fun clearFolders()
}

@Database(entities = [LibraryRoot::class, FolderEntity::class, MediaFile::class], version = 1, exportSchema = true)
@TypeConverters(DatabaseConverters::class)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
}
