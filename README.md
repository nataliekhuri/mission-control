# Mission Control

Aplicación de escritorio para que una agencia espacial gestione sus expediciones: misiones, astronautas,
recursos, tripulaciones, incidencias y estadísticas generales.

Desarrollada con **Kotlin** y **Compose Multiplatform (Desktop)**. Toda la información se guarda de forma
persistente en **SQLite** y puede importarse y exportarse en **JSON**.

> Actividad 3 · Programación multimedia y dispositivos móviles (MEDAC)

---

## Tecnologías

| Tecnología | Uso |
|---|---|
| Kotlin 2.4 | Lenguaje |
| Compose Multiplatform 1.12 (Desktop/JVM) + Material 3 | Interfaz gráfica |
| SQLite (`org.xerial:sqlite-jdbc`) | Base de datos persistente |
| kotlinx.serialization (JSON) | Importación y exportación |
| Gradle (Kotlin DSL, version catalog) | Compilación |
| kotlin-test / JUnit | Pruebas de la capa de datos |

---

## Funcionalidades

### Panel de control
Calculado en tiempo real a partir de la base de datos:
- Número total de misiones, misiones activas (en preparación o en curso) y completadas.
- Astronautas disponibles e incidencias registradas.
- Próxima misión planificada con cuenta atrás.
- Distribución de misiones por estado y últimas incidencias.
- **Alertas automáticas**: incidencias críticas en misiones abiertas, misiones sin tripulación,
  astronautas no disponibles asignados, fechas de lanzamiento superadas y recursos agotados o con stock bajo.

### Misiones
- Crear, consultar, editar (el formulario se carga con los datos actuales) y eliminar con confirmación.
- Pantalla de detalle con toda la información, tripulación, recursos e incidencias de la misión.
- **Búsqueda** por nombre, destino o descripción (sin distinguir mayúsculas ni tildes).
- **Filtros combinables** por estado y prioridad.
- **Ordenación** por fecha, nombre, prioridad, estado o duración.
- Estados: `PLANIFICADA`, `PREPARACION`, `EN_CURSO`, `COMPLETADA`, `CANCELADA`.
- Prioridades: `BAJA`, `MEDIA`, `ALTA`, `CRITICA`.

### Astronautas
- CRUD completo con búsqueda (por nombre o especialidad) y filtro por disponibilidad.
- Detalle con estado actual y misiones en las que participa o ha participado (con su rol).
- Especialidades: `PILOTO`, `INGENIERIA`, `MEDICINA`, `CIENCIA`, `COMUNICACIONES`.
- Disponibilidad: `DISPONIBLE`, `EN_MISION`, `NO_DISPONIBLE`.

### Tripulación (misión ↔ astronauta)
- Una misión puede tener varios astronautas y un astronauta puede estar en varias misiones.
- Cada asignación guarda el **rol** del astronauta en la misión.
- Se puede retirar a un astronauta de una misión sin eliminarlo del sistema.

### Recursos e inventario
- Crear, consultar, modificar la cantidad disponible y eliminar recursos.
- Asignar recursos a misiones indicando recurso, misión y cantidad.
- **No se permite asignar más cantidad de la disponible**; el inventario se descuenta en una transacción.
- Al liberar un recurso o eliminar una misión, la cantidad vuelve al inventario.
- Indicadores de recurso agotado y stock bajo.

### Incidencias
- Registrar, editar y eliminar incidencias con misión, fecha y hora, tipo, gravedad y descripción.
- Solo pueden asociarse a misiones existentes (validación + clave foránea).
- Se muestran en el detalle de cada misión y en un apartado general, ordenables por fecha o gravedad.
- La gravedad se distingue por colores (baja, media, alta, crítica).

### Importar / Exportar JSON
- **Exportar**: genera un JSON con la información real de la base de datos (misiones, astronautas,
  recursos, incidencias, tripulaciones y asignaciones de recursos).
- **Importar**: lee, deserializa, valida (formato, campos obligatorios, fechas, valores permitidos,
  ids duplicados y referencias entre registros) e inserta en SQLite **dentro de una transacción**:
  si hay cualquier error no se guarda nada y se informa al usuario.
- Dos modos: *añadir* a los datos existentes o *reemplazar todo* (con confirmación).
- Los ids del archivo son referencias internas: al importar se generan ids nuevos y se conservan
  todas las relaciones.

