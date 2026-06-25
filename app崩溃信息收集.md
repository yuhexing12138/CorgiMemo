2026-06-25 15:40:50.924 18665-18665 AndroidRuntime          com.xiaomi.aiasst.vision             I  VM exiting with result code 18665, cleanup skipped.
2026-06-25 15:40:54.974  3964-3964  AndroidRuntime          com.qti.qcc                          I  VM exiting with result code 0, cleanup skipped.
2026-06-25 15:41:45.093 18130-18130 AndroidRuntime          com.corgimemo.app                    E  FATAL EXCEPTION: main
                                                                                                    Process: com.corgimemo.app, PID: 18130
                                                                                                    java.lang.IllegalStateException: Migration didn't properly handle: todo_items(com.corgimemo.app.data.model.TodoItem).
                                                                                                     Expected:
                                                                                                    TableInfo {
                                                                                                        name = 'todo_items',
                                                                                                        columns = {    
                                                                                                            Column {
                                                                                                               name = 'backgroundColor',
                                                                                                               type = 'INTEGER',
                                                                                                               affinity = '3',
                                                                                                               notNull = 'true',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = '16777215'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'categoryId',
                                                                                                               type = 'INTEGER',
                                                                                                               affinity = '3',
                                                                                                               notNull = 'true',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'completedAt',
                                                                                                               type = 'INTEGER',
                                                                                                               affinity = '3',
                                                                                                               notNull = 'false',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'content',
                                                                                                               type = 'TEXT',
                                                                                                               affinity = '2',
                                                                                                               notNull = 'false',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'contentFormat',
                                                                                                               type = 'TEXT',
                                                                                                               affinity = '2',
                                                                                                               notNull = 'true',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = ''''
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
                                                                                                               name = 'dueDate',
                                                                                                               type = 'INTEGER',
                                                                                                               affinity = '3',
                                                                                                               notNull = 'false',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'estimatedDurationMinutes',
                                                                                                               type = 'INTEGER',
                                                                                                               affinity = '3',
                                                                                                               notNull = 'false',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'geofenceAddress',
                                                                                                               type = 'TEXT',
                                                                                                               affinity = '2',
                                                                                                               notNull = 'false',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'geofenceEnabled',
                                                                                                               type = 'INTEGER',
                                                                                                               affinity = '3',
                                                                                                               notNull = 'true',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'geofenceLat',
                                                                                                               type = 'REAL',
                                                                                                               affinity = '4',
                                                                                                               notNull = 'false',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'geofenceLng',
                                                                                                               type = 'REAL',
                                                                                                               affinity = '4',
                                                                                                               notNull = 'false',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'geofenceRadius',
                                                                                                               type = 'REAL',
                                                                                                               affinity = '4',
                                                                                                               notNull = 'false',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'geofenceType',
                                                                                                               type = 'INTEGER',
                                                                                                               affinity = '3',
                                                                                                               notNull = 'true',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'hasSubTasks',
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
2026-06-25 15:41:45.095 18130-18130 AndroidRuntime          com.corgimemo.app                    E             name = 'isPinned',
                                                                                                               type = 'INTEGER',
                                                                                                               affinity = '3',
                                                                                                               notNull = 'true',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = '0'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'position',
                                                                                                               type = 'INTEGER',
                                                                                                               affinity = '3',
                                                                                                               notNull = 'true',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'priority',
                                                                                                               type = 'INTEGER',
                                                                                                               affinity = '3',
                                                                                                               notNull = 'true',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'reminderTime',
                                                                                                               type = 'INTEGER',
                                                                                                               affinity = '3',
                                                                                                               notNull = 'false',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'repeatType',
                                                                                                               type = 'INTEGER',
                                                                                                               affinity = '3',
                                                                                                               notNull = 'true',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'startDate',
                                                                                                               type = 'INTEGER',
                                                                                                               affinity = '3',
                                                                                                               notNull = 'false',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'status',
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
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'voiceDuration',
                                                                                                               type = 'INTEGER',
                                                                                                               affinity = '3',
                                                                                                               notNull = 'false',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'voiceNotePath',
                                                                                                               type = 'TEXT',
                                                                                                               affinity = '2',
                                                                                                               notNull = 'false',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            }
                                                                                                        },
                                                                                                        foreignKeys = { }
                                                                                                        indices = {    
                                                                                                            Index {
                                                                                                               name = 'index_todo_items_categoryId_status',
                                                                                                               unique = 'false',
                                                                                                               columns = {    categoryId,status    },
                                                                                                               orders = {    ASC,ASC     }
                                                                                                            },
                                                                                                            Index {
                                                                                                               name = 'index_todo_items_dueDate_status',
                                                                                                               unique = 'false',
                                                                                                               columns = {    dueDate,status    },
                                                                                                               orders = {    ASC,ASC     }
                                                                                                            },
                                                                                                            Index {
                                                                                                               name = 'index_todo_items_hasSubTasks',
                                                                                                               unique = 'false',
                                                                                                               columns = {    hasSubTasks    },
                                                                                                               orders = {    ASC     }
                                                                                                            },
                                                                                                            Index {
                                                                                                               name = 'index_todo_items_priority_startDate',
                                                                                                               unique = 'false',
                                                                                                               columns = {    priority,startDate    },
                                                                                                               orders = {    ASC,ASC     }
                                                                                                            },
                                                                                                            Index {
                                                                                                               name = 'index_todo_items_status_createdAt',
                                                                                                               unique = 'false',
                                                                                                               columns = {    status,createdAt    },
                                                                                                               orders = {    ASC,ASC     }
                                                                                                            }
                                                                                                        },
                                                                                                    }
                                                                                                     Found:
                                                                                                    TableInfo {
                                                                                                        name = 'todo_items',
                                                                                                        columns = {    
                                                                                                            Column {
                                                                                                               name = 'backgroundColor',
                                                                                                               type = 'INTEGER',
                                                                                                               affinity = '3',
                                                                                                               notNull = 'true',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = '16777215'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'categoryId',
                                                                                                               type = 'INTEGER',
                                                                                                               affinity = '3',
                                                                                                               notNull = 'true',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'completedAt',
                                                                                                               type = 'INTEGER',
2026-06-25 15:41:45.097 18130-18130 AndroidRuntime          com.corgimemo.app                    E             affinity = '3',
                                                                                                               notNull = 'false',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'content',
                                                                                                               type = 'TEXT',
                                                                                                               affinity = '2',
                                                                                                               notNull = 'false',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'contentFormat',
                                                                                                               type = 'TEXT',
                                                                                                               affinity = '2',
                                                                                                               notNull = 'true',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = ''''
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
                                                                                                               name = 'dueDate',
                                                                                                               type = 'INTEGER',
                                                                                                               affinity = '3',
                                                                                                               notNull = 'false',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'estimatedDurationMinutes',
                                                                                                               type = 'INTEGER',
                                                                                                               affinity = '3',
                                                                                                               notNull = 'false',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'geofenceAddress',
                                                                                                               type = 'TEXT',
                                                                                                               affinity = '2',
                                                                                                               notNull = 'false',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'geofenceEnabled',
                                                                                                               type = 'INTEGER',
                                                                                                               affinity = '3',
                                                                                                               notNull = 'true',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'geofenceLat',
                                                                                                               type = 'REAL',
                                                                                                               affinity = '4',
                                                                                                               notNull = 'false',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'geofenceLng',
                                                                                                               type = 'REAL',
                                                                                                               affinity = '4',
                                                                                                               notNull = 'false',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'geofenceRadius',
                                                                                                               type = 'REAL',
                                                                                                               affinity = '4',
                                                                                                               notNull = 'false',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'geofenceType',
                                                                                                               type = 'INTEGER',
                                                                                                               affinity = '3',
                                                                                                               notNull = 'true',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'hasSubTasks',
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
                                                                                                               name = 'isPinned',
                                                                                                               type = 'INTEGER',
                                                                                                               affinity = '3',
                                                                                                               notNull = 'true',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = '0'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'position',
                                                                                                               type = 'INTEGER',
                                                                                                               affinity = '3',
                                                                                                               notNull = 'true',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'priority',
                                                                                                               type = 'INTEGER',
                                                                                                               affinity = '3',
                                                                                                               notNull = 'true',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'reminderTime',
                                                                                                               type = 'INTEGER',
                                                                                                               affinity = '3',
                                                                                                               notNull = 'false',
2026-06-25 15:41:45.097 18130-18130 AndroidRuntime          com.corgimemo.app                    E             primaryKeyPosition = '0', (Fix with AI)
                                                                                                               defaultValue = 'undefined'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'repeatType',
                                                                                                               type = 'INTEGER',
                                                                                                               affinity = '3',
                                                                                                               notNull = 'true',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'startDate',
                                                                                                               type = 'INTEGER',
                                                                                                               affinity = '3',
                                                                                                               notNull = 'false',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'status',
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
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'voiceDuration',
                                                                                                               type = 'INTEGER',
                                                                                                               affinity = '3',
                                                                                                               notNull = 'false',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            },
                                                                                                            Column {
                                                                                                               name = 'voiceNotePath',
                                                                                                               type = 'TEXT',
                                                                                                               affinity = '2',
                                                                                                               notNull = 'false',
                                                                                                               primaryKeyPosition = '0',
                                                                                                               defaultValue = 'undefined'
                                                                                                            }
                                                                                                        },
                                                                                                        foreignKeys = { }
                                                                                                        indices = {    
                                                                                                            Index {
                                                                                                               name = 'index_todo_items_categoryId_status',
                                                                                                               unique = 'false',
                                                                                                               columns = {    categoryId,status    },
                                                                                                               orders = {    ASC,ASC     }
                                                                                                            },
                                                                                                            Index {
                                                                                                               name = 'index_todo_items_dueDate_status',
                                                                                                               unique = 'false',
                                                                                                               columns = {    dueDate,status    },
                                                                                                               orders = {    ASC,ASC     }
                                                                                                            },
                                                                                                            Index {
                                                                                                               name = 'index_todo_items_hasSubTasks',
                                                                                                               unique = 'false',
                                                                                                               columns = {    hasSubTasks    },
                                                                                                               orders = {    ASC     }
                                                                                                            },
                                                                                                            Index {
                                                                                                               name = 'index_todo_items_isPinned',
                                                                                                               unique = 'false',
                                                                                                               columns = {    isPinned    },
                                                                                                               orders = {    ASC     }
                                                                                                            },
                                                                                                            Index {
                                                                                                               name = 'index_todo_items_priority_startDate',
                                                                                                               unique = 'false',
                                                                                                               columns = {    priority,startDate    },
                                                                                                               orders = {    ASC,ASC     }
                                                                                                            },
                                                                                                            Index {
                                                                                                               name = 'index_todo_items_status_createdAt',
                                                                                                               unique = 'false',
                                                                                                               columns = {    status,createdAt    },
                                                                                                               orders = {    ASC,ASC     }
                                                                                                            }
                                                                                                        },
                                                                                                    }
                                                                                                    	at androidx.room.BaseRoomConnectionManager.onMigrate(RoomConnectionManager.kt:215)
                                                                                                    	at androidx.room.RoomConnectionManager$SupportOpenHelperCallback.onUpgrade(RoomConnectionManager.android.kt:165)
                                                                                                    	at androidx.sqlite.db.framework.FrameworkSQLiteOpenHelper$OpenHelper.onUpgrade(FrameworkSQLiteOpenHelper.android.kt:245)
                                                                                                    	at android.database.sqlite.SQLiteOpenHelper.getDatabaseLocked(SQLiteOpenHelper.java:415)
                                                                                                    	at android.database.sqlite.SQLiteOpenHelper.getWritableDatabase(SQLiteOpenHelper.java:316)
                                                                                                    	at androidx.sqlite.db.framework.FrameworkSQLiteOpenHelper$OpenHelper.getWritableOrReadableDatabase(FrameworkSQLiteOpenHelper.android.kt:224)
                                                                                                    	at androidx.sqlite.db.framework.FrameworkSQLiteOpenHelper$OpenHelper.innerGetDatabase(FrameworkSQLiteOpenHelper.android.kt:180)
                                                                                                    	at androidx.sqlite.db.framework.FrameworkSQLiteOpenHelper$OpenHelper.getSupportDatabase(FrameworkSQLiteOpenHelper.android.kt:141)
                                                                                                    	at androidx.sqlite.db.framework.FrameworkSQLiteOpenHelper.getWritableDatabase(FrameworkSQLiteOpenHelper.android.kt:96)
                                                                                                    	at androidx.sqlite.driver.SupportSQLiteDriver.open(SupportSQLiteDriver.android.kt:57)
