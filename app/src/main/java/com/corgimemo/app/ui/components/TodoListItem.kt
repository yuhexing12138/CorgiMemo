package com.corgimemo.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.SubTask
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.ui.util.formatReminderDisplay
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 寰呭姙鍒楄〃椤圭粍浠?
 *
 * @param todo 寰呭姙鏁版嵁
 * @param subTaskProgress 瀛愪换鍔¤繘搴︼紙濡?"2/5"锛屾棤瀛愪换鍔℃椂涓?null锛?
 * @param subTasks 瀛愪换鍔″垪琛?
 * @param isExpanded 鏄惁灞曞紑鏄剧ず瀛愪换鍔?
 * @param isBatchMode 鏄惁澶勪簬鎵归噺閫夋嫨妯″紡
 * @param isSelected 鏄惁宸查€変腑锛堟壒閲忔ā寮忎笅锛?
 * @param isDragging 鏄惁姝ｅ湪琚嫋鎷斤紙鐢ㄤ簬璋冩暣 DragHandle 绛夊瓙缁勪欢鏍峰紡锛?
 * @param categoryName 鍒嗙被鍚嶇О
 * @param categoryIcon 鍒嗙被鍥炬爣锛坋moji锛?
 * @param onToggleComplete 鍒囨崲瀹屾垚鐘舵€佸洖璋?
 * @param onDelete 鍒犻櫎鍥炶皟
 * @param onClick 鐐瑰嚮鍥炶皟锛堟櫘閫氭ā寮忥級
 * @param onLongClick 闀挎寜鍥炶皟锛堣繘鍏ユ壒閲忔ā寮忥級
 * @param onSelectClick 閫夋嫨鍥炶皟锛堟壒閲忔ā寮忎笅鐐瑰嚮锛?
 * @param onShareAsImage 鍒嗕韩涓哄浘鐗囧洖璋?
 * @param onPinClick 缃《鍥炶皟锛堝乏婊戝悗鐐瑰嚮缃《鎸夐挳瑙﹀彂锛?
 * @param onShare 鍒嗕韩鍥炶皟锛堝乏婊戝悗鐐瑰嚮鍒嗕韩鎸夐挳瑙﹀彂锛屼笌 onShareAsImage 璇箟涓€鑷达紝缁熶竴鍒嗕韩涓哄浘鐗囷級
 * @param onToggleExpand 鍒囨崲灞曞紑鐘舵€佸洖璋?
 * @param onToggleSubTask 鍒囨崲瀛愪换鍔″畬鎴愮姸鎬佸洖璋?
 * @param start 鍓嶇疆鍐呭妲戒綅锛堢敤浜庢斁缃?DragHandle 绛夋嫋鎷界浉鍏?UI锛?
 * @param relationHint 鍏宠仈鎻愮ず鏂囧瓧
 * @param searchQuery 鎼滅储鍏抽敭璇嶏紙闈炵┖鏃跺鏍囬鍜屽唴瀹硅繘琛岄珮浜樉绀猴級
 */
@OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
@Composable
fun TodoListItem(
    todo: TodoItem,
    subTaskProgress: String? = null,
    subTasks: List<SubTask> = emptyList(),
    isExpanded: Boolean = false,
    isBatchMode: Boolean = false,
    isSelected: Boolean = false,
    isDragging: Boolean = false,
    categoryName: String? = null,
    categoryIcon: String? = null,
    onToggleComplete: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onSelectClick: () -> Unit = {},
    onShareAsImage: () -> Unit = {},
    onPinClick: () -> Unit = {},
    onShare: () -> Unit = {},
    onToggleExpand: () -> Unit = {},
    onToggleSubTask: (Long) -> Unit = {},
    start: @Composable () -> Unit = {},
    relationHint: String? = null,
    /** 鎼滅储鍏抽敭璇嶏紙闈炵┖鏃跺鏍囬鍜屽唴瀹硅繘琛岄珮浜樉绀猴級 */
    searchQuery: String = ""
) {
    /** 閫愬尯闂村姩鐢诲弬鏁帮細姣忓瓧绗﹀欢杩?2ms锛屾渶澶у欢杩熶笂闄?300ms */
    val STAGGER_DELAY_PER_CHAR = 2
    val STAGGER_MAX_DELAY = 300

    /**
     * V2.5 閫愬尯闂翠氦閿欐贰鍏ュ姩鐢绘帶鍒舵爣蹇?
     *
     * 褰?searchQuery 闈炵┖鏃跺惎鐢紝鎵€鏈夐珮浜尯闂翠互銆屼粠宸﹀埌鍙虫尝娴壂鎻忋€嶆柟寮忎緷娆℃贰鍏ャ€?
     */
    val isHighlightActive = searchQuery.isNotBlank()

    /** 鍗＄墖鑳屾櫙鑹诧細閫変腑鏃朵娇鐢?primaryContainer 鍗婇€忔槑锛屽惁鍒欎娇鐢?surface */
    val cardBackground by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(durationMillis = 200),
        label = "cardBackground"
    )

    /** 澶嶉€夋宸︿晶闂磋窛锛氭壒閲忔ā寮忎笅绌哄嚭 8dp 璁╀綅缁?Checkbox */
    val checkboxStartPadding by animateDpAsState(
        targetValue = if (isBatchMode) 8.dp else 0.dp,
        animationSpec = tween(durationMillis = 200),
        label = "checkboxStartPadding"
    )

    var showLongPressMenu by remember { mutableStateOf(false) }

    val actionSheetState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .combinedClickable(
                onClick = {
                    if (isBatchMode) {
                        onSelectClick()
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    if (isBatchMode) {
                        onLongClick()
                    } else {
                        showLongPressMenu = true
                    }
                },
                role = Role.Tab
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground)
) {
        // 鍗＄墖鍐呴儴锛氬乏渚?4dp 浼樺厛绾х珫鏉?+ 鍙充晶鍐呭
        // 浣跨敤 IntrinsicSize.Max 璁?PriorityBar.fillMaxHeight() 鑳芥纭拺婊″崱鐗囬珮搴?
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max)
        ) {
            /** 宸︿晶 4dp 浼樺厛绾х珫鏉★紙鏃犱紭鍏堢骇鏃堕€忔槑锛屼笉鍗犺瑙夌┖闂达級 */
            PriorityBar(priority = todo.priority, isCompleted = todo.status == 1)

            /** 鍐呭鍖哄煙锛屽崰婊￠櫎绔栨潯澶栫殑瀹藉害 */
            Column(modifier = Modifier.weight(1f)) {
                // 椤堕儴 Row锛氬閫夋 + start 妲戒綅 + 鍐呭 + 灞曞紑鎸夐挳
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 澶嶉€夋鍖哄煙
                    if (isBatchMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onSelectClick() },
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    } else {
                        if (checkboxStartPadding > 0.dp) {
                            Spacer(modifier = Modifier.width(checkboxStartPadding))
                        }
                        CircularCheckbox(
                            checked = todo.status == 1,
                            onCheckedChange = { isChecked ->
                                onToggleComplete(todo.id, isChecked)
                            },
                            // 宸插畬鎴愭€佽瑙夐檷鏉冿細鍕鹃€夋鍙樻贰锛堜繚鎸佹鑹茬郴浠呴檷娣卞害锛?
                            dimmed = todo.status == 1,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }

                    /**
                     * 鍓嶇疆鍐呭妲戒綅锛坰tart slot锛?
                     *
                     * 鐢ㄤ簬鏀剧疆 DragHandle 绛夋嫋鎷界浉鍏崇殑 UI 缁勪欢銆?
                     * 榛樿涓虹┖锛堜笉娓叉煋浠讳綍鍐呭锛夛紝
                     * 褰撲笌 ReorderableLazyColumn 閰嶅悎浣跨敤鏃讹紝
                     * 鍙湪姝ゅ鎻掑叆 VerticalDragIndicator銆?
                     */
                    start()

                    // 鏍囬 + 鍒嗙被 + 鏃堕棿绛夊唴瀹?Column
                    Column(modifier = Modifier.weight(1f)) {
                        // 鏍囬琛?
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            /**
                             * 鏍囬鏂囨湰锛堟敮鎸侀€愬尯闂翠氦閿欐贰鍏ラ珮浜級
                             *
                             * V2.5 鏀归€狅細浣跨敤 buildHighlightRanges() 鎷嗗垎涓虹嫭绔嬪尯闂村垪琛紝
                             * 姣忎釜楂樹寒鍖洪棿鎷ユ湁鐙珛鐨?animateFloatAsState + 寤惰繜锛?
                             * 瀹炵幇浠庡乏鍒板彸鐨勬尝娴紡娣″叆鏁堟灉銆?
                             */
                            if (isHighlightActive) {
                                val (titleRanges, titleHighlightColor) =
                                    com.corgimemo.app.util.HighlightUtil.buildHighlightRanges(
                                        text = todo.title,
                                        searchQuery = searchQuery,
                                        containerBgColor = if (todo.backgroundColor != 0)
                                            Color(todo.backgroundColor) else null
                                    )
                                /** 閫愬尯闂存覆鏌擄細姣忎釜 HighlightRange 鐙珛鍔ㄧ敾 */
                                androidx.compose.foundation.layout.Row {
                                    titleRanges.forEach { range ->
                                        val rangeAlpha by androidx.compose.animation.core.animateFloatAsState(
                                            targetValue = 1f,
                                            animationSpec = androidx.compose.animation.core.tween(
                                                durationMillis = 300,
                                                delayMillis = (range.startIndex * STAGGER_DELAY_PER_CHAR)
                                                    .coerceAtMost(STAGGER_MAX_DELAY)
                                            ),
                                            label = "titleRangeAlpha_${range.startIndex}"
                                        )
                                        Text(
                                            text = range.text,
                                            fontSize = 16.sp,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                            textDecoration = if (todo.status == 1) TextDecoration.LineThrough else TextDecoration.None,
                                            color = if (todo.status == 1) MaterialTheme.colorScheme.onSurfaceVariant
                                            else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.graphicsLayer { alpha = rangeAlpha },
                                            style = if (range.isHighlight) androidx.compose.ui.text.TextStyle(
                                                background = titleHighlightColor
                                            ) else androidx.compose.ui.text.TextStyle.Default
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = todo.title,
                                    fontSize = 16.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                    textDecoration = if (todo.status == 1) TextDecoration.LineThrough else TextDecoration.None,
                                    color = if (todo.status == 1) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // 瀹屾垚鏃堕棿
                        if (todo.status == 1 && todo.completedAt != null) {
                            Text(
                                text = formatCompletedTime(todo.completedAt),
                                fontSize = 12.sp,
                                // 宸插畬鎴愭€佽瑙夐檷鏉冿細浣跨敤 CompletedColors.Text 鑰岄潪 primary 姗欒壊
                                color = CompletedColors.Text,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        // 鎻愰啋鏃堕棿 + 闄勪欢鏁伴噺锛堣仛鍚堬細鐖?+ 鎵€鏈夊瓙浠诲姟锛?
                        val aggregateCounts = aggregateAttachmentCounts(todo, subTasks)
                        if (todo.reminderTime != null || aggregateCounts.first > 0 || aggregateCounts.second > 0 || categoryName != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                // 鍒嗙被锛堝唴鑱斿睍绀猴紝甯﹂槾褰辨晥鏋滐級
                                if (categoryName != null) {
                                    CategoryTagWithShadow(
                                        categoryName = categoryName!!,
                                        categoryIcon = categoryIcon,
                                        isCompleted = todo.status == 1
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                if (todo.reminderTime != null) {
                                    val reminder = formatReminderDisplay(todo.reminderTime)
                                    // 宸插畬鎴愭€佽瑙夐檷鏉冿細寮哄埗浣跨敤 CompletedColors.Text 鐏拌壊锛?
                                    // 瑕嗙洊"宸茶繃鏈?鍦烘櫙涓嬬殑绾㈣壊锛圕olor(0xFFDC2626)锛?
                                    val reminderColor = if (todo.status == 1) {
                                        CompletedColors.Text
                                    } else if (reminder.isOverdue) {
                                        Color(0xFFDC2626)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Alarm,
                                        contentDescription = if (reminder.isOverdue) "宸茶繃鏈熸彁閱? else "鎻愰啋",
                                        tint = reminderColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = reminder.text,
                                        fontSize = 12.sp,
                                        color = reminderColor,
                                        fontWeight = if (reminder.isOverdue && todo.status != 1)
                                            androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))   // 鎻愰啋涓庨檮浠堕棿 1 涓┖鏍肩殑闂磋窛
                                }

                                // 闄勪欢璁℃暟锛堝浘鐗?+ 璇煶锛?
                                if (aggregateCounts.first > 0 || aggregateCounts.second > 0) {
                                    val attachmentText = buildString {
                                        if (aggregateCounts.second > 0) append("馃帳脳${aggregateCounts.second}")
                                        if (aggregateCounts.first > 0 && aggregateCounts.second > 0) append(" ")  // 涓ょ闄勪欢闂?1 涓┖鏍?
                                        if (aggregateCounts.first > 0) append("馃柤脳${aggregateCounts.first}")
                                    }
                                    Text(
                                        text = attachmentText,
                                        fontSize = 12.sp,
                                        // 宸插畬鎴愭€佽瑙夐檷鏉冿細浣跨敤 CompletedColors.Text 鑰岄潪 onSurfaceVariant
                                        color = if (todo.status == 1) CompletedColors.Text
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // 鍒嗙被琛屽凡鍒犻櫎锛堣縼绉诲埌鎻愰啋琛屽乏渚э紝璇﹁涓嬫柟 CategoryTagWithShadow锛?

                        // 鍏宠仈鎻愮ず
                        if (relationHint != null) {
                            Text(
                                text = relationHint,
                                fontSize = 12.sp,
                                color = Color(0xFF999999),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        // 寮€濮嬫椂闂存樉绀?
                        if (todo.startDate != null) {
                            Column(modifier = Modifier.padding(top = 4.dp)) {
                                val timeDisplayText = todo.estimatedDurationMinutes?.let { duration ->
                                    val endTime = todo.startDate + duration * 60 * 1000
                                    formatTimeRange(todo.startDate, endTime)
                                } ?: formatDateTime(todo.startDate)

                                Text(
                                    text = "\uD83D\uDD51 $timeDisplayText",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (todo.status != 1 && System.currentTimeMillis() < todo.startDate) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    CountdownDisplay(startDate = todo.startDate)
                                }
                            }
                        }

                        /** 鎴鏃堕棿锛坉ueDate锛夋樉绀?*/
                        if (todo.dueDate != null) {
                            val isOverdue = todo.status != 1 && todo.dueDate!! < System.currentTimeMillis()
                            Column(modifier = Modifier.padding(top = 4.dp)) {
                                Text(
                                    text = "\u23F0 ${formatDateTime(todo.dueDate!!)}${if (isOverdue) " (宸茶繃鏈?" else ""}",
                                    fontSize = 12.sp,
                                    color = if (isOverdue) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isOverdue) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal
                                )
                            }
                        }

                        // 杩涘害鏉?
                        if (todo.status != 1 && todo.content.isNullOrBlank() && subTaskProgress != null) {
                            SubTaskProgressBar(
                                progress = parseProgress(subTaskProgress),
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }

                    // 瀛愪换鍔¤繘搴︼紙绉昏嚦灞曞紑鎸夐挳宸︿晶锛? 灞曞紑/鏀惰捣鎸夐挳锛堝甫闃村奖锛?
                    if (subTaskProgress != null && !isBatchMode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 瀛愪换鍔¤繘搴︽枃鏈細绱ц创灞曞紑鎸夐挳宸︿晶
                            Text(
                                text = "($subTaskProgress)",
                                fontSize = 13.sp,
                                // 宸插畬鎴愭€佽瑙夐檷鏉冿細浣跨敤 CompletedColors.Text 鑰岄潪 primary 姗欒壊
                                color = if (todo.status == 1) CompletedColors.Text
                                        else MaterialTheme.colorScheme.primary,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            // 灞曞紑/鏀惰捣鎸夐挳锛歋urface 鍦嗗舰闃村奖 2dp
                            Surface(
                                onClick = onToggleExpand,
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 2.dp,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isExpanded) {
                                            Icons.Default.ExpandLess
                                        } else {
                                            Icons.Default.ExpandMore
                                        },
                                        contentDescription = if (isExpanded) "鏀惰捣" else "灞曞紑",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // 灞曞紑鏃舵樉绀哄瓙浠诲姟鍒楄〃
                if (isExpanded && subTasks.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 60.dp, end = 16.dp, bottom = 16.dp)
                    ) {
                        subTasks.forEach { subTask ->
                            SubTaskInTodoListItem(
                                subTask = subTask,
                                isParentCompleted = todo.status == 1,
                                onToggleComplete = { onToggleSubTask(subTask.id) }
                            )
                            if (subTask != subTasks.last()) {
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        }
}   // 鍏抽棴 Card

// ModalBottomSheet 绫诲瀷鐨勯暱鎸夎彍鍗曚负椤跺眰寮瑰眰锛岀嫭绔嬩簬鍗＄墖涓讳綋娓叉煋
if (showLongPressMenu) {
    TodoActionSheet(
        sheetState = actionSheetState,
        onDismiss = {
            showLongPressMenu = false
        },
        onEdit = {
            onClick()
        },
        onShare = {
            onShareAsImage()
        },
        onBatchSelect = {
            onLongClick()
        },
        onDelete = {
            onDelete(todo.id)
        }
    )
}
}   // 鍏抽棴 TodoListItem 鍑芥暟

/**
 * 鏍煎紡鍖栧畬鎴愭椂闂翠负鍙嬪ソ鐨勬樉绀烘枃鏈?
 *
 * @param completedAt 瀹屾垚鏃堕棿鎴筹紙姣锛?
 * @return 鏍煎紡鍖栧悗鐨勬椂闂存枃鏈紝濡?"3 鍒嗛挓鍓嶅畬鎴?銆?2 灏忔椂鍓嶅畬鎴?銆?5 澶╁墠瀹屾垚"
 */
private fun formatCompletedTime(completedAt: Long): String {
    val diffMillis = System.currentTimeMillis() - completedAt
    val diffMinutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)
    val diffHours = TimeUnit.MILLISECONDS.toHours(diffMillis)
    val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis)

    return when {
        diffMinutes < 1 -> "鍒氬垰瀹屾垚"
        diffMinutes < 60 -> "$diffMinutes 鍒嗛挓鍓嶅畬鎴?
        diffHours < 24 -> "$diffHours 灏忔椂鍓嶅畬鎴?
        else -> "$diffDays 澶╁墠瀹屾垚"
    }
}

/**
 * 瀛愪换鍔¤繘搴︽潯缁勪欢
 *
 * @param progress 杩涘害鍊?(0.0-1.0)
 * @param modifier Modifier
 */
@Composable
private fun SubTaskProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val warmOrange = Color(0xFFF97316)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(trackColor)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
        ) {
            val trackWidth = size.width
            val progressWidth = trackWidth * progress.coerceIn(0f, 1f)
            val height = size.height

            // 缁樺埗杩涘害鏉★紙鏆栨鑹诧級
            if (progressWidth > 0f) {
                drawRoundRect(
                    color = warmOrange,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(progressWidth, height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                )
            }
        }
    }
}

/**
 * 瑙ｆ瀽杩涘害鏂囨湰涓烘诞鐐瑰€?
 *
 * @param progressText 杩涘害鏂囨湰锛屽 "2/5"
 * @return 杩涘害鍊?(0.0-1.0)锛岃В鏋愬け璐ヨ繑鍥?0f
 */
private fun parseProgress(progressText: String): Float {
    return try {
        val parts = progressText.split("/")
        if (parts.size == 2) {
            val completed = parts[0].toInt()
            val total = parts[1].toInt()
            if (total > 0) completed.toFloat() / total.toFloat() else 0f
        } else {
            0f
        }
    } catch (e: Exception) {
        0f
    }
}

/**
 * 寰呭姙鍒楄〃涓殑瀛愪换鍔￠」缁勪欢
 * 鐢ㄤ簬灞曞紑寰呭姙鍚庢樉绀虹殑瀛愪换鍔″垪琛?
 *
 * @param subTask 瀛愪换鍔?
 * @param onToggleComplete 鍒囨崲瀹屾垚鐘舵€佸洖璋?
 * @param modifier Modifier
 */
@Composable
private fun SubTaskInTodoListItem(
    subTask: SubTask,
    isParentCompleted: Boolean = false,
    onToggleComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 鍦嗗舰澶嶉€夋
        SubTaskCheckbox(
            isCompleted = subTask.isCompleted,
            isParentCompleted = isParentCompleted,
            onClick = onToggleComplete
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 瀛愪换鍔℃爣棰?
        Text(
            text = subTask.title,
            fontSize = 14.sp,
            color = if (subTask.isCompleted) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textDecoration = if (subTask.isCompleted) {
                TextDecoration.LineThrough
            } else {
                TextDecoration.None
            },
            modifier = Modifier.weight(1f)
        )

        // 瀛愪换鍔¤嚜韬檮浠惰鏁帮紙鐙珛浜庣埗鍗¤仛鍚堬級
        val subImageCount = parseImagePathsCount(subTask.imagePaths)
        val subVoiceCount = parseVoicePathsCount(subTask.voicePaths)
        if (subImageCount > 0 || subVoiceCount > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            val text = buildString {
                if (subVoiceCount > 0) append("馃帳脳$subVoiceCount")
                if (subImageCount > 0 && subVoiceCount > 0) append(" ")
                if (subImageCount > 0) append("馃柤脳$subImageCount")
            }
            Text(
                text = text,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 瀛愪换鍔″閫夋缁勪欢锛堜笌SubTaskListItem涓€鑷达級
 *
 * @param isCompleted 鏄惁宸插畬鎴?
 * @param onClick 鐐瑰嚮鍥炶皟
 */
@Composable
private fun SubTaskCheckbox(
    isCompleted: Boolean,
    isParentCompleted: Boolean = false,
    onClick: () -> Unit
) {
    /**
     * 鍕鹃€夋鑳屾櫙鑹诧細
     * - 瀛愪换鍔℃湭瀹屾垚 鈫?娴呯伆鎻忚竟
     * - 瀛愪换鍔″畬鎴?+ 鐖跺緟鍔炴湭瀹屾垚 鈫?primary 姗欒壊锛堥儴鍒嗗畬鎴愮殑瑙嗚寮鸿皟锛?
     * - 瀛愪换鍔″畬鎴?+ 鐖跺緟鍔炲凡瀹屾垚 鈫?CompletedColors.CheckboxBgDim 娴呮锛堜繚鎸佹鑹茬郴锛屼粎闄嶆繁搴︼級
     *
     * 娉ㄦ剰锛氱敤鎴疯姹?淇濇寔姗欒壊绯婚厤鑹叉柟妗堬紝涓嶅緱鏇存敼涓虹伆鑹诧紝瀹炵幇棰滆壊娣卞害闄嶄綆鏁堟灉"
     * 鍥犳鐖跺緟鍔炲畬鎴愭椂涔熺敤娴呮锛?FFCCAB锛夛紝鑰岄潪 CheckboxBg 鐨勭伆鑹层€?
     */
    val bgColor = when {
        !isCompleted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        isParentCompleted -> CompletedColors.CheckboxBgDim
        else -> MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isCompleted) {
            Text(
                text = "鉁?,
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * 鏍煎紡鍖栨棩鏈熸椂闂翠负鏄剧ず鏂囨湰
 *
 * @param timestamp 鏃堕棿鎴筹紙姣锛?
 * @return 鏍煎紡鍖栧悗鐨勬棩鏈熸椂闂存枃鏈紝濡?"05-15 14:30"
 */
private fun formatDateTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * 鏍煎紡鍖栨椂闂磋寖鍥翠负鏄剧ず鏂囨湰
 *
 * @param startTime 寮€濮嬫椂闂存埑锛堟绉掞級
 * @param endTime 缁撴潫鏃堕棿鎴筹紙姣锛?
 * @return 鏍煎紡鍖栧悗鐨勬椂闂磋寖鍥存枃鏈?
 *         鍚屼竴澶╋細05-22 15:00 鑷?17:30
 *         闅斿ぉ锛?5-22 15:00 鑷?05-23 17:30
 */
private fun formatTimeRange(startTime: Long, endTime: Long): String {
    val sdfDate = SimpleDateFormat("MM-dd", Locale.getDefault())
    val sdfDateTime = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())

    val startDate = Date(startTime)
    val endDate = Date(endTime)

    val cal1 = java.util.Calendar.getInstance().apply { time = startDate }
    val cal2 = java.util.Calendar.getInstance().apply { time = endDate }

    val isSameDay = cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
            cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)

    return if (isSameDay) {
        "${sdfDate.format(startDate)} ${sdfTime.format(startDate)} 鑷?${sdfTime.format(endDate)}"
    } else {
        "${sdfDateTime.format(startDate)} 鑷?${sdfDateTime.format(endDate)}"
    }
}

/**
 * 璁＄畻骞舵牸寮忓寲璺濈寮€濮嬫椂闂寸殑鍓╀綑鏃堕棿
 *
 * @param startDate 寮€濮嬫椂闂存埑锛堟绉掞級
 * @param currentTime 褰撳墠鏃堕棿鎴筹紙姣锛?
 * @return 鏍煎紡鍖栧悗鐨勫墿浣欐椂闂存枃鏈紝澶т簬1灏忔椂鏄剧ず鍒板垎閽燂紝灏忎簬绛変簬1灏忔椂鏄剧ず鍒扮
 */
private fun formatCountdown(startDate: Long, currentTime: Long): String {
    val diffMillis = startDate - currentTime

    if (diffMillis <= 0) return "宸插紑濮?

    val diffSeconds = TimeUnit.MILLISECONDS.toSeconds(diffMillis)
    val diffMinutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)
    val diffHours = TimeUnit.MILLISECONDS.toHours(diffMillis)
    val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis)

    return if (diffHours > 0) {
        when {
            diffDays > 0 -> "杩樺墿 ${diffDays}澶?${diffHours % 24}鏃?{diffMinutes % 60}鍒?
            else -> "杩樺墿 ${diffHours}鏃?{diffMinutes % 60}鍒?
        }
    } else {
        "杩樺墿 ${diffMinutes}鍒?{diffSeconds % 60}绉?
    }
}

/**
 * 浼樺厛绾х珫鏉?- 鏄剧ず鍦ㄥ緟鍔炲崱鐗囧乏渚?4dp 瀹界殑褰╄壊绾挎潯
 *
 * 棰滆壊鏍规嵁 todo.priority 鍔ㄦ€佸彉鍖栵細
 * - 0 (鏃犱紭鍏堢骇) 鈫?閫忔槑
 * - 1 (浣? 鈫?PriorityColors.Low锛堟煍钃濓級
 * - 2 (涓? 鈫?PriorityColors.Medium锛堟煍姗欙級
 * - 3 (楂? 鈫?PriorityColors.High锛堟煍绾級
 *
 * 閫氳繃 animateColorAsState 瀹炵幇 200ms 棰滆壊骞虫粦杩囨浮銆?
 * 楂樺害閫氳繃 fillMaxHeight() 鑷€傚簲鐖跺鍣紙Card锛夛紝鏃犻渶纭紪鐮併€?
 *
 * @param priority 浼樺厛绾ф暟鍊?
 * @param modifier Modifier
 */
@Composable
private fun PriorityBar(
    priority: Int,
    isCompleted: Boolean = false,
    modifier: Modifier = Modifier
) {
    /** 鐩爣棰滆壊锛氭湭瀹屾垚鐢ㄥ師鑹诧紝宸插畬鎴愮敤娴呰壊鐗堬紙淇濈暀鑹茬浉浣嗛檷楗卞拰锛?*/
    val targetColor = if (isCompleted) {
        PriorityColors.dimColorOf(priority)
    } else {
        PriorityColors.colorOf(priority)
    }

    /** 棰滆壊杩囨浮鍔ㄧ敾锛氫笌鍗＄墖鍏朵粬鍔ㄧ敾淇濇寔 200ms 鑺傚涓€鑷?*/
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 200),
        label = "PriorityBarColor"
    )

    Box(
        modifier = modifier
            .width(4.dp)
            .fillMaxHeight()
            .background(animatedColor)
    )
}

/**
 * 鍊掕鏃舵樉绀虹粍浠?
 * 瀹炴椂鏄剧ず璺濈寮€濮嬫椂闂寸殑鍓╀綑鏃堕棿
 *
 * @param startDate 寮€濮嬫椂闂存埑锛堟绉掞級
 * @param onExpired 鍊掕鏃剁粨鏉熸椂鐨勫洖璋?
 */
@Composable
private fun CountdownDisplay(
    startDate: Long,
    onExpired: () -> Unit = {}
) {
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(startDate) {
        while (true) {
            val now = System.currentTimeMillis()
            currentTime = now

            if (now >= startDate) {
                onExpired()
                break
            }

            val remainingMillis = startDate - now
            val delayMillis = if (remainingMillis > 3600000L) {
                60000L
            } else {
                1000L
            }

            delay(delayMillis)
        }
    }

    val countdownText = formatCountdown(startDate, currentTime)

    Text(
        text = "鈴?$countdownText",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.primary
    )
}

/**
 * 瑙ｆ瀽 JSON 鏁扮粍鏍煎紡鐨勫浘鐗囪矾寰勫瓧绗︿覆锛岃繑鍥炲浘鐗囨暟閲?
 *
 * @param imagePathsJson org.json.JSONArray 搴忓垪鍖栫殑瀛楃涓?
 * @return 鍥剧墖鏁伴噺锛堣В鏋愬け璐ユ垨涓虹┖杩斿洖 0锛?
 */
private fun parseImagePathsCount(imagePathsJson: String): Int {
    if (imagePathsJson.isBlank()) return 0
    return try {
        org.json.JSONArray(imagePathsJson).length()
    } catch (e: Exception) {
        0
    }
}

/**
 * 瑙ｆ瀽 JSON 鏁扮粍鏍煎紡鐨勮闊宠矾寰勫瓧绗︿覆锛岃繑鍥炶闊虫暟閲?
 *
 * @param voicePathsJson org.json.JSONArray 搴忓垪鍖栫殑瀛楃涓?
 * @return 璇煶鏁伴噺锛堣В鏋愬け璐ユ垨涓虹┖杩斿洖 0锛?
 */
private fun parseVoicePathsCount(voicePathsJson: String): Int {
    if (voicePathsJson.isBlank()) return 0
    return try {
        org.json.JSONArray(voicePathsJson).length()
    } catch (e: Exception) {
        0
    }
}

/**
 * 鑱氬悎寰呭姙鍗＄墖闄勪欢鏁伴噺锛堢埗鑷韩 + 鎵€鏈夊瓙浠诲姟锛?
 *
 * @param todo 鐖跺緟鍔?
 * @param subTasks 瀛愪换鍔″垪琛?
 * @return Pair(鍥剧墖鎬绘暟, 璇煶鎬绘暟)
 */
private fun aggregateAttachmentCounts(
    todo: TodoItem,
    subTasks: List<SubTask>
): Pair<Int, Int> {
    val imageCount = parseImagePathsCount(todo.imagePaths) +
            subTasks.sumOf { parseImagePathsCount(it.imagePaths) }
    val voiceCount = (if (todo.voiceNotePath != null) 1 else 0) +
            subTasks.sumOf { parseVoicePathsCount(it.voicePaths) }
    return imageCount to voiceCount
}

/**
 * 甯﹂槾褰辨晥鏋滅殑鍒嗙被鏍囩
 *
 * 鐢ㄤ簬 TodoListItem 鍗＄墖鎻愰啋琛屽乏渚э細
 * - 闃村奖锛氭按骞冲亸绉?2px锛屽瀭鐩村亸绉?2px锛屾ā绯婂崐寰?4px锛岄鑹?rgba(0,0,0,0.1)
 * - 瀛楀彿 12sp
 * - 宸插畬鎴愭€佷娇鐢?CompletedColors.Text 闄嶆潈
 *
 * 瀹炵幇锛氬弻灞?Box
 * - 澶栧眰 Box锛歮atchParentSize + offset(2.dp, 2.dp) + 鍗婇€忔槑榛戣儗鏅?+ blur(4.dp) 妯℃嫙闃村奖
 * - 鍐呭眰 Row锛氬疄闄呭唴瀹癸紙鑳屾櫙鑹?+ 鍥炬爣 + 鍚嶇О锛?
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun CategoryTagWithShadow(
    categoryName: String,
    categoryIcon: String?,
    isCompleted: Boolean
) {
    val textColor = if (isCompleted) CompletedColors.Text
                    else MaterialTheme.colorScheme.primary
    val bgColor = if (isCompleted) CompletedColors.Text.copy(alpha = 0.12f)
                  else MaterialTheme.colorScheme.primaryContainer

    Box(contentAlignment = Alignment.Center) {
        // 澶栧眰锛氶槾褰憋紙鍋忕Щ 2dp 鍗婇€忔槑榛?+ 4dp 妯＄硦锛?
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 2.dp, y = 2.dp)
                .background(
                    color = Color(0x1A000000),  // rgba(0, 0, 0, 0.1)
                    shape = RoundedCornerShape(4.dp)
                )
                .blur(radius = 4.dp)
        )
        // 鍐呭眰锛氬疄闄呭唴瀹?
        Row(
            modifier = Modifier
                .background(color = bgColor, shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = categoryIcon ?: "馃搵",
                fontSize = 12.sp,
                color = textColor
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = categoryName,
                fontSize = 12.sp,
                color = textColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}