### Experiencia de usuario
- Navegación lateral entre secciones y pantallas de detalle con botón "Volver".
- Formularios con validación; no se guardan registros incompletos ni datos incorrectos.
- Diálogos de confirmación antes de eliminar.
- Mensajes de éxito y de error.
- Estados vacíos en todas las pantallas (la aplicación funciona con todas las tablas vacías).

---

## Base de datos

La base de datos se crea automáticamente al iniciar la aplicación si no existe, **sin datos**:

```
~/.missioncontrol/missioncontrol.db      (Windows: C:\Users\<usuario>\.missioncontrol\missioncontrol.db)
```

Se guarda fuera de la carpeta del proyecto para que los datos persistan entre ejecuciones y nunca se
entregue una base de datos con datos. La ruta exacta aparece en la pantalla *Importar / Exportar*.
Opcionalmente se puede usar otro archivo con la variable de entorno `MISSIONCONTROL_DB`.

| Tabla | Contenido |
|---|---|
| `MISIONES` | id, nombre, destino, fecha, duracion, estado, prioridad, descripcion |
| `ASTRONAUTAS` | id, nombre, especialidad, experiencia, estado |
| `RECURSOS` | id, nombre, categoria, cantidad (disponible), unidad |
| `INCIDENCIAS` | id, mision_id → MISIONES, fecha, tipo, gravedad, descripcion |
| `MISION_ASTRONAUTA` | mision_id → MISIONES, astronauta_id → ASTRONAUTAS, rol |
| `MISION_RECURSO` | mision_id → MISIONES, recurso_id → RECURSOS, cantidad |

- Claves primarias, claves foráneas (`PRAGMA foreign_keys = ON`) y restricciones `CHECK` para los valores enumerados.
- Al eliminar una misión se eliminan en cascada sus incidencias y asignaciones.
- Un recurso asignado a misiones no puede eliminarse hasta liberarlo.
- Las asignaciones de recursos y las importaciones JSON usan transacciones.

---

## Datos de demostración

El archivo [`mission-control-sample.json`](mission-control-sample.json) contiene datos realistas
(9 misiones, 10 astronautas, 10 recursos, 10 incidencias, tripulaciones y asignaciones de recursos).

**No se carga automáticamente.** Para usarlo: *Importar / Exportar → Importar y añadir…* y seleccionar el archivo.

---

## Requisitos e instalación

- **IntelliJ IDEA** (recomendado) o JDK 17+ para la línea de comandos.
- Conexión a Internet la primera vez para que Gradle descargue las dependencias.

### Ejecutar desde IntelliJ IDEA
1. *File → Open…* y seleccionar la carpeta del proyecto (la que contiene `settings.gradle.kts`).
2. Esperar a que termine la sincronización de Gradle.
3. Abrir `desktopApp/src/main/kotlin/com/cineflow/missioncontrol/main.kt` y pulsar ▶ junto a `main`,
   o ejecutar la tarea Gradle `desktopApp > Tasks > compose desktop > run`.

### Ejecutar desde la línea de comandos
```bash
./gradlew :desktopApp:run          # macOS / Linux
gradlew.bat :desktopApp:run        # Windows
```

### Pruebas y compilación
```bash
./gradlew build                    # compila y ejecuta todas las pruebas
./gradlew :shared:jvmTest          # solo las pruebas de la capa de datos
```

Las pruebas usan una base de datos temporal (nunca la real) y verifican: creación de tablas vacías,
CRUD, búsqueda y filtros, control de inventario, tripulaciones, incidencias, persistencia tras reabrir
la base de datos e importación/exportación JSON (incluidos archivos inválidos).

---

## Estructura del proyecto

```
desktopApp/src/main/kotlin/.../main.kt     Punto de entrada: abre la base de datos y la ventana
shared/src/jvmMain/kotlin/.../
├── App.kt                                 Estructura principal: barra lateral, navegación y mensajes
├── AppState.kt                            Estado de la UI, navegación y refresco de datos
├── data/
│   ├── Models.kt                          Modelos, enumerados y utilidades de fechas/búsqueda
│   ├── Database.kt                        Conexión SQLite y creación del esquema
│   ├── Repository.kt                      Consultas SQL, reglas de negocio y validación
│   └── JsonService.kt                     Exportación e importación JSON
└── ui/                                    Pantallas (panel, misiones, astronautas, recursos,
                                           incidencias, importar/exportar), formularios y componentes
shared/src/jvmTest/.../DataLayerTest.kt    Pruebas de la capa de datos
mission-control-sample.json                Datos de demostración
```