2026-06-25 15:41:45.099 18130-18130 AndroidRuntime          com.corgimemo.app                    E  	at androidx.sqlite.driver.SupportSQLiteDriver.open(SupportSQLiteDriver.android.kt:33) (Fix with AI)
                                                                                                    	at androidx.room.coroutines.PassthroughConnectionPool.connection$lambda$0(PassthroughConnectionPool.kt:47)
                                                                                                    	at androidx.room.coroutines.PassthroughConnectionPool$$ExternalSyntheticLambda0.invoke(D8$$SyntheticClass:0)
                                                                                                    	at kotlin.SynchronizedLazyImpl.getValue(LazyJVM.kt:86)
                                                                                                    	at androidx.room.coroutines.PassthroughConnectionPool.useConnection(PassthroughConnectionPool.kt:58)
                                                                                                    	at androidx.room.RoomConnectionManager.useConnection(RoomConnectionManager.android.kt:138)
                                                                                                    	at androidx.room.RoomDatabase.useConnection(RoomDatabase.android.kt:619)
                                                                                                    	at androidx.room.TriggerBasedInvalidationTracker.syncTriggers$room_runtime(InvalidationTracker.kt:306)
                                                                                                    	at androidx.room.TriggerBasedInvalidationTracker$createFlow$1$1.invokeSuspend(InvalidationTracker.kt:239)
                                                                                                    	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
                                                                                                    	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:101)
                                                                                                    	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1291)
                                                                                                    	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:670)
                                                                                                    	at java.lang.Thread.run(Thread.java:1012)
                                                                                                    	Suppressed: kotlinx.coroutines.internal.DiagnosticCoroutineContextException: [StandaloneCoroutine{Cancelling}@ad4a268, Dispatchers.Main.immediate]
2026-06-25 15:42:46.608 20910-20910 AndroidRuntime          com.xiaomi.aiasst.vision             I  VM exiting with result code 20910, cleanup skipped.