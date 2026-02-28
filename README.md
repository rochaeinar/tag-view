# MaterialTagView

Widget de entrada de tags para Android. El usuario escribe texto, presiona un separador para crear un chip, y puede elegir de una lista de sugerencias predefinida.

---

## Setup

Copia `materialtagview-debug.aar` en `app/libs/` y agrega en `build.gradle`:

```groovy
dependencies {
    implementation files('libs/materialtagview-debug.aar')
    implementation 'com.google.android.flexbox:flexbox:3.0.0'
}
```

---

## Layout XML

```xml
<com.skyhope.materialtagview.TagView
    android:id="@+id/tag_view"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:tag_separator="SPACE_SEPARATOR"
    app:tag_text_color="@color/white"
    app:tag_background_color="@color/teal_200" />
```

### Atributos XML disponibles

| Atributo              | Valores posibles                                                                                   | Default              |
|-----------------------|----------------------------------------------------------------------------------------------------|----------------------|
| `tag_separator`       | `COMMA_SEPARATOR`, `SPACE_SEPARATOR`, `PLUS_SEPARATOR`, `MINUS_SEPARATOR`, `AT_SEPARATOR`, `HASH_SEPARATOR` | `COMMA_SEPARATOR` |
| `tag_text_color`      | color                                                                                              | blanco               |
| `tag_background_color`| color                                                                                              | teal                 |
| `tag_limit`           | entero                                                                                             | **1**                |
| `close_icon`          | referencia a drawable                                                                              | X integrada          |
| `limit_error_text`    | string                                                                                             | "You reach maximum tags" |

> **Atención:** `tag_limit` tiene default **1** desde XML. Si no querés límite, siempre llamá `addTagLimit(Integer.MAX_VALUE)` en código.

---

## Uso básico

```java
TagView tagView = findViewById(R.id.tag_view);

tagView.setHint("Agregar tag...");
tagView.addTagSeparator(TagSeparator.SPACE_SEPARATOR);
tagView.addTagLimit(Integer.MAX_VALUE);

List<String> sugerencias = Arrays.asList("Android", "Kotlin", "Java", "iOS");
tagView.setTagList(new ArrayList<>(sugerencias));
```

---

## Pre-seleccionar tags (chips iniciales)

Llamá `addTag()` por cada tag que ya debe aparecer como chip **antes** de llamar `setTagList()`.
Eliminá esos tags del pool de sugerencias para que no aparezcan duplicados en el dropdown.

```java
List<String> todos = new ArrayList<>(Arrays.asList("Android", "Kotlin", "Java", "iOS"));
List<String> seleccionados = Arrays.asList("Kotlin", "Java");

// 1. Agregar chips primero
for (String nombre : seleccionados) {
    tagView.addTag(nombre, true); // true = viene de la lista
    todos.remove(nombre);
}

// 2. Cargar el resto como sugerencias
tagView.setTagList(new ArrayList<>(todos));
```

---

## Escuchar cuando se agrega o elimina un chip

```java
tagView.initTagListener(new TagItemListener() {
    @Override
    public void onGetAddedItem(TagModel tagModel) {
        String tag = tagModel.getTagText();
        boolean deListado = tagModel.isFromList();
        // el chip fue agregado
    }

    @Override
    public void onGetRemovedItem(TagModel tagModel) {
        String tag = tagModel.getTagText();
        // el chip fue eliminado (usuario tocó la X)
        // si isFromList() == true, la librería lo devuelve al dropdown automáticamente
    }
});
```

---

## Leer los tags seleccionados

```java
List<TagModel> seleccionados = tagView.getSelectedTags();

for (TagModel model : seleccionados) {
    String texto    = model.getTagText();   // el string del tag
    boolean deLista = model.isFromList();   // true = provino del listado de sugerencias
}
```

---

## Long-press en una sugerencia para eliminarla

```java
tagView.initTagLongClickListener(new TagLongClickListener() {
    @Override
    public void onTagLongClick(int position, String tagText) {
        new AlertDialog.Builder(context)
            .setTitle("Eliminar tag")
            .setMessage("¿Eliminar \"" + tagText + "\" de forma permanente?")
            .setPositiveButton("Eliminar", (dialog, which) -> {
                tagView.removeDropdownItem(tagText); // lo quita del dropdown
                eliminarTagDeBaseDeDatos(tagText);   // tu lógica de persistencia
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }
});
```

---

## Eliminar una sugerencia por código

