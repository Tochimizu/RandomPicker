package fr.randommediapicker.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import fr.randommediapicker.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class ScanProgress(val folders: Int = 0, val media: Int = 0, val errors: Int = 0)

class LibraryRepository(private val context: Context, private val db: LibraryDatabase) {
    val dao = db.libraryDao()
    val root = dao.observeRoot()
    val folders = dao.observeFolders()
    val media = dao.observeMedia()

    fun hasPermission(uri: String): Boolean = context.contentResolver.persistedUriPermissions.any {
        it.uri.toString() == uri && it.isReadPermission
    }

    suspend fun select(id: String?, include: Boolean) = db.withTransaction {
        val folders = dao.folders().map { it.node() }
        val ids = if (id == null) folders.map { it.id } else FolderSelection.descendants(id, folders).toList()
        // Room/SQLite bind limits: a large subtree is split into bounded updates.
        ids.chunked(400).forEach {
            dao.select(it, if (include) SelectionOverride.INCLUDE else SelectionOverride.EXCLUDE)
        }
    }

    suspend fun scan(tree: Uri, progress: (ScanProgress) -> Unit) = withContext(Dispatchers.IO) {
        check(hasPermission(tree.toString())) { "Autorisation du dossier perdue. Sélectionnez à nouveau le dossier." }
        val root = DocumentFile.fromTreeUri(context, tree)
            ?: error("Ce fournisseur ne permet pas de parcourir ce dossier.")
        check(root.exists() && root.canRead()) { "Dossier inaccessible. Sélectionnez à nouveau le dossier." }
        val previous = if (dao.root()?.treeUri == tree.toString()) dao.folders().associateBy { it.id } else emptyMap()
        data class Pending(val uri: Uri, val parent: String?, val name: String, val depth: Int, val path: String)
        val queue = ArrayDeque<Pending>()
        queue.add(Pending(root.uri, null, "Fichiers du dossier racine", 0, ""))
        val visited = mutableSetOf<String>()
        val folders = mutableListOf<FolderEntity>()
        val media = linkedMapOf<String, MediaFile>()
        var errors = 0
        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val entry = queue.removeFirst()
            val id = entry.uri.toString()
            if (!visited.add(id)) continue
            var count = 0
            try {
                val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    tree, DocumentsContract.getDocumentId(entry.uri),
                )
                val projection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                )
                val cursor = context.contentResolver.query(childUri, projection, null, null, null)
                    ?: error("Liste indisponible : ${entry.path}")
                cursor.use {
                    while (it.moveToNext()) {
                        currentCoroutineContext().ensureActive()
                        val documentId = it.getString(0) ?: continue
                        val name = it.getString(1) ?: documentId
                        val mime = it.getString(2).orEmpty()
                        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, documentId)
                        val path = if (entry.path.isEmpty()) name else "${entry.path}/$name"
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            queue.add(Pending(uri, if (entry.uri == root.uri) null else id, name,
                                if (entry.uri == root.uri) 0 else entry.depth + 1, path))
                        } else {
                            val kind = classify(name, mime) ?: continue
                            media[uri.toString()] = MediaFile(uri.toString(), id, name, path, mime, kind,
                                if (it.isNull(3)) 0 else it.getLong(3), if (it.isNull(4)) 0 else it.getLong(4))
                            count++
                            if (media.size % 100 == 0) progress(ScanProgress(folders.size, media.size, errors))
                        }
                    }
                }
            } catch (error: SecurityException) {
                if (entry.uri == root.uri) throw error
                errors++
                Log.w("LibraryScan", "Accès refusé : ${entry.path}", error)
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                if (entry.uri == root.uri) throw error
                errors++
                Log.w("LibraryScan", "Dossier ignoré : ${entry.path}", error)
            }
            folders.add(FolderEntity(id, entry.parent, entry.name, entry.depth, count, count,
                previous[id]?.selection ?: SelectionOverride.INHERIT))
            progress(ScanProgress(folders.size, media.size, errors))
        }
        val totals = folders.associate { it.id to it.directCount }.toMutableMap()
        for (folder in folders.sortedByDescending { it.depth }) {
            folder.parentId?.let { totals[it] = totals.getValue(it) + totals.getValue(folder.id) }
        }
        db.withTransaction {
            dao.clearMedia()
            dao.clearFolders()
            dao.insertFolders(folders.map { it.copy(totalCount = totals.getValue(it.id)) })
            media.values.toList().chunked(400).forEach { dao.insertMedia(it) }
            dao.saveRoot(LibraryRoot(treeUri = tree.toString(), displayName = root.name ?: "Bibliothèque",
                lastScanAt = System.currentTimeMillis()))
        }
    }

    suspend fun invalidate(id: String) = dao.invalidate(id)

    companion object {
        fun classify(name: String, mime: String): MediaKind? {
            val extension = name.substringAfterLast('.', "").lowercase()
            return when {
                mime == "image/gif" || extension == "gif" -> MediaKind.ANIMATED
                // The renderer detects animated WebP from the decoded drawable.
                mime == "image/webp" || extension == "webp" -> MediaKind.IMAGE
                mime in setOf("image/jpeg", "image/png") || extension in setOf("jpg", "jpeg", "png") -> MediaKind.IMAGE
                mime.startsWith("video/") || extension in setOf("mp4", "webm", "mov", "m4v") -> MediaKind.VIDEO
                else -> null
            }
        }
    }
}
