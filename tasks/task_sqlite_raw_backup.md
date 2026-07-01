# Задача: бэкап копированием файла .db вместо JSON для REPLACE

> Продолжение [task_settings_to_sqlite.md]. Там уже сделано: session_names —
> единственный источник правды для акцентов/toggles, бэкап настроек упрощён
> до generic map поверх SharedPreferences. Здесь — следующий шаг: перенести
> сами настройки в SQLite (таблица `app_settings`), чтобы REPLACE-бэкап был
> копией **одного** файла `.db` — без JSON и без отдельного xml-файла.

## Решения (согласованы с пользователем)

1. **REPLACE** — копирование одного файла `.db` целиком, без JSON.
   Настройки (`BACKUP_KEYS`) переезжают из SharedPreferences в новую Room-таблицу
   `app_settings` — значит они окажутся внутри того же файла БД, и отдельный
   xml/zip не нужен. Требует **перезапуска приложения** после восстановления
   (Room кэширует открытое соединение в памяти процесса — подмена файла на
   диске незаметна для уже открытого соединения).
2. **MERGE** — остаётся на текущем JSON-пути без изменений (`restoreMerge`,
   как сейчас).
3. Создание бэкапов:
   - `BackupManager.createBackup(folderUri)` (авто-бэкап `BackupWorker` + кнопка
     «Создать бэкап сейчас») → копия файла `.db` (см. ниже).
   - `BackupManager.generateBackupData()` («Save Backup To...» для облака) →
     **остаётся JSON**, без изменений — единственный источник свежих JSON-файлов
     для будущего MERGE.

## Почему таблица в Room, а не xml/zip

Изначально план был: скопировать `.db` + `.xml` (SharedPreferences) в zip.
Но xml и .db — разные форматы, простого "положить один файл внутрь другого"
не бывает. Чтобы бэкап был **одним** файлом без контейнера, настройки должны
физически лежать в самой SQLite-базе — то есть переехать в Room. Это тот же
`app_settings`, который в task_settings_to_sqlite.md был отложен как излишний
для фикса бага с акцентами — здесь он оправдан другой целью (один файл вместо
zip) и по меньшему скоупу, чем тогда:

- Переносятся только ~15 ключей из текущего `PreferencesManager.BACKUP_KEYS`
  (то, что уже входит в бэкап). Рантайм-состояние секундомера, флаги миграций,
  `currentName`/`currentNotes`, `backupFolderUri` и т.п. остаются в
  SharedPreferences как есть — они и не входили в бэкап.
- Публичный API `PreferencesManager` (синхронные `val` с `get()`/`set()`) не
  меняется — под капотом место чтения/записи переключается с
  `SharedPreferences` на Room, но все вызывающие места (StopwatchViewModel и
  т.д.) не трогаются.

## Реализация

### 1. Новые файлы
- `data/database/entity/AppSettingEntity.kt`:
  ```kotlin
  @Entity(tableName = "app_settings")
  data class AppSettingEntity(
      @PrimaryKey val key: String,
      val value: String?
  )
  ```
- `data/database/dao/AppSettingsDao.kt`:
  ```kotlin
  @Dao
  interface AppSettingsDao {
      @Query("SELECT * FROM app_settings")
      suspend fun getAll(): List<AppSettingEntity>

      @Insert(onConflict = OnConflictStrategy.REPLACE)
      suspend fun upsert(setting: AppSettingEntity)
  }
  ```

### 2. `AppDatabase.kt`
- `entities = [..., AppSettingEntity::class]`, `version = 4 -> 5`.
- `MIGRATION_4_5`: `CREATE TABLE IF NOT EXISTS app_settings (key TEXT PRIMARY KEY NOT NULL, value TEXT)`.
- `abstract fun appSettingsDao(): AppSettingsDao`.
- Вынести имя файла БД в constant: `const val DB_NAME = "laplog_database"`,
  использовать в `Room.databaseBuilder(..., DB_NAME)`.
- Добавить `fun closeDatabase()`: `INSTANCE?.close(); INSTANCE = null` — нужно
  для restore (закрыть перед подменой файла).

### 3. `PreferencesManager.kt` — переключить ~15 свойств на Room

