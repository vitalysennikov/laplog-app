# Задача: ускорить восстановление из бэкапа

## Проблема

`BackupManager.restoreReplace` и `restoreMerge` работают медленно из-за двух причин:

### 1. Авто-перевод во время восстановления (главная причина)
Для каждой сессии с именем/заметкой без готовых переводов вызываются:
- `autoTranslateName(sessionId, finalName)` — 3 HTTP-запроса к `api.mymemory.translated.net`
- `autoTranslateNotes(sessionId, finalNotes)` — ещё 3 HTTP-запроса

При 50 сессиях с именами: ~300 сетевых запросов → несколько минут ожидания.

### 2. Каждая вставка в БД — отдельная транзакция
`sessionDao.insertSession(session)` и `sessionDao.insertLaps(laps)` вызываются в цикле без общей транзакции. При 100 сессиях — 200+ отдельных flush на диск.

## Файлы для изменения

- `app/src/main/java/com/laplog/app/data/BackupManager.kt` — основной файл
- `app/src/main/java/com/laplog/app/viewmodel/BackupViewModel.kt` — передать `AppDatabase` вместо `SessionDao`
- `app/src/main/java/com/laplog/app/MainActivity.kt` — два места создания `BackupManager` (строки ~166, ~374)

## Что нужно сделать

### BackupManager.kt

1. Заменить параметр `sessionDao: SessionDao` на `database: AppDatabase`
2. Добавить `private val sessionDao = database.sessionDao()`
3. Добавить импорт `androidx.room.withTransaction`
4. Убрать вызовы `autoTranslateName` и `autoTranslateNotes` из `restoreReplace` и `restoreMerge`
5. Обернуть все DB-вставки в `database.withTransaction { ... }`:

```kotlin
private suspend fun restoreReplace(backupData: BackupData) {
    database.withTransaction {
        sessionDao.deleteAllSessions()
        backupData.sessions.forEach { backupSession ->
            val finalName = backupSession.name ?: backupSession.comment
            val session = SessionEntity(
                id = 0,
                startTime = backupSession.startTime,
                endTime = backupSession.endTime,
                totalDuration = backupSession.totalDuration,
                name = finalName,
                notes = backupSession.notes,
                name_en = backupSession.name_en,
                name_ru = backupSession.name_ru,
                name_zh = backupSession.name_zh,
                notes_en = backupSession.notes_en,
                notes_ru = backupSession.notes_ru,
                notes_zh = backupSession.notes_zh
            )
            val sessionId = sessionDao.insertSession(session)
            val laps = backupSession.laps.map { backupLap ->
                LapEntity(
                    sessionId = sessionId,
                    lapNumber = backupLap.lapNumber,
                    totalTime = backupLap.totalTime,
                    lapDuration = backupLap.lapDuration
                )
            }
            if (laps.isNotEmpty()) sessionDao.insertLaps(laps)
        }
    }
    backupData.settings?.let { restoreSettings(it) }
}
```

Аналогично для `restoreMerge` (без `sessionDao.deleteAllSessions()`).

6. Функции `autoTranslateName` и `autoTranslateNotes` можно оставить в классе — они используются в других местах (проверить grep).

### BackupViewModel.kt

```kotlin
class BackupViewModel(
    context: Context,
    preferencesManager: PreferencesManager,
    database: AppDatabase,          // было: sessionDao: SessionDao
    translationManager: TranslationManager
) {
    private val backupManager = BackupManager(context, preferencesManager, database, translationManager)
}
```

Обновить `BackupViewModelFactory` соответственно.

### MainActivity.kt (строки ~166 и ~374)

```kotlin
val backupManager = BackupManager(applicationContext, preferencesManager, database, translationManager)
// было: BackupManager(applicationContext, preferencesManager, database.sessionDao(), translationManager)
```

## Проверить после изменений

- `grep -n "autoTranslateName\|autoTranslateNotes"` — убедиться, что в `restoreReplace`/`restoreMerge` вызовов нет
- `grep -n "BackupManager("` — убедиться, что все места создания обновлены
- Компиляция: `import androidx.room.withTransaction` должен резолвиться (зависимость room-ktx уже есть)

## Контекст проекта

- Android Kotlin, MVVM, Room, Jetpack Compose
- `AppDatabase` находится в `app/src/main/java/com/laplog/app/data/database/AppDatabase.kt`
- `SessionDao` в `app/src/main/java/com/laplog/app/data/database/dao/SessionDao.kt`
- `BackupViewModelFactory` вероятно в `app/src/main/java/com/laplog/app/viewmodel/BackupViewModelFactory.kt`
