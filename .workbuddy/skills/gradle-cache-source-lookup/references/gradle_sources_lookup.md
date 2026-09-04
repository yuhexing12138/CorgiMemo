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
