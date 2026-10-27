package com.familydocs.app.data

import android.content.Context
import com.familydocs.app.security.CryptoManager
import java.io.File
import java.util.UUID

/** Encrypted blob storage for document files and photos (app-private, excluded from Android backups). */
class FileStore(context: Context, private val crypto: CryptoManager) {
    private val dir = File(context.noBackupFilesDir, "vault").apply { mkdirs() }

    fun put(bytes: ByteArray, id: String = UUID.randomUUID().toString()): String {
        File(dir, id).writeBytes(crypto.encrypt(bytes))
        return id
    }

    fun get(id: String): ByteArray? = File(dir, id).takeIf { it.exists() }?.let { crypto.decrypt(it.readBytes()) }

    fun delete(id: String?) { if (id != null) File(dir, id).delete() }

    fun clear() { dir.listFiles()?.forEach { it.delete() } }
}