```kotlin
private val settingsDao = AppDatabase.getDatabase(context).appSettingsDao()
private val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

// Разовое блокирующее чтение при создании PreferencesManager — таблица крошечная
// (~15 строк), стоимость сопоставима с тем, что и так делает сама SharedPreferences
// при первом обращении к файлу (ленивая загрузка+парсинг XML на вызывающем потоке).
// runBlocking здесь не требует allowMainThreadQueries(), т.к. getAll() — suspend
// и физически выполняется в background-дispatcher'е Room, а не на вызывающем потоке.
private val settingsCache: MutableMap<String, String?> = ConcurrentHashMap(
    runBlocking { settingsDao.getAll().associate { it.key to it.value } }
)

private fun writeSetting(key: String, value: String?) {
    settingsCache[key] = value
    settingsScope.launch {
        try { settingsDao.upsert(AppSettingEntity(key, value)) } catch (_: Exception) {}
    }
}

var showMilliseconds: Boolean
    get() = settingsCache[KEY_SHOW_MILLISECONDS]?.toBooleanStrictOrNull() ?: true
    set(value) = writeSetting(KEY_SHOW_MILLISECONDS, value.toString())
// ... аналогично для остальных ~14 свойств из BACKUP_KEYS
```

Остальные свойства (`usedNames`, `currentName`, `currentNotes`, стопвотч-стейт,
`permissionsRequested`, `isFirstLaunch`, флаги миграций/сидов, legacy per-name
карты) — без изменений, продолжают жить в `prefs` (SharedPreferences).

`exportSettingsMap()`/`importSettingsMap()` (нужны для JSON-пути — «Save Backup
To...» и restore старых `.json`-бэкапов) упрощаются: теперь это просто снимок/
запись всего `settingsCache`, без отдельного allowlist'а `BACKUP_KEYS` — раз
таблица `app_settings` по построению содержит только то, что нужно бэкапить.

### 4. `BackupManager.kt` — REPLACE как копия одного файла

- Конструктор: добавить `private val database: AppDatabase` (для checkpoint и
  close/reopen).
- `checkpointDatabase()`: `database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)", null).close()`.
- `createBackup(folderUri)`: checkpoint → скопировать
  `context.getDatabasePath(AppDatabase.DB_NAME)` **напрямую** как файл бэкапа
  (без zip, без xml — настройки уже внутри). Расширение — например `.laplogdb`,
  mime `application/octet-stream`.
- `generateBackupData()` — не трогать, остаётся JSON (использует
  `preferencesManager.exportSettingsMap()`, как сейчас).
- `restoreBackup(fileUri, mode, onProgress)`:
  - Формат по расширению: `.laplogdb` → raw, `.json` → текущий код без изменений.
  - raw + `MERGE` → `Result.failure(...)` с понятным сообщением.
  - raw + `REPLACE` → `restoreRaw(fileUri)`:
    1. Скопировать файл из `fileUri` во временный файл в `context.cacheDir`.
    2. `database.closeDatabase()`.
    3. Удалить `-wal`/`-shm` рядом с текущим файлом БД, скопировать временный
       файл поверх `context.getDatabasePath(AppDatabase.DB_NAME)`.
    4. Удалить временный файл.
    5. Вернуть `RestoreResult(requiresRestart = true, ...)` — счётчики
       sessions/laps без переоткрытия БД недоступны (можно поставить `-1`).
- `RestoreResult`: добавить `val requiresRestart: Boolean = false`.
- `listBackups()`: матчить оба расширения (`.json` и `.laplogdb`) под общим
  префиксом `laplog_backup_`.

### 5. `MainActivity.kt` / `BackupWorker.kt`
Оба места создания `BackupManager(...)` — передать `database` дополнительным
параметром.

### 6. `BackupViewModel.kt` / `BackupScreen.kt`
- Если `result.getOrNull()?.requiresRestart == true` — показать диалог
  «Восстановление завершено. Перезапустите приложение», а не обычную сводку.
- Программный перезапуск процесса не делать (риск с активным foreground
  `StopwatchService`) — просто попросить пользователя закрыть приложение
  вручную.

## Файлы

- `data/database/entity/AppSettingEntity.kt` — создать
- `data/database/dao/AppSettingsDao.kt` — создать
- `data/database/AppDatabase.kt`
- `data/PreferencesManager.kt`
- `data/BackupManager.kt`
- `MainActivity.kt`, `worker/BackupWorker.kt`
- `viewmodel/BackupViewModel.kt`, `ui/BackupScreen.kt`

## Риски

