java.lang.IllegalStateException: Migration didn't properly handle: categories(com.corgimemo.app.data.model.Category).
 Expected:
TableInfo {
    name = 'categories',
    columns = {    
        Column {
           name = 'id',
           type = 'INTEGER',
           affinity = '3',
           notNull = 'true',
           primaryKeyPosition = '1',
           defaultValue = 'undefined'
        },
        Column {
           name = 'isDefault',
           type = 'INTEGER',
           affinity = '3',
           notNull = 'true',
           primaryKeyPosition = '0',
           defaultValue = 'undefined'
        },
        Column {
           name = 'name',
           type = 'TEXT',
           affinity = '2',
           notNull = 'true',
           primaryKeyPosition = '0',
           defaultValue = 'undefined'
        },
        Column {
           name = 'sortOrder',
           type = 'INTEGER',
           affinity = '3',
           notNull = 'true',
           primaryKeyPosition = '0',
           defaultValue = '0'
        },
        Column {
           name = 'type',
           type = 'INTEGER',
           affinity = '3',
           notNull = 'true',
           primaryKeyPosition = '0',
           defaultValue = 'undefined'
        }
    },
    foreignKeys = { }
    indices = { }
}
 Found:
TableInfo {
    name = 'categories',
    columns = {    
        Column {
           name = 'id',
           type = 'INTEGER',
           affinity = '3',
           notNull = 'true',
           primaryKeyPosition = '1',
           defaultValue = 'undefined'
        },
        Column {
           name = 'isDefault',
           type = 'INTEGER',
           affinity = '3',
           notNull = 'true',
           primaryKeyPosition = '0',
           defaultValue = 'undefined'
        },
        Column {
           name = 'name',
           type = 'TEXT',
           affinity = '2',
           notNull = 'true',
           primaryKeyPosition = '0',
           defaultValue = 'undefined'
        },
        Column {
           name = 'sortOrder',
           type = 'INTEGER',
           affinity = '3',
           notNull = 'true',
           primaryKeyPosition = '0',
           defaultValue = '0'
        },
        Column {
           name = 'type',
           type = 'INTEGER',
           affinity = '3',
           notNull = 'true',
           primaryKeyPosition = '0',
           defaultValue = 'undefined'
        }
    },
    foreignKeys = { }
    indices = {    
        Index {
           name = 'index_categories_sortOrder',
           unique = 'false',
           columns = {    sortOrder    },
           orders = {    ASC     }
        }
    },
}
	at androidx.room.BaseRoomConnectionManager.onMigrate(RoomConnectionManager.kt:215)
	at androidx.room.RoomConnectionManager$SupportOpenHelperCallback.onUpgrade(RoomConnectionManager.android.kt:165)
	at androidx.sqlite.db.framework.FrameworkSQLiteOpenHelper$OpenHelper.onUpgrade(FrameworkSQLiteOpenHelper.android.kt:245)
	at android.database.sqlite.SQLiteOpenHelper.getDatabaseLocked(SQLiteOpenHelper.java:437)
	at android.database.sqlite.SQLiteOpenHelper.getWritableDatabase(SQLiteOpenHelper.java:336)
	at androidx.sqlite.db.framework.FrameworkSQLiteOpenHelper$OpenHelper.getWritableOrReadableDatabase(FrameworkSQLiteOpenHelper.android.kt:224)
	at androidx.sqlite.db.framework.FrameworkSQLiteOpenHelper$OpenHelper.innerGetDatabase(FrameworkSQLiteOpenHelper.android.kt:180)
	at androidx.sqlite.db.framework.FrameworkSQLiteOpenHelper$OpenHelper.getSupportDatabase(FrameworkSQLiteOpenHelper.android.kt:141)
	at androidx.sqlite.db.framework.FrameworkSQLiteOpenHelper.getWritableDatabase(FrameworkSQLiteOpenHelper.android.kt:96)
	at androidx.sqlite.driver.SupportSQLiteDriver.open(SupportSQLiteDriver.android.kt:57)
	at androidx.sqlite.driver.SupportSQLiteDriver.open(SupportSQLiteDriver.android.kt:33)
	at androidx.room.coroutines.PassthroughConnectionPool.connection$lambda$0(PassthroughConnectionPool.kt:47)
	at androidx.room.coroutines.PassthroughConnectionPool$$ExternalSyntheticLambda0.invoke(D8$$SyntheticClass:0)
	at kotlin.SynchronizedLazyImpl.getValue(LazyJVM.kt:86)
	at androidx.room.coroutines.PassthroughConnectionPool.useConnection(PassthroughConnectionPool.kt:58)
	at androidx.room.RoomConnectionManager.useConnection(RoomConnectionManager.android.kt:138)
	at androidx.room.RoomDatabase.useConnection(RoomDatabase.android.kt:619)
	at androidx.room.TriggerBasedInvalidationTracker.syncTriggers$room_runtime(InvalidationTracker.kt:306)
	at androidx.room.TriggerBasedInvalidationTracker$createFlow$1$1.invokeSuspend(InvalidationTracker.kt:239)
	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:34)
	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:100)
	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1302)
	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:677)
	at java.lang.Thread.run(Thread.java:1119)
	Suppressed: kotlinx.coroutines.internal.DiagnosticCoroutineContextException: [StandaloneCoroutine{Cancelling}@619e712, Dispatchers.Main.immediate]