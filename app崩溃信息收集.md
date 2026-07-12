java.lang.IllegalStateException: Migration didn't properly handle: special_dates(com.corgimemo.app.data.model.SpecialDate).
 Expected:
TableInfo {
    name = 'special_dates',
    columns = {    
        Column {
           name = 'category',
           type = 'TEXT',
           affinity = '2',
           notNull = 'true',
           primaryKeyPosition = '0',
           defaultValue = ''OTHER''
        },
        Column {
           name = 'content',
           type = 'TEXT',
           affinity = '2',
           notNull = 'true',
           primaryKeyPosition = '0',
           defaultValue = ''''
        },
        Column {
           name = 'countMode',
           type = 'INTEGER',
           affinity = '3',
           notNull = 'true',
           primaryKeyPosition = '0',
           defaultValue = '0'
        },
        Column {
           name = 'createdAt',
           type = 'INTEGER',
           affinity = '3',
           notNull = 'true',
           primaryKeyPosition = '0',
           defaultValue = 'undefined'
        },
        Column {
           name = 'id',
           type = 'INTEGER',
           affinity = '3',
           notNull = 'true',
           primaryKeyPosition = '1',
           defaultValue = 'undefined'
        },
        Column {
           name = 'imagePaths',
           type = 'TEXT',
           affinity = '2',
           notNull = 'true',
           primaryKeyPosition = '0',
           defaultValue = ''''
        },
        Column {
           name = 'imageUrls',
           type = 'TEXT',
           affinity = '2',
           notNull = 'true',
           primaryKeyPosition = '0',
           defaultValue = ''''
        },
        Column {
           name = 'isArchived',
           type = 'INTEGER',
           affinity = '3',
           notNull = 'true',
           primaryKeyPosition = '0',
           defaultValue = '0'
        },
        Column {
           name = 'isPinned',
           type = 'INTEGER',
           affinity = '3',
           notNull = 'true',
           primaryKeyPosition = '0',
           defaultValue = '0'
        },
        Column {
           name = 'reminderDays',
           type = 'INTEGER',
           affinity = '3',
           notNull = 'true',
           primaryKeyPosition = '0',
           defaultValue = '0'
        },
        Column {
           name = 'repeatType',
           type = 'INTEGER',
           affinity = '3',
           notNull = 'true',
           primaryKeyPosition = '0',
           defaultValue = '0'
        },
        Column {
           name = 'tags',
           type = 'TEXT',
           affinity = '2',
           notNull = 'true',
           primaryKeyPosition = '0',
           defaultValue = ''''
        },
        Column {
           name = 'targetDate',
           type = 'INTEGER',
           affinity = '3',
           notNull = 'true',
           primaryKeyPosition = '0',
           defaultValue = 'undefined'
        },
        Column {
           name = 'title',
           type = 'TEXT',
           affinity = '2',
           notNull = 'true',
           primaryKeyPosition = '0',
           defaultValue = 'undefined'
        },
        Column {
           name = 'updatedAt',
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
           name = 'index_special_dates_category',
           unique = 'false',
           columns = {    category    },
           orders = {    ASC     }
        },
        Index {
           name = 'index_special_dates_isPinned',
           unique = 'false',
           columns = {    isPinned    },
           orders = {    ASC     }
        },
        Index {
           name = 'index_special_dates_targetDate',
           unique = 'false',
           columns = {    targetDate    },
           orders = {    ASC     }
        }
    },
}
 Found:
TableInfo {
    name = 'special_dates',
    columns = {    
        Column {
           name = 'category',
           type = 'TEXT',
           affinity = '2',
           notNull = 'true',
           primaryKeyPosition = '0',
           defaultValue = ''OTHER''
        },
        Column {
           name = 'content',
           type = 'TEXT',
           affinity = '2',
           notNull = 'true',
           primaryKeyPosition = '0',
           defaultValue = ''''
        },
        Column {
           name = 'countMode',
           type = 'INTEGER',
           affinity = '3',
           notNull = 'true',
           primaryKeyPosition = '0',
           defaultValue = '0'
        },
        Column {
           name = 'createdAt',
           type = 'INTEGER',
           affinity = '3',
           notNull = 'true',
           primaryKeyPosition = '0',
           defaultValue = 'undefined'
        },
        Column {
           name = 'id',
           type = 'INTEGER',
           affinity = '3',
           notNull = 'true',
           primaryKeyPosition = '1',
           defaultValue = 'undefined'
        },
        Column {
           name = 'imagePaths',
           type = 'TEXT',
           affinity = '2',
           notNull = 'true',
           primaryKeyPosition = '0',
           defaultValue = ''''
        },
        Column {
           name = 'imageUrls',
           type = 'TEXT',
           affinity = '2',
           notNull = 'true',
           primaryKeyPosition = '0',
           defaultValue = ''''
        },
        Column {
           name = 'isArchived',
           type = 'INTEGER',
           affinity = '3',
           notNull = 'true',
           primaryKeyPosition = '0',
           defaultValue = '0'
        },
        Column {
           name = 'isPinned',
           type = 'INTEGER',
           affinity = '3',
           notNull = 'true',
           primaryKeyPosition = '0',
           defaultValue = '0'
        },
        Column {
           name = 'reminderDays',
           type = 'INTEGER',
           affinity = '3',
           notNull = 'true',
           primaryKeyPosition = '0',
           defaultValue = '0'
        },
        Column {
           name = 'repeatType',
           type = 'INTEGER',
           affinity = '3',
           notNull = 'true',
           primaryKeyPosition = '0',
           defaultValue = '0'
        },
        Column {
           name = 'tags',
           type = 'TEXT',
           affinity = '2',
           notNull = 'true',
           primaryKeyPosition = '0',
           defaultValue = ''''
        },
        Column {
           name = 'targetDate',
           type = 'INTEGER',
           affinity = '3',
           notNull = 'true',
           primaryKeyPosition = '0',
           defaultValue = 'undefined'
        },
        Column {
           name = 'title',
           type = 'TEXT',
           affinity = '2',
           notNull = 'true',
           primaryKeyPosition = '0',
           defaultValue = 'undefined'
        },
        Column {
           name = 'updatedAt',
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
           name = 'index_special_dates_category',
           unique = 'false',
           columns = {    category    },
           orders = {    ASC     }
        },
        Index {
           name = 'index_special_dates_isArchived',
           unique = 'false',
           columns = {    isArchived    },
           orders = {    ASC     }
        },
        Index {
           name = 'index_special_dates_isPinned',
           unique = 'false',
           columns = {    isPinned    },
           orders = {    ASC     }
        },
        Index {
           name = 'index_special_dates_targetDate',
           unique = 'false',
           columns = {    targetDate    },
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
	Suppressed: kotlinx.coroutines.internal.DiagnosticCoroutineContextException: [StandaloneCoroutine{Cancelling}@d90bdc2, Dispatchers.Main.immediate]