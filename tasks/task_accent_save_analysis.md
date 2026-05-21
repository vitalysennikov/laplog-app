# Анализ: сохранение набора акцентов при переключении имени

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
  для полностью битого JSON без `{`).

**Остаточная ошибка в этом исправлении:**
```kotlin
if (result.isEmpty() && !json.contains('{')) DEFAULT_TICK_ACCENTS else result
```
Для `"[]"`: `result.isEmpty()` = true И `!"[]".contains('{')` = true →
возвращается `DEFAULT_TICK_ACCENTS`. То есть пустой список акцентов
(намеренно очищенный) всё равно сбрасывается к дефолту!

---

## Оставшиеся проблемы

### Проблема 1: ручной ввод имени не загружает акценты
`loadNameToggles` вызывается **только** из `selectNameFromHistory`.
`updateCurrentName` (каждое нажатие клавиши) не вызывает `loadNameToggles`.

**Сценарий:**
1. Имя «Бег», акценты A1, сохранены.
2. Пользователь **вручную** набирает «Велик» → акценты не загружаются.
3. Меняет акценты на A2 → сохраняется под «Велик» ✓.
4. **Вручную** набирает «Бег» снова → A1 не загружаются, остаётся A2.

### Проблема 2: `saveCurrentNameToggles` сохраняет под неверным именем
При вводе нового имени (например «В»), `_currentName.value` уже стало «В».
Если затем кликнуть «Велик» из выпадающего:
- `selectNameFromHistory("Велик")` вызывает `saveCurrentNameToggles()` под «В»,
  а не под предыдущим полным именем.

### Проблема 3: пустой список акцентов не сохраняется (см. выше)

---

## Предложение: отдельная структура «имя → акценты»

### Новые методы в PreferencesManager

```kotlin
fun getNameAccents(name: String): String? {
    val allJson = prefs.getString(KEY_NAME_ACCENTS_JSON, null) ?: return null
    return try { JSONObject(allJson).optString(name, null) } catch (_: Exception) { null }
}

fun saveNameAccents(name: String, accentsJson: String) {
    val allJson = prefs.getString(KEY_NAME_ACCENTS_JSON, null)
    val all = if (allJson != null) try { JSONObject(allJson) }
              catch (_: Exception) { JSONObject() } else JSONObject()
    all.put(name, accentsJson)
    prefs.edit().putString(KEY_NAME_ACCENTS_JSON, all.toString()).apply()
}

private const val KEY_NAME_ACCENTS_JSON = "name_accents_json"
```

### Изменения в StopwatchViewModel

**updateTickAccents** — сохранять и в отдельную структуру:
```kotlin
fun updateTickAccents(accents: List<TickAccent>) {
    _tickAccents.value = accents
    preferencesManager.tickAccentsJson = serializeTickAccents(accents)
    val name = _currentName.value.trim()
    if (name.isNotBlank()) {
        preferencesManager.saveNameAccents(name, serializeTickAccents(accents))
    }
    saveCurrentNameToggles()
}
```

**loadNameToggles** — читать из отдельной структуры в первую очередь:
```kotlin
val nameAccentsJson = preferencesManager.getNameAccents(name)
when {
    nameAccentsJson != null ->
        _tickAccents.value = parseTickAccents(nameAccentsJson)
    toggles.tickAccentsJson != null ->
        _tickAccents.value = parseTickAccents(toggles.tickAccentsJson)
    else ->
        _tickAccents.value = parseTickAccents(preferencesManager.tickAccentsJson)
}
```

**updateCurrentName** — загружать акценты при точном совпадении с сохранённым именем:
```kotlin
fun updateCurrentName(name: String) {
    val prevTrimmed = _currentName.value.trim()
    _currentName.value = name
    preferencesManager.currentName = name
    val trimmed = name.trim()
    if (trimmed.isNotBlank() && !_usedNames.value.contains(trimmed)) {
        val updated = _usedNames.value.toMutableSet()
        updated.add(trimmed)
        _usedNames.value = updated
        preferencesManager.usedNames = updated
    }
    // Загружать акценты только при точном совпадении с известным именем
    if (trimmed.isNotBlank() && trimmed != prevTrimmed && _usedNames.value.contains(trimmed)) {
        val savedAccents = preferencesManager.getNameAccents(trimmed)
        if (savedAccents != null) {
            _tickAccents.value = parseTickAccents(savedAccents)
        }
    }
}
```

**parseTickAccents** — исправить проверку пустого массива:
```kotlin
// Было:
if (result.isEmpty() && !json.contains('{')) DEFAULT_TICK_ACCENTS else result
// Стало:
if (result.isEmpty() && !json.contains('[')) DEFAULT_TICK_ACCENTS else result
```

### Итог
Нужны все четыре изменения. Отдельная структура + исправление parseTickAccents
устраняют основные проблемы и покрывают оба сценария переключения.
