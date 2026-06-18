# Анализ: сохранение набора акцентов при переключении имени

> **Статус: в работе** — четвёртое исправление применено, ожидает проверки.
>
> Обходное решение: [task_activity_presets.md]

## Хронология попыток исправления

### Коммит `a2feed5` — первое исправление
**Проблема:** диалог TickSettings закрывался кнопкой «Закрыть» без вызова
`saveCurrentNameToggles()`. Изменения применялись через `updateTickAccents`
на каждое изменение внутри диалога, но явного сохранения при закрытии не было.

**Исправление:** кнопка → «Сохранить», добавлен явный `saveCurrentNameToggles()`
в `onSave`.

---

### Коммит `6fd1ad6` — второе исправление
**Проблема:** `loadNameToggles` при загрузке имени с сохранёнными акцентами
перезаписывал глобальный ключ `preferencesManager.tickAccentsJson`:
```kotlin
preferencesManager.tickAccentsJson = it   // ← перезаписывал глобальный ключ!
```
При возврате к имени без своих акцентов (`tickAccentsJson == null`) читался
уже «испорченный» глобальный пресет.

**Исправление:**
- `tickAccentsJson != null` → восстанавливать `_tickAccents.value`, НЕ трогать
  `preferencesManager.tickAccentsJson`.
- `tickAccentsJson == null` → явно читать `preferencesManager.tickAccentsJson`.
- `parseTickAccents("[]")` → больше не сбрасывается к DEFAULT (откат только
  для полностью битого JSON без `[`).

---

### Коммит `3eabe79` — третье исправление

Найдены три корневые причины, по которым акценты сбрасывались к DEFAULT при
переключении имени:

#### Причина 1: `saveSession` создаёт entity без togglesJson/accentsJson
```kotlin
// Было в saveSession:
sessionNameDao.insert(SessionNameEntity(name = name))  // togglesJson=null, accentsJson=null
```
Когда пользователь сохраняет сессию с именем впервые, entity в `session_names`
создаётся с null-полями. При последующем `loadNameToggles` для этого имени код
попадает в `else`-ветку, там `preferencesManager.getNameToggles` возвращает null
(миграция уже выполнена), и ничего не применяется — акценты остаются от
предыдущего состояния (при старте приложения это DEFAULT).

#### Причина 2: Заражённый глобальный ключ как fallback в IF-ветке
```kotlin
// Было в loadNameToggles (IF-ветка):
_tickAccents.value = parseTickAccents(
    entity.accentsJson ?: preferencesManager.tickAccentsJson  // ← заражённый глобал!
)
```
Если `accentsJson == null` (entity создана через saveSession), читался глобальный
ключ. Этот ключ перезаписывался при каждом `updateTickAccents` любого имени,
т.е. содержал акценты последнего редактировавшего имени, а не DEFAULT и не
акценты целевого имени.

#### Причина 3: `updateTickAccents` не сохранял per-name акценты в SharedPreferences
```kotlin
// Было в updateTickAccents:
preferencesManager.tickAccentsJson = serializeTickAccents(accents)  // только глобал
saveCurrentNameToggles()  // DB — OK, но SharedPreferences per-name — нет
```
Метод `preferencesManager.saveNameAccents(name, accentsJson)` (введённый ранее)
не вызывался, поэтому `preferencesManager.getNameAccents(name)` в fallback-цепочке
всегда возвращал null.

---

### Четвёртое исправление (текущее)

Три предыдущих исправления не решили проблему, потому что оба источника null-entity
не были устранены.

#### Причина 4а: `saveSession` создаёт entity без togglesJson/accentsJson

```kotlin
// Было:
sessionNameDao.insert(SessionNameEntity(name = name))  // togglesJson=null, accentsJson=null
```

Когда сессия сохраняется с новым именем, `saveSession` создаёт entity с
`togglesJson=null, accentsJson=null`. При следующем `loadNameToggles` ELSE-ветка
применяет DEFAULT и **записывает его в DB**. Отныне IF-ветка всегда читает DEFAULT.

**Исправление:** `saveSession` теперь сохраняет текущие toggles и акценты:

```kotlin
sessionNameDao.insert(
    SessionNameEntity(
        name = name,
        togglesJson = serializeCurrentToggles(),
        accentsJson = serializeTickAccents(_tickAccents.value)
    )
)
```

#### Причина 4б: сид v1 пропускает существующие entity с null accentsJson

