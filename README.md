# ODISSEY DUNGUEONS

Plugin profesional, completo y altamente optimizado de Mazmorras (Dungeons) e Instancias Privadas para Minecraft (Paper / Purpur 1.20.4+ / Java 21) desarrollado para **OdisseyMC**.

---

## 1. Características Principales

- **Sistema de Instancias Privadas Aisladas:** Cada grupo o jugador accede a su propia copia de la mazmorra. Los grupos nunca se cruzan ni interfieren entre sí. Se realiza clonación efímera de mundos y descarga segura con restauración de bloques y limpieza total de memoria al finalizar.
- **Sin Drops Ni Pérdida Ambiental:** Los enemigos de las mazmorras no sueltan absolutamente ningún objeto ni experiencia al morir. Las explosiones de creepers dañan a los jugadores pero no destruyen bloques, cofres ni mecanismos del mapa.
- **Herramientas de Edición en el Juego (Setup Wands):**
  - Vara de Blaze para spawns de mobs normales (`/dungeons gui` -> Configuración -> Vara de Mobs).
  - Vara de Blaze dedicada para spawn del Boss Final.
  - Ambas identificadas con PersistentDataContainer (PDC) para evitar exploits de jugadores.
  - Visualizador de partículas en tiempo real para administradores.
- **Sistema de Oleadas y Jefes Finales:** Múltiples oleadas configurables por tiempo y eliminación de enemigos con BossBar interactiva y alertas de combate.
- **Tres Modos de Dificultad Dinámicos:**
  - `NORMAL`: Multiplicadores estándar (1.0x).
  - `DIFÍCIL`: Aumento de vida (+50%), daño (+30%) y cantidad de enemigos.
  - `PESADILLA`: Desafío extremo (+120% HP, +80% Daño, recompensas exclusivas legendarias).
- **Sistema de Cooldown Persistente:** Mantiene el tiempo restante tras apagados o reinicios del servidor.
- **Anuncios Globales Automáticos:** Avisos programados (10 min, 5 min, 1 min y disponibilidad) sin duplicación ni spam.
- **Recompensas Individuales Antirrobo:** Soporte para dinero (Vault), XP, comandos de consola, ítems vanilla y objetos personalizados entregados directamente a cada participante según su probabilidad porcentual configurada.
- **Integraciones Opcionales Seguras (Soft-Dependencies):** Si no están instaladas, el plugin funciona perfectamente sin errores.

---

## 2. Tecnologías y Dependencias Opcionales

| Plugin / API | Propósito en OdisseyDungueons |
| :--- | :--- |
| **Vault** | Entrega de dinero y economía en recompensas. |
| **PlaceholderAPI** | Variables para TAB, Scoreboard y hologramas (`%odisseydungueons_<nombre>_*%`). |
| **MythicMobs (v4 / v5)** | Generación de mobs y jefes con habilidades avanzadas. |
| **ModelEngine / BetterModel** | Asignación de modelos 3D personalizados a bosses y enemigos. |
| **ItemsAdder / Nexo / ExecutableItems** | Recompensas con ítems y armas personalizadas. |
| **WorldGuard / WorldEdit** | Verificación de regiones seguras. |
| **SlimeWorldManager / ASWM** | Instancias efímeras de mundos ultra-rápidas en memoria RAM. |

---

## 3. Comandos y Permisos

### Permisos
- `odisseydungueons.admin`: Acceso completo a crear, borrar, editar, probar y recargar mazmorras (por defecto: Operador).
- `odisseydungueons.player`: Acceso a ver mazmorras en la interfaz gráfica, unirse y salir (por defecto: todos los jugadores).

### Comandos
| Comando | Permiso | Descripción |
| :--- | :--- | :--- |
| `/dungeons gui` | `odisseydungueons.player` | Abre la interfaz gráfica interactiva con todas las mazmorras. |
| `/dungeons join <nombre>` | `odisseydungueons.player` | Entra a la mazmorra indicada en una instancia privada. |
| `/dungeons leave` | `odisseydungueons.player` | Abandona la mazmorra actual de forma segura. |
| `/dungeons info <nombre>` | `odisseydungueons.player` | Muestra estadísticas y estado de la mazmorra. |
| `/dungeons create <nombre>` | `odisseydungueons.admin` | Crea una nueva mazmorra y su archivo YAML individual. |
| `/dungeons delete <nombre>` | `odisseydungueons.admin` | Elimina una mazmorra y borra su archivo de configuración. |
| `/dungeons setspawn <nombre>` | `odisseydungueons.admin` | Fija el punto de inicio de los jugadores en la ubicación actual. |
| `/dungeons tp <nombre>` | `odisseydungueons.admin` | Teletranspórtate a la ubicación y mundo configurados de la mazmorra. |
| `/dungeons start <nombre>` | `odisseydungueons.admin` | Fuerza el inicio de una mazmorra para el grupo. |
| `/dungeons stop <nombre>` | `odisseydungueons.admin` | Detiene y limpia una mazmorra activa de inmediato. |
| `/dungeons test <nombre>` | `odisseydungueons.admin` | Entra a una instancia de prueba (sin cooldown ni recompensas). |
| `/dungeons reload` | `odisseydungueons.admin` | Recarga todas las configuraciones, mensajes y mazmorras. |
| `/dungeons help` | Todos | Muestra el menú de ayuda contextual. |

