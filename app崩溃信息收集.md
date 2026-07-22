(base) PS C:\Users\EDY\Desktop\CorgiMemo> adb logcat -s UndoRedoTrace:W
--------- beginning of main
07-22 17:49:38.287  5754  5754 W UndoRedoTrace: [UI] canUndo / canRedo 变化: canUndo=false, canRedo=false, isRestoring=false
07-22 17:49:38.288  5754  5754 W UndoRedoTrace: [initializeTodoLines] 入口: size=1, texts=[], hasInitializedLines_before=false
07-22 17:49:38.288  5754  5754 W UndoRedoTrace: [resetTodoLinesInitialized] _hasInitializedLines = false
07-22 17:49:38.289  5754  5754 W UndoRedoTrace: [initializeTodoLines] 完成: _todoLines.size=1, texts=[], _title=''
07-22 17:49:38.289  5754  5754 W UndoRedoTrace: [markTodoLinesInitialized] _hasInitializedLines = true (从此刻起，setTodoLines 才会推快照)
07-22 17:49:40.326  5754  5754 W UndoRedoTrace: [UI onLinesChange] 触发: newLines.size=1, newLines.texts=[1]
07-22 17:49:40.327  5754  5754 W UndoRedoTrace: [setTodoLines] 入口: isRestoring=false, isDebouncing=false, hasInitializedLines=true, oldLines.size=1, newLines.size=1, newLines.texts=[1]
07-22 17:49:40.327  5754  5754 W UndoRedoTrace: [setTodoLines] → 触发 pushSnapshot（操作前）: currentStack: undo=false, redo=false, beforeSnapshot.title=''
07-22 17:49:40.327  5754  5754 W UndoRedoTrace: [pushSnapshot] 入口: isRestoring=false, stackBefore: undo=false, redo=false
07-22 17:49:40.327  5754  5754 W UndoRedoTrace: [pushSnapshot] ✅ 已推入: before undo=false, after undo=true, redo=false, snapshotTitle='', subTasks=0
07-22 17:49:40.349  5754  5754 W UndoRedoTrace: [UI] canUndo / canRedo 变化: canUndo=true, canRedo=false, isRestoring=false
07-22 17:49:41.576  5754  5754 W UndoRedoTrace: [UI onLinesChange] 触发: newLines.size=2, newLines.texts=[1, ]
07-22 17:49:41.576  5754  5754 W UndoRedoTrace: [setTodoLines] 入口: isRestoring=false, isDebouncing=false, hasInitializedLines=true, oldLines.size=1, newLines.size=2, newLines.texts=[1, ]
07-22 17:49:41.576  5754  5754 W UndoRedoTrace: [setTodoLines] → 触发 pushSnapshot（操作前）: currentStack: undo=true, redo=false, beforeSnapshot.title='1'
07-22 17:49:41.576  5754  5754 W UndoRedoTrace: [pushSnapshot] 入口: isRestoring=false, stackBefore: undo=true, redo=false
07-22 17:49:41.576  5754  5754 W UndoRedoTrace: [pushSnapshot] ✅ 已推入: before undo=true, after undo=true, redo=false, snapshotTitle='1', subTasks=0
07-22 17:49:43.499  5754  5754 W UndoRedoTrace: [UI onLinesChange] 触发: newLines.size=2, newLines.texts=[1, 2]
07-22 17:49:43.500  5754  5754 W UndoRedoTrace: [setTodoLines] 入口: isRestoring=false, isDebouncing=false, hasInitializedLines=true, oldLines.size=2, newLines.size=2, newLines.texts=[1, 2]
07-22 17:49:43.500  5754  5754 W UndoRedoTrace: [setTodoLines] → 触发 pushSnapshot（操作前）: currentStack: undo=true, redo=false, beforeSnapshot.title='1'
07-22 17:49:43.500  5754  5754 W UndoRedoTrace: [pushSnapshot] 入口: isRestoring=false, stackBefore: undo=true, redo=false
07-22 17:49:43.500  5754  5754 W UndoRedoTrace: [pushSnapshot] ✅ 已推入: before undo=true, after undo=true, redo=false, snapshotTitle='1', subTasks=0
07-22 17:49:45.543  5754  5754 W UndoRedoTrace: [UI] 撤销按钮点击: canUndo=true, canRedo=false, current todoLines.texts=[1, 2]
07-22 17:49:45.543  5754  5754 W UndoRedoTrace: [undo] 入口: isRestoring=false, canUndo=true, canRedo=false
07-22 17:49:45.543  5754  5754 W UndoRedoTrace: [undo] 准备恢复: current.title='1' → previous.title='1', current.subTasks=1 → previous.subTasks=0
07-22 17:49:45.543  5754  5754 W UndoRedoTrace: [undo] _isRestoring = true
07-22 17:49:45.543  5754  5754 W UndoRedoTrace: [restoreFromSnapshot] 入口: snapshot.title='1', subTasks=[], todoLinesBefore.size=2, todoLinesBefore.texts=[1, 2]
07-22 17:49:45.544  5754  5754 W UndoRedoTrace: [rebuildTodoLinesFromSnapshot] 完成: size=1, texts=[1], title='1', snapshot.subTasks.size=0, snapshot.subTasks.titles=[], mainGroupId=0, ⚠️ 注意：snapshot.subTasks 不包含空白子任务行，若用户撤销时正在编辑空白子待办行，重建后会丢失占位行
07-22 17:49:45.544  5754  5754 W UndoRedoTrace: [restoreFromSnapshot] _todoLines 已更新: size=1, texts=[1]
07-22 17:49:45.544  5754  5754 W UndoRedoTrace: [undo] _isRestoring = false
07-22 17:49:45.544  5754  5754 W UndoRedoTrace: [undo] ✅ 完成: canUndo=true, canRedo=true, todoLinesAfter.texts=[1]
07-22 17:49:45.570  5754  5754 W UndoRedoTrace: [UI] canUndo / canRedo 变化: canUndo=true, canRedo=true, isRestoring=false
07-22 17:49:45.570  5754  5754 W UndoRedoTrace: [UI LaunchedEffect(restoreEvent)] 触发: snapshot.title='1', subTasks=[], todoLinesAtTrigger.size=1, todoLinesAtTrigger.texts=[1]
07-22 17:49:47.884  5754  5754 W UndoRedoTrace: [UI] 撤销按钮点击: canUndo=true, canRedo=true, current todoLines.texts=[1]
07-22 17:49:47.884  5754  5754 W UndoRedoTrace: [undo] 入口: isRestoring=false, canUndo=true, canRedo=true
07-22 17:49:47.884  5754  5754 W UndoRedoTrace: [undo] 准备恢复: current.title='1' → previous.title='1', current.subTasks=0 → previous.subTasks=0
07-22 17:49:47.884  5754  5754 W UndoRedoTrace: [undo] _isRestoring = true
07-22 17:49:47.884  5754  5754 W UndoRedoTrace: [restoreFromSnapshot] 入口: snapshot.title='1', subTasks=[], todoLinesBefore.size=1, todoLinesBefore.texts=[1]
07-22 17:49:47.885  5754  5754 W UndoRedoTrace: [rebuildTodoLinesFromSnapshot] 完成: size=1, texts=[1], title='1', snapshot.subTasks.size=0, snapshot.subTasks.titles=[], mainGroupId=0, ⚠️ 注意：snapshot.subTasks 不包含空白子任务行，若用户撤销时正在编辑空白子待办行，重建后会丢失占位行
07-22 17:49:47.885  5754  5754 W UndoRedoTrace: [restoreFromSnapshot] _todoLines 已更新: size=1, texts=[1]
07-22 17:49:47.885  5754  5754 W UndoRedoTrace: [undo] _isRestoring = false
07-22 17:49:47.885  5754  5754 W UndoRedoTrace: [undo] ✅ 完成: canUndo=true, canRedo=true, todoLinesAfter.texts=[1]
07-22 17:49:47.897  5754  5754 W UndoRedoTrace: [UI LaunchedEffect(restoreEvent)] 触发: snapshot.title='1', subTasks=[], todoLinesAtTrigger.size=1, todoLinesAtTrigger.texts=[1]
07-22 17:49:50.096  5754  5754 W UndoRedoTrace: [UI] 撤销按钮点击: canUndo=true, canRedo=true, current todoLines.texts=[1]
07-22 17:49:50.096  5754  5754 W UndoRedoTrace: [undo] 入口: isRestoring=false, canUndo=true, canRedo=true
07-22 17:49:50.096  5754  5754 W UndoRedoTrace: [undo] 准备恢复: current.title='1' → previous.title='', current.subTasks=0 → previous.subTasks=0
07-22 17:49:50.096  5754  5754 W UndoRedoTrace: [undo] _isRestoring = true
07-22 17:49:50.096  5754  5754 W UndoRedoTrace: [restoreFromSnapshot] 入口: snapshot.title='', subTasks=[], todoLinesBefore.size=1, todoLinesBefore.texts=[1]
07-22 17:49:50.097  5754  5754 W UndoRedoTrace: [rebuildTodoLinesFromSnapshot] 完成: size=1, texts=[], title='', snapshot.subTasks.size=0, snapshot.subTasks.titles=[], mainGroupId=0, ⚠️ 注意：snapshot.subTasks 不包含空白子任务行，若用户撤销时正在编辑空白子待办行，重建后会丢失占位行
07-22 17:49:50.097  5754  5754 W UndoRedoTrace: [restoreFromSnapshot] _todoLines 已更新: size=1, texts=[]
07-22 17:49:50.097  5754  5754 W UndoRedoTrace: [undo] _isRestoring = false
07-22 17:49:50.097  5754  5754 W UndoRedoTrace: [undo] ✅ 完成: canUndo=false, canRedo=true, todoLinesAfter.texts=[]
07-22 17:49:50.121  5754  5754 W UndoRedoTrace: [UI] canUndo / canRedo 变化: canUndo=false, canRedo=true, isRestoring=false
07-22 17:49:50.121  5754  5754 W UndoRedoTrace: [UI LaunchedEffect(restoreEvent)] 触发: snapshot.title='', subTasks=[], todoLinesAtTrigger.size=1, todoLinesAtTrigger.texts=[]
07-22 17:49:51.782  5754  5754 W UndoRedoTrace: [UI] 恢复按钮点击: canUndo=false, canRedo=true, current todoLines.texts=[]
07-22 17:49:51.782  5754  5754 W UndoRedoTrace: [redo] 入口: isRestoring=false, canUndo=false, canRedo=true
07-22 17:49:51.782  5754  5754 W UndoRedoTrace: [redo] 准备恢复: current.title='' → next.title='1', current.subTasks=0 → next.subTasks=0
07-22 17:49:51.782  5754  5754 W UndoRedoTrace: [redo] _isRestoring = true
07-22 17:49:51.782  5754  5754 W UndoRedoTrace: [restoreFromSnapshot] 入口: snapshot.title='1', subTasks=[], todoLinesBefore.size=1, todoLinesBefore.texts=[]
07-22 17:49:51.783  5754  5754 W UndoRedoTrace: [rebuildTodoLinesFromSnapshot] 完成: size=1, texts=[1], title='1', snapshot.subTasks.size=0, snapshot.subTasks.titles=[], mainGroupId=0, ⚠️ 注意：snapshot.subTasks 不包含空白子任务行，若用户撤销时正在编辑空白子待办行，重建后会丢失占位行
07-22 17:49:51.783  5754  5754 W UndoRedoTrace: [restoreFromSnapshot] _todoLines 已更新: size=1, texts=[1]
07-22 17:49:51.783  5754  5754 W UndoRedoTrace: [redo] _isRestoring = false
07-22 17:49:51.783  5754  5754 W UndoRedoTrace: [redo] ✅ 完成: canUndo=true, canRedo=true, todoLinesAfter.texts=[1]
07-22 17:49:51.807  5754  5754 W UndoRedoTrace: [UI] canUndo / canRedo 变化: canUndo=true, canRedo=true, isRestoring=false
07-22 17:49:51.807  5754  5754 W UndoRedoTrace: [UI LaunchedEffect(restoreEvent)] 触发: snapshot.title='1', subTasks=[], todoLinesAtTrigger.size=1, todoLinesAtTrigger.texts=[1]
07-22 17:49:52.981  5754  5754 W UndoRedoTrace: [UI] 恢复按钮点击: canUndo=true, canRedo=true, current todoLines.texts=[1]
07-22 17:49:52.982  5754  5754 W UndoRedoTrace: [redo] 入口: isRestoring=false, canUndo=true, canRedo=true
07-22 17:49:52.982  5754  5754 W UndoRedoTrace: [redo] 准备恢复: current.title='1' → next.title='1', current.subTasks=0 → next.subTasks=0
07-22 17:49:52.982  5754  5754 W UndoRedoTrace: [redo] _isRestoring = true
07-22 17:49:52.982  5754  5754 W UndoRedoTrace: [restoreFromSnapshot] 入口: snapshot.title='1', subTasks=[], todoLinesBefore.size=1, todoLinesBefore.texts=[1]
07-22 17:49:52.982  5754  5754 W UndoRedoTrace: [rebuildTodoLinesFromSnapshot] 完成: size=1, texts=[1], title='1', snapshot.subTasks.size=0, snapshot.subTasks.titles=[], mainGroupId=0, ⚠️ 注意：snapshot.subTasks 不包含空白子任务行，若用户撤销时正在编辑空白子待办行，重建后会丢失占位行
07-22 17:49:52.982  5754  5754 W UndoRedoTrace: [restoreFromSnapshot] _todoLines 已更新: size=1, texts=[1]
07-22 17:49:52.982  5754  5754 W UndoRedoTrace: [redo] _isRestoring = false
07-22 17:49:52.983  5754  5754 W UndoRedoTrace: [redo] ✅ 完成: canUndo=true, canRedo=true, todoLinesAfter.texts=[1]
07-22 17:49:52.994  5754  5754 W UndoRedoTrace: [UI LaunchedEffect(restoreEvent)] 触发: snapshot.title='1', subTasks=[], todoLinesAtTrigger.size=1, todoLinesAtTrigger.texts=[1]
07-22 17:49:54.265  5754  5754 W UndoRedoTrace: [UI] 恢复按钮点击: canUndo=true, canRedo=true, current todoLines.texts=[1]
07-22 17:49:54.265  5754  5754 W UndoRedoTrace: [redo] 入口: isRestoring=false, canUndo=true, canRedo=true
07-22 17:49:54.265  5754  5754 W UndoRedoTrace: [redo] 准备恢复: current.title='1' → next.title='1', current.subTasks=0 → next.subTasks=1
07-22 17:49:54.265  5754  5754 W UndoRedoTrace: [redo] _isRestoring = true
07-22 17:49:54.265  5754  5754 W UndoRedoTrace: [restoreFromSnapshot] 入口: snapshot.title='1', subTasks=[2], todoLinesBefore.size=1, todoLinesBefore.texts=[1]
07-22 17:49:54.265  5754  5754 W UndoRedoTrace: [rebuildTodoLinesFromSnapshot] 完成: size=2, texts=[1, 2], title='1', snapshot.subTasks.size=1, snapshot.subTasks.titles=[2], mainGroupId=0, ⚠️ 注意：snapshot.subTasks 不包含空白子任务行，若用户撤销时正在编辑空白子待办行，重建后会丢失占位行
07-22 17:49:54.265  5754  5754 W UndoRedoTrace: [restoreFromSnapshot] _todoLines 已更新: size=2, texts=[1, 2]
07-22 17:49:54.265  5754  5754 W UndoRedoTrace: [redo] _isRestoring = false
07-22 17:49:54.265  5754  5754 W UndoRedoTrace: [redo] ✅ 完成: canUndo=true, canRedo=false, todoLinesAfter.texts=[1, 2]
07-22 17:49:54.297  5754  5754 W UndoRedoTrace: [UI] canUndo / canRedo 变化: canUndo=true, canRedo=false, isRestoring=false
07-22 17:49:54.297  5754  5754 W UndoRedoTrace: [UI LaunchedEffect(restoreEvent)] 触发: snapshot.title='1', subTasks=[2], todoLinesAtTrigger.size=2, todoLinesAtTrigger.texts=[1, 2]