package fr.randommediapicker

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.randommediapicker.data.*
import fr.randommediapicker.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryIntegrationTest {
    private lateinit var database: LibraryDatabase
    private lateinit var repository: LibraryRepository
    @Before fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        repository = LibraryRepository(context, database)
    }
    @After fun close() { database.close() }

    @Test fun parentSelectionAndChildOverrideSurviveDatabaseRoundTrip() = runTest {
        val dao = database.libraryDao()
        dao.insertFolders(listOf(
            FolderEntity("p", null, "Parent", 0, 1, 2),
            FolderEntity("c", "p", "Enfant", 1, 1, 1),
        ))
        repository.select("p", true)
        repository.select("c", false)
        val selection = FolderSelection.effective(dao.folders().map { it.node() })
        assertTrue(selection.getValue("p"))
        assertFalse(selection.getValue("c"))
    }

    @Test fun inaccessibleMediaAreNotReturnedAndUrisAreUnique() = runTest {
        val dao = database.libraryDao()
        val file = MediaFile("content://a", "p", "a.jpg", "a.jpg", "image/jpeg", MediaKind.IMAGE, 1, 0)
        dao.insertMedia(listOf(file, file))
        assertEquals(1, dao.observeMedia().first().size)
        dao.invalidate(file.id)
        assertTrue(dao.observeMedia().first().isEmpty())
    }
}
