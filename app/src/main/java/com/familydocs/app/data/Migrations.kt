package com.familydocs.app.data

import androidx.room.migration.Migration

/**
 * How updates keep your data
 * --------------------------
 * Every time the database layout changes:
 *   1. bump `version` in [AppDatabase],
 *   2. add a Migration(old, new) below that ALTERs/creates tables (never drops user data),
 *   3. commit the new JSON in app/schemas/ (Room writes it on build) so the change can be checked.
 * The database is never opened with fallbackToDestructiveMigration, so a missing migration
 * makes the app refuse to start instead of silently wiping the vault.
 *
 * Example for a future version 2:
 *   val MIGRATION_1_2 = object : Migration(1, 2) {
 *       override fun migrate(db: SupportSQLiteDatabase) {
 *           db.execSQL("ALTER TABLE documents ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
 *       }
 *   }
 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    // MIGRATION_1_2,
)
