# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build

Requires `JAVA_HOME` apuntando al JDK de Android Studio:

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
./gradlew :materialtagview:assembleDebug
```

El AAR resultante queda en:
```
materialtagview/build/outputs/aar/materialtagview-debug.aar
```

El módulo `:app` está excluido de `settings.gradle` (era la app de demo; no compila con AGP moderno sin migración adicional). Solo se construye `:materialtagview`.

## Arquitectura

La librería es un único módulo (`materialtagview/`) con tres capas:

### 1. `TagView.java` — Vista principal
Extiende `FlexboxLayout`. Es el punto de entrada público: contiene el `EditText` embebido, el `RecyclerView` del dropdown y los chips. Mantiene:
- `mTagList` — chips activos (`List<TagModel>`)
- `mTagItemList` — sugerencias del dropdown (sincronizado con el adapter)
- `mAdapter` — instancia privada de `TagViewAdapter`

**Invariante crítico:** `setTagList()` llama a `addRecyclerView()`, que agrega `EditText + RecyclerView + TextView` como hijos del FlexboxLayout. **Llamarlo más de una vez duplica esos hijos.** Para modificar el dropdown después de la inicialización usar `removeDropdownItem()`, `mAdapter.addItem()` o `mAdapter.removeTagItem()` — nunca volver a llamar `setTagList()`.

### 2. `TagViewAdapter.java` — Adapter del dropdown
RecyclerView adapter + Filterable. Mantiene dos listas:
- `mTagItemList` — lista filtrada actualmente visible
- `mBackUpList` — lista completa sin filtrar (fuente de verdad para el filtro de texto)

Ambas listas deben mantenerse en sincronía. `removeTagItem()` y `removeDropdownItem()` eliminan de las dos.

### 3. Interfaces / modelo
- `TagItemListener` — callbacks de chip añadido/eliminado (para el consumidor)
- `TagClickListener` — click en item del dropdown (implementado internamente por `TagView`)
- `TagLongClickListener` — long-press en item del dropdown (para el consumidor)
- `TagModel` — POJO con `tagText` y `isFromList`

## Flujo de inicialización correcto

El orden importa. El consumidor **siempre** debe seguir esta secuencia:

1. Configurar (`setHint`, `addTagSeparator`, `addTagLimit`)
2. Registrar listeners (`initTagListener`, `initTagLongClickListener`)
3. `addTag(name, true)` + eliminar del pool → por cada tag pre-seleccionado
4. `setTagList(pool)` — única llamada, con el pool ya sin los pre-seleccionados

## Filtro de texto

El `TextWatcher` del `EditText` llama a `mAdapter.getFilter().filter(texto)` en cada keystroke. El filtro trabaja contra `mBackUpList`. Si `mTagItemList` y `mBackUpList` se dessincronizan (p. ej. por llamar `addItems()` incorrectamente), el filtro muestra resultados incorrectos.

## Añadir la librería a otro proyecto

```groovy
implementation files('libs/materialtagview-debug.aar')
implementation 'com.google.android.flexbox:flexbox:3.0.0'  // dependencia transitiva requerida
```

`TagView` extiende `FlexboxLayout`; sin la dependencia de flexbox el proyecto consumidor no compila (falta el atributo `layout_maxWidth` en AAPT).