- **Блокирующее чтение при старте** (`runBlocking` в конструкторе
  `PreferencesManager`) — разовая стоимость на холодном старте, таблица
  крошечная; тем не менее это блокирующий вызов, стоит перепроверить на
  реальном устройстве, что нет заметной задержки старта.
- `PRAGMA wal_checkpoint(TRUNCATE)` не даёт 100% атомарности при параллельной
  записи — на практике для раз-в-сутки бэкапа при одном процессе риск
  минимален, но не нулевой (публичного `sqlite3_backup_init` в Android нет).
- Даунгрейд APK: бэкап с новой схемой, восстановленный на более старой версии
  приложения — не откроется (Room-миграции однонаправленные). JSON-путь
  (MERGE) к этому терпимее.
- Обязательный перезапуск после REPLACE — нужно чётко показать в UI.
- Старые `.json`-бэкапы должны продолжать работать (REPLACE и MERGE) без
  регрессий — код для них не меняется.
- Миграция `MIGRATION_4_5` создаёт пустую `app_settings`; при первом запуске
  после обновления `PreferencesManager` должен засеять её текущими значениями
  из старых SharedPreferences-ключей (аналогично уже существующему
  `migrateNameSettingsFromPrefsIfNeeded()` для per-name настроек) — иначе после
  обновления приложения значения по умолчанию перезапишут пользовательские
  настройки.

## Проверка

- Создать raw-бэкап (`.laplogdb`), убедиться что это валидный SQLite-файл
  (открывается `sqlite3 file.laplogdb ".tables"`) и содержит `app_settings` с
  актуальными значениями.
- Восстановить raw-бэкап (REPLACE), перезапустить приложение вручную —
  сессии/акценты/настройки должны совпадать с бэкапом.
- Восстановить raw-бэкап с `mode = MERGE` — понятная ошибка, не тихий сбой.
- Восстановить старый `.json`-бэкап (REPLACE и MERGE) — как раньше, без
  регрессий.
- Обновление с версии до этой задачи: настройки должны сохраниться (проверить
  миграцию из SharedPreferences в `app_settings`), не откатиться к DEFAULT.
- `BackupWorker` создаёт `.laplogdb`, не `.json`.

## Статус

**Реализовано**, ждёт сборки через GitHub Actions и проверки на устройстве
(локальная сборка недоступна). Дополнительно по пути найдены и исправлены ещё
два пробела с `sessionNameDao`:

- `BackupViewModelFactory`/`BackupScreen` создавали свой собственный
  `BackupViewModel`/`BackupManager` (независимо от `MainActivity.backupViewModel`)
  и не передавали `sessionNameDao`/`database` — то есть кнопка «Создать бэкап
  сейчас» на экране бэкапов тоже никогда не включала `session_names`.
  Исправлено во всех местах создания (`MainActivity.kt`, `BackupScreen.kt`,
  `BackupViewModelFactory.kt`, `BackupViewModel.kt`).

Реализованные пункты:
- [x] `AppSettingEntity`/`AppSettingsDao`, `MIGRATION_4_5`, `AppDatabase.DB_NAME`, `closeDatabase()`
- [x] `PreferencesManager`: ~15 свойств из `BACKUP_KEYS` переведены на Room-кэш,
      сид из старого SharedPreferences при пустой таблице
- [x] `BackupManager.createBackup` — raw-копия `.db` (checkpoint + copy), JSON как fallback
- [x] `BackupManager.restoreBackup` — определение формата по расширению,
      `restoreRaw()` для `.laplogdb` (REPLACE only), явная ошибка на MERGE
- [x] `listBackups`/`generateBackupFileName`/`extractTimestampFromFileName` — оба расширения
- [x] `RestoreResult.requiresRestart` + диалог в `BackupScreen` с текстом
      про ручной перезапуск (EN/RU/ZH)
- [x] Все места создания `BackupManager`/`BackupViewModel` передают `database`

Не проверено (нет локальной сборки):
- [ ] Реальная сборка компилируется без ошибок
- [ ] raw-бэкап создаётся и валиден (`sqlite3 ... ".tables"` показывает `app_settings`)
- [ ] REPLACE-восстановление + ручной перезапуск возвращают верные данные
- [ ] MERGE на `.laplogdb` даёт понятную ошибку, а не краш
- [ ] Старые `.json`-бэкапы всё ещё восстанавливаются (REPLACE и MERGE)
- [ ] Апгрейд с версии до этой задачи не роняет пользовательские настройки
      (миграция `seedFromLegacyPrefs`)