```java
// Quita el ítem del dropdown sin reinicializar la vista
tagView.removeDropdownItem("Kotlin");
```

---

## Personalizar apariencia

```java
tagView.setTagBackgroundColor("#1565C0");        // color hex
tagView.setTagBackgroundColor(Color.BLUE);       // o int color

tagView.setTagTextColor("#FFFFFF");
tagView.setTagTextColor(Color.WHITE);

tagView.setCrossButton(
    ContextCompat.getDrawable(this, R.drawable.ic_close)
);

tagView.setMaximumTagLimitMessage("Límite de tags alcanzado");
```

---

## Ejemplo completo (Activity)

```java
public class TagActivity extends AppCompatActivity {

    private TagView tagView;
    private List<String> availableTags = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tag);

        tagView = findViewById(R.id.tag_view);

        // Configuración
        tagView.setHint("Agregar tag...");
        tagView.addTagSeparator(TagSeparator.SPACE_SEPARATOR);
        tagView.addTagLimit(Integer.MAX_VALUE);

        // Callbacks de chip
        tagView.initTagListener(new TagItemListener() {
            @Override
            public void onGetAddedItem(TagModel tagModel) {
                if (tagModel.isFromList()) {
                    availableTags.remove(tagModel.getTagText());
                }
            }

            @Override
            public void onGetRemovedItem(TagModel tagModel) {
                if (tagModel.isFromList()) {
                    availableTags.add(tagModel.getTagText());
                }
            }
        });

        // Long-press en sugerencia → confirmar borrado
        tagView.initTagLongClickListener((position, tagText) -> {
            new AlertDialog.Builder(this)
                .setTitle("Eliminar tag")
                .setMessage("¿Eliminar \"" + tagText + "\" de todas las notas?")
                .setPositiveButton("Eliminar", (d, w) -> {
                    availableTags.remove(tagText);
                    tagView.removeDropdownItem(tagText);
                    eliminarTagDeDB(tagText);
                })
                .setNegativeButton("Cancelar", null)
                .show();
        });

        cargarTags();
    }

    private void cargarTags() {
        List<String> todos      = obtenerTagsDeBD();      // ej: ["Android","Kotlin","Java"]
        List<String> asignados  = obtenerTagsDeLaNota();  // ej: ["Kotlin"]

        // Agregar chips pre-seleccionados
        for (String nombre : asignados) {
            tagView.addTag(nombre, true);
            todos.remove(nombre);
        }

        // Cargar sugerencias restantes
        availableTags.addAll(todos);
        tagView.setTagList(new ArrayList<>(availableTags));
    }

    private void guardar() {
        List<TagModel> tags = tagView.getSelectedTags();
        for (TagModel model : tags) {
            guardarTagParaNota(model.getTagText());
        }
    }
}
```

---

## Referencia de API

| Método | Descripción |
|---|---|
| `setHint(String)` | Texto de hint en el campo de entrada |
| `addTagSeparator(TagSeparator)` | Carácter que dispara la creación de un chip |
| `addTagLimit(int)` | Cantidad máxima de chips (`Integer.MAX_VALUE` = sin límite) |
| `setTagList(List<String>)` | Carga el dropdown de sugerencias (llamar una sola vez, después de `addTag`) |
| `addTag(String, boolean)` | Agrega un chip por código; `true` si proviene de la lista |
| `removeDropdownItem(String)` | Elimina un ítem del dropdown sin reinicializar la vista |
| `getSelectedTags()` | Devuelve `List<TagModel>` con los chips actuales |
| `initTagListener(TagItemListener)` | Callback para eventos de agregar / eliminar chip |
| `initTagLongClickListener(TagLongClickListener)` | Callback para long-press en un ítem del dropdown |
| `setTagBackgroundColor(int\|String)` | Color de fondo de los chips |
| `setTagTextColor(int\|String)` | Color de texto de los chips |
| `setCrossButton(Drawable\|Bitmap)` | Ícono personalizado del botón eliminar en cada chip |
| `setMaximumTagLimitMessage(String)` | Mensaje Toast cuando se alcanza el límite |

---

## Interfaces

**TagItemListener** — chip agregado o eliminado:
```java
void onGetAddedItem(TagModel tagModel);
void onGetRemovedItem(TagModel tagModel);
```

**TagLongClickListener** — long-press en sugerencia del dropdown:
```java
void onTagLongClick(int position, String tagText);
```

**TagModel**:
```java
String getTagText()    // el string del tag
boolean isFromList()   // true = provino del dropdown de sugerencias
```