---

## 4. Placeholders de PlaceholderAPI

OdisseyDungueons registra el identificador `%odisseydungueons_<nombre>_<propiedad>%`:

- `%odisseydungueons_<nombre>_time%`: Tiempo restante legible (ej. `01h 25m 30s`).
- `%odisseydungueons_<nombre>_minutes%`: Minutos restantes de cooldown.
- `%odisseydungueons_<nombre>_seconds%`: Segundos restantes de cooldown.
- `%odisseydungueons_<nombre>_status%`: Estado actual (`Disponible`, `En Cooldown`, `En curso`, `Desactivada`).
- `%odisseydungueons_<nombre>_players%`: Jugadores activos dentro de la mazmorra.
- `%odisseydungueons_<nombre>_maxplayers%`: Capacidad máxima de jugadores.
- `%odisseydungueons_<nombre>_boss%`: Nombre o vida actual del jefe final.
- `%odisseydungueons_<nombre>_wave%`: Número de oleada actual.
- `%odisseydungueons_<nombre>_waves_total%`: Cantidad total de oleadas.
- `%odisseydungueons_<nombre>_enemies%`: Enemigos restantes por derrotar en la oleada.
- `%odisseydungueons_<nombre>_difficulty%`: Dificultad seleccionada (`NORMAL`, `DIFÍCIL`, `PESADILLA`).

---

## 5. Estructura de Archivos

```
plugins/OdisseyDungueons/
├── config.yml                # Configuración general y ajustes de limpieza
├── messages.yml              # Centralización de todos los mensajes con prefijo editable
├── dungeons.yml              # Registro central de mazmorras
├── guis/
│   ├── main-menu.yml         # Menú interactivo principal
│   ├── dungeon-menu.yml      # Menú administrativo de edición
│   ├── mobs-menu.yml         # Configuración visual de enemigos
│   ├── boss-menu.yml         # Configuración del Boss final
│   ├── rewards-menu.yml      # Configuración de recompensas
│   ├── difficulty-menu.yml   # Selección y multiplicadores de dificultad
│   └── settings-menu.yml     # Ajustes avanzados
├── dungeons/
│   ├── prueba.yml            # Plantilla completa de ejemplo funcional
│   └── ejemplo.yml           # Plantilla avanzada con MythicMobs y salas
├── instances/                # Almacén de instancias privadas efímeras
└── backups/                  # Copias de seguridad automáticas
```

---

## 6. Instalación y Puesta en Marcha

1. Asegúrate de tener instalado Java 21 y un servidor Paper o Purpur 1.20.4+.
2. Coloca `OdisseyDungueons.jar` (ubicado en `target/OdisseyDungueons.jar`) dentro de la carpeta `plugins/` de tu servidor.
3. Si deseas economía y marcadores, añade `Vault` y `PlaceholderAPI`.
4. Inicia el servidor. Se generará automáticamente la estructura completa de carpetas y archivos YAML por defecto.
5. Usa `/dungeons create <nombre>` para crear tu primera mazmorra o utiliza las incluidas (`Prueba` y `Ejemplo`).
6. Configura el punto de spawn con `/dungeons setspawn <nombre>`.
7. Abre el menú `/dungeons gui` para recibir las varas de blaze y marcar los puntos de aparición de los mobs y del jefe final.

---

## 7. Informe de Pruebas Automatizadas

Se ejecutó la suite de pruebas unitarias con JUnit 5 validando:
- Creación, inicialización y persistencia de modelos de mazmorra.
- Cálculo de cooldowns y resistencia a reinicios de servidor.
- Serialización/deserialización de coordenadas, rotaciones y regiones cuboides.
- Aislamiento e independencia de jugadores en instancias privadas simultáneas.
- Control y decremento de mobs vivos en oleadas.
- Triggers de salas y progreso de un solo uso.
- Distribución y probabilidades de recompensas por dificultad.
- Regla de combate estricta: eliminación total de drops de mobs, supresión de XP y neutralización del daño a bloques por creepers.