```kotlin
// Было в seedActivityPresetsIfNeeded:
if (sessionNameDao.getByName(name) == null) {
    sessionNameDao.insert(...)  // иначе — ничего
}
```

Если пользователь ранее сохранял сессии с именем пресета («дыхание медведя» и т.п.),
entity уже существует с `accentsJson=null`. Сид видит непустой результат и пропускает
вставку. В итоге ELSE-ветка записывает DEFAULT вместо пресетных акцентов.

**Исправление:** сид v2 обновляет существующие entity с `accentsJson=null`:

```kotlin
when {
    existing == null -> sessionNameDao.insert(...)           // v1: вставить новую
    existing.accentsJson == null -> sessionNameDao.update(   // v2: починить старую
        existing.copy(accentsJson = serializeTickAccents(preset.accents))
    )
}
```

Новый флаг `activityPresetsSeedV2Done` гарантирует однократный запуск миграции.
Пользовательские акценты (accentsJson != null) при этом не перезаписываются.

---

## Применённые исправления (StopwatchViewModel.kt)

### 1. `updateTickAccents` — сохранять и в per-name SharedPreferences
```kotlin
fun updateTickAccents(accents: List<TickAccent>) {
    _tickAccents.value = accents
    val accentsJson = serializeTickAccents(accents)
    preferencesManager.tickAccentsJson = accentsJson
    val name = _currentName.value.trim()
    if (name.isNotBlank()) {
        preferencesManager.saveNameAccents(name, accentsJson)
    }
    saveCurrentNameToggles()
}
```

### 2. `loadNameToggles` IF-ветка — правильная fallback-цепочка
```kotlin
// Стало:
_tickAccents.value = parseTickAccents(
    entity.accentsJson
        ?: preferencesManager.getNameAccents(name)   // per-name legacy
        ?: serializeTickAccents(DEFAULT_TICK_ACCENTS)  // явный DEFAULT, не глобал
)
```

Аналогично в `if (toggles != null)` else-ветки:
```kotlin
val accentsJson = preferencesManager.getNameAccents(name)
    ?: toggles.tickAccentsJson
    ?: serializeTickAccents(DEFAULT_TICK_ACCENTS)  // было: preferencesManager.tickAccentsJson
```

### 3. `loadNameToggles` ELSE-ветка — обработка entity с null togglesJson
Добавлена ветка `else` (когда `toggles == null`), которая раньше была NO-OP:
```kotlin
} else {
    // Entity из saveSession: togglesJson=null, нет legacy префов.
    val nameAccentsJson = preferencesManager.getNameAccents(name)
    _tickAccents.value = if (nameAccentsJson != null) {
        parseTickAccents(nameAccentsJson)
    } else {
        DEFAULT_TICK_ACCENTS
    }
    // Сохраняем в DB, чтобы следующий load пошёл по быстрому IF-пути.
    val togglesJson = serializeCurrentToggles()
    val accentsJson = serializeTickAccents(_tickAccents.value)
    val dbEntity = entity ?: SessionNameEntity(name = name)
    if (entity != null) {
        sessionNameDao.update(dbEntity.copy(togglesJson = togglesJson, accentsJson = accentsJson))
    } else {
        sessionNameDao.insert(dbEntity.copy(togglesJson = togglesJson, accentsJson = accentsJson))
    }
}
```

---

## Оставшиеся известные ограничения

### Проблема 1: ручной ввод имени не загружает акценты с сохранением предыдущего
`loadNameToggles` вызывается из `updateCurrentName` только при точном совпадении
с известным именем, но `saveCurrentNameToggles` для предыдущего имени НЕ
вызывается. Если пользователь изменил акценты, не переключив имя явно через
`selectNameFromHistory`, изменения могут не сохраниться для предыдущего имени.

**Сценарий:**
1. Имя «Бег», акценты A1. Пользователь меняет акценты, не нажимает «Сохранить».
2. Вручную стирает поле и набирает «Велик» → `loadNameToggles("Велик")` — без
   сохранения A1 для «Бег».
3. A1 сбрасывается. (Но в DB «Бег» хранит старый вариант A1 от предыдущего
   явного сохранения, так что деградация минимальна.)

### Проблема 2: первое переключение на имя без сохранённых акцентов показывает DEFAULT
После исправления 3 это стало предсказуемым поведением: имя без сохранённых
акцентов показывает DEFAULT и сохраняет их в DB. При последующих переключениях
пользователь видит то, что задал явно.
