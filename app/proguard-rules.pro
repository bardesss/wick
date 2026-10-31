# Project-specific R8 rules. kotlinx.serialization, Room, WorkManager, Coil, OkHttp and jsoup
# ship consumer rules, so the @Serializable DTOs, the Room database and its DAOs are kept
# without anything here. Checked against the R8 seeds and mapping of a release build.

# WorkManager stores the worker class name with the queued work, and SyncWorkerFactory compares
# against it. Keep the name stable so work queued by one version is found by the next.
-keepnames class io.github.bardesss.wick.sync.SyncWorker

# Readable crash traces once the mapping file is applied.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
