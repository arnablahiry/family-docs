package com.familydocs.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
/** v2: members get an illustrated default character. Existing rows keep everything and get no character (initials until one is picked). */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE members ADD COLUMN avatar TEXT")
    }
}

/** v3: events (trips, birthdays, appointments) and event-only documents. Nothing existing changes. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `events` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `art` TEXT NOT NULL, `photoFile` TEXT, " +
                "`startDay` TEXT NOT NULL, `endDay` TEXT, `people` TEXT NOT NULL, `visibleTo` TEXT NOT NULL, `pins` TEXT NOT NULL, " +
                "`remindDays` INTEGER NOT NULL, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("ALTER TABLE documents ADD COLUMN eventId TEXT")
    }
}

/** v4: per-document "show flag on card" choice (NULL = the type's default). Nothing existing changes. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE documents ADD COLUMN showFlag INTEGER")
    }
}

val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
)
