# Case Study: `AndroidFont` (Compose UI 1.11.2)

Source: `~/.gradle/caches/modules-2/files-2.1/androidx.compose.ui/ui-text-android/1.11.2/<hash>/ui-text-android-1.11.2-sources.jar` → `androidMain/androidx/compose/ui/text/font/AndroidFont.android.kt`

## Key facts (verified from source, NOT from memory)

1. **`AndroidFont` is `abstract`** — it cannot be instantiated directly via `AndroidFont(...)`. You must subclass it (the KDoc recommends a private/internal subclass).

2. **Primary constructor signature:**
   ```kotlin
   abstract class AndroidFont
   constructor(
       final override val loadingStrategy: FontLoadingStrategy,
       val typefaceLoader: TypefaceLoader,
       variationSettings: FontVariation.Settings,
   ) : Font
   ```
   - 1st param is `FontLoadingStrategy` (NOT `weight`, NOT a `name` string).
   - There is **no `weight` parameter** in the constructor; `weight`/`style` come from the `Font` interface and must be overridden in the subclass.
   - There is a deprecated 2-arg constructor `AndroidFont(loadingStrategy, typefaceLoader)` delegating with `FontVariation.Settings()`.

3. **`TypefaceLoader` interface (nested in `AndroidFont`):**
   ```kotlin
   interface TypefaceLoader {
       fun loadBlocking(context: Context, font: AndroidFont): Typeface?
       suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface?
   }
   ```
   - Method is `loadBlocking` (not `loadBlocked`).
   - Parameter type is `AndroidFont` (not the base `Font`).

4. **`Font` interface** requires `val weight: FontWeight` and `val style: FontStyle` (no defaults in the interface) — so the subclass must `override val weight` / `override val style`.

## Correct minimal implementation pattern

```kotlin
private class MyTypefaceLoader(private val resId: Int) : AndroidFont.TypefaceLoader {
    override fun loadBlocking(context: Context, font: AndroidFont): Typeface? {
        val file = File(context.cacheDir, "ff_${resId}.ttf")
        if (!file.exists()) {
            context.resources.openRawResource(resId).use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
        }
        return Typeface.Builder(file.absolutePath).build() ?: Typeface.DEFAULT
    }
    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface? =
        loadBlocking(context, font)
}

private class MyAndroidFont(resId: Int, weight: FontWeight) : AndroidFont(
    loadingStrategy = FontLoadingStrategy.Blocking,
    typefaceLoader = MyTypefaceLoader(resId),
    variationSettings = FontVariation.Settings(weight, FontStyle.Normal),
) {
    override val weight: FontWeight = weight
    override val style: FontStyle = FontStyle.Normal
}
```

## Gotcha that motivated this skill

Guessing `AndroidFont(weight, MyTypefaceLoader(resId), "name")` fails with three errors at once: abstract-class instantiation, `loadBlocked overrides nothing`, and `Argument type mismatch` (weight→FontLoadingStrategy, name→FontVariation.Settings). Reading the real source first yields the correct shape immediately.

## Built-in alternative

`Font(file: File, weight, style)` returns `AndroidFileFont`, which also uses `Typeface.Builder(file)` (mmap, no Java-heap byte[]). In `AndroidPreloadedFont.android.kt`, `AndroidFileFont.doLoad` calls `Typeface.Builder(file).build()`. Use this when a plain file-based font is enough and a custom loader/subclass is unnecessary.

---

# Case Study: `PointerInputChange.position` (Compose UI 1.11.2)

Source: `ui-android-1.11.2-sources.jar` → `commonMain/androidx/compose/ui/input/pointer/PointerEvent.kt`

注意：**这个类在 `-android` 产物的 `commonMain/` 下**，不在 `ui`（common 版）产物里。

## Symptoms

```
e: ... Expression 'position' of type 'Offset' cannot be invoked as a function. Function 'invoke()' is not found.
e: ... Unresolved reference 'x'.
```
即代码写了 `change.position().x`。

## Verified facts

| 成员 | 形态 | 说明 |
|---|---|---|
| `position` | `val position: Offset` | **属性**，直接 `.x` / `.y` |
| `positionChange()` | `fun ... : Offset` | 函数，返回相对上一帧位移 |
| `positionChanged()` | `fun ... : Boolean` | 函数 |
| `positionChangeIgnoreConsumed()` | `fun ... : Offset` | 函数 |
| `positionChangedIgnoreConsumed()` | `fun ... : Boolean` | 函数 |
| `consume()` | `fun consume()` | **未废弃**，现行 API |
| `consumeDownChange()` / `consumePositionChange()` / `consumeAllChanges()` | 扩展函数 | 均已 `@Deprecated` |

正确写法（`detectDragGestures { change, _ -> ... }` 内）：

```kotlin
val x = change.position.x          // 不是 change.position().x
val y = change.position.y
change.consume()
```

`detectTapGestures { offset -> }` 的 lambda 参数本身就是 `Offset`，天然就写 `offset.x` —— 同一个回调里两种写法并存时尤其容易顺手给 `position` 也加上括号。

## 产物/路径规律（本次一并确认）

- `files-2.1` 的 group 目录是**点分隔扁平名**：`androidx.compose.ui`，不是 `androidx/compose/ui`。
- 多平台源码文件名带 source-set 后缀：真实条目是 `AndroidFont.android.kt`、`Foo.jvmAndAndroid.kt`，
  所以 `--match` / `--class` 传裸类名，不要带 `.kt`。
- 全缓存扫描 419 个 sources jar 约 3.2 秒，不加过滤也能接受。

---

# Case Study: `Color(Long)` 位打包 (Compose UI Graphics 1.11.2)

Source: `ui-graphics-android-1.11.2-sources.jar` → `commonMain/androidx/compose/ui/graphics/Color.kt`

```kotlin
fun Color(color: Long): Color {
    return Color((color shl 32).toULong())
}
```

即：**只取低 32 位当作 ARGB int**，左移 32 位后成为 `value`。分量位域（`value` 低 6 位为 0 时＝sRGB 8bit）：

- alpha = bits 56-63，red = 48-55，green = 40-47，blue = 32-39

实践结论：把 `android.graphics.Color.HSVToColor()` 之类的 int ARGB 转成 Compose `Color` 时，
`Color(argb.toLong())` 即可（`shl 32` 会自然丢掉符号扩展的高位），写
`Color(argb.toLong() and 0xFFFFFFFFL)` 也正确、只是掩码冗余。
