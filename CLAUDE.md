# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Build

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
./gradlew :materialtagview:assembleDebug
```

AAR resultante: `materialtagview/build/outputs/aar/materialtagview-debug.aar`

El módulo `:app` está excluido de `settings.gradle` (demo original, no migrada a AGP moderno). Solo se construye `:materialtagview`.

---

## Integración completa — CRD en Kotlin

Todo lo necesario para integrar la librería desde cero, sin leer ningún otro archivo.

### 1. Dependencias (`build.gradle`)

```groovy
implementation files('libs/materialtagview-debug.aar')
implementation 'com.google.android.flexbox:flexbox:3.0.0'   // REQUERIDO — TagView extiende FlexboxLayout
```

Sin `flexbox`, AAPT falla con `attribute layout_maxWidth not found`.

### 2. Layout XML

```xml
<com.skyhope.materialtagview.TagView
    android:id="@+id/tag_view"
    android:layout_width="match_parent"
    android:layout_height="wrap_content" />
```

No agregar `app:tag_limit` en XML — su default es **1**. Siempre controlar el límite por código.

### 3. Implementación completa (Fragment/Activity en Kotlin)

```kotlin
import android.graphics.Color
import com.google.android.material.color.MaterialColors
import com.skyhope.materialtagview.TagView
import com.skyhope.materialtagview.enums.TagSeparator
import com.skyhope.materialtagview.interfaces.TagItemListener
import com.skyhope.materialtagview.interfaces.TagLongClickListener
import com.skyhope.materialtagview.model.TagModel

// Variables de estado — mantener sincronizadas con la librería
val availableTagNames = mutableListOf<String>()  // sugerencias en el dropdown
val deletedTagNames   = mutableSetOf<String>()    // borrados globalmente este session

fun initTagView(
    tagView: TagView,
    context: Context,
    allTagNames: List<String>,          // todos los tags existentes en BD
    preSelectedNames: List<String>      // tags ya asignados a este item
) {
    // ── PASO 1: configuración básica ────────────────────────────────────────
    tagView.setHint("Add tag...")
    tagView.addTagSeparator(TagSeparator.SPACE_SEPARATOR)  // espacio crea chip
    tagView.addTagLimit(Int.MAX_VALUE)

    // ── PASO 2: colores ANTES de addTag/setTagList ───────────────────────────
    // Material3 DayNight — se resuelven del tema activo (day/night automático)
    val chipBg   = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSecondaryContainer, Color.GRAY)
    val chipText = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSecondaryContainer, Color.BLACK)
    tagView.setTagBackgroundColor(chipBg)
    tagView.setTagTextColor(chipText)

    // ── PASO 3: listeners ────────────────────────────────────────────────────
    tagView.initTagListener(object : TagItemListener {
        override fun onGetAddedItem(tagModel: TagModel) {
            if (tagModel.isFromList) availableTagNames.remove(tagModel.tagText.trim())
        }
        override fun onGetRemovedItem(tagModel: TagModel) {
            if (tagModel.isFromList) availableTagNames.add(tagModel.tagText.trim())
        }
    })

    // DELETE — long-press en sugerencia del dropdown
    tagView.initTagLongClickListener(TagLongClickListener { _, tagText ->
        AlertDialog.Builder(context)
            .setTitle("Delete tag")
            .setMessage("Delete \"$tagText\" from all notes?")
            .setPositiveButton("Delete") { _, _ ->
                deletedTagNames.add(tagText)
                availableTagNames.remove(tagText)
                tagView.removeDropdownItem(tagText)   // quita del dropdown sin reinicializar
                // → persistir borrado en BD al guardar
            }
            .setNegativeButton("Cancel", null)
            .show()
    })

    // ── PASO 4: chips pre-seleccionados (CREATE desde BD) ────────────────────
    val pool = allTagNames.toMutableList()
    for (name in preSelectedNames) {
        tagView.addTag(name, true)   // true = viene de la lista
        pool.remove(name)
    }

    // ── PASO 5: cargar sugerencias — UNA SOLA VEZ ────────────────────────────
    availableTagNames.addAll(pool)
    tagView.setTagList(ArrayList(pool.sorted()))
    // ¡NUNCA volver a llamar setTagList()! Duplica hijos en el FlexboxLayout.
}
```

### 4. READ — obtener tags seleccionados al guardar

```kotlin
// Leer en el hilo principal ANTES de entrar a IO
val tagsToSave = tagView.getSelectedTags()
    .map { it.tagText.trim() }
    .filter { it.isNotEmpty() }

// En IO:
// - Procesar deletedTagNames primero (borrar de BD y asociaciones)
// - Luego reemplazar asociaciones del item actual con tagsToSave
// - Crear tags nuevos (los que no existen en BD) con tagRepo.findByName / saveNew
```

### 5. DELETE — persistencia al guardar

```kotlin
// Kotlin name-shadowing trap: capturar IDs fuera del apply{}
for (name in deletedTagNames) {
    val tag = tagRepo.findByName(name) ?: continue
    val tagId = tag.id                             // capturar ANTES del apply
    // marcar como eliminado en BD
    // actualizar campo .tags (string denormalizado) en todos los items afectados
}
```

---

## Invariantes críticos

| Regla | Consecuencia si se viola |
|---|---|
| `setTagList()` se llama exactamente una vez | Segunda llamada duplica `EditText + RecyclerView + TextView` en el layout |
| Colores **antes** de `addTag()` y `setTagList()` | Chips pre-seleccionados y `textViewAdd` quedan con el color incorrecto |
| `removeDropdownItem()` para quitar del dropdown post-init | `setTagList()` no puede usarse post-init (viola la regla anterior) |
| `availableTagNames` se mantiene en sync con el adapter | Estado diverge: se muestra como disponible lo que ya no existe |

---

## Arquitectura interna (para modificar la librería)

```
TagView.java          — FlexboxLayout público. Contiene EditText + RecyclerView + textViewAdd.
                        mAdapter es privado; exponerlo requiere métodos nuevos en TagView.
TagViewAdapter.java   — RecyclerView.Adapter + Filterable. Dos listas:
                          mTagItemList  (filtrada, visible)
                          mBackUpList   (completa, fuente del filtro de texto)
                        Ambas deben estar en sync. removeDropdownItem() elimina de las dos.
interfaces/
  TagItemListener       — chip añadido/eliminado (para el consumidor)
  TagLongClickListener  — long-press en item del dropdown (para el consumidor)
  TagClickListener      — click en dropdown (implementado internamente por TagView, no exponer)
model/TagModel          — tagText: String, isFromList: Boolean
```

El `TextWatcher` del `EditText` filtra el dropdown llamando `mAdapter.getFilter().filter(texto)` en cada keystroke, contra `mBackUpList`.

---

## Colores — defaults de la librería

| Modo  | `tag_bg` |
|-------|----------|
| Light | `#52a78b` (teal medio) — `values/colors.xml` |
| Dark  | `#5BBDA0` (teal brillante) — `values-night/colors.xml` |

Android resuelve el recurso correcto automáticamente. El consumidor puede sobreescribir con `setTagBackgroundColor()` / `setTagTextColor()` (ver paso 2 arriba).
