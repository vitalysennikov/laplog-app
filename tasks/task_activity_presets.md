# Задача: Пресеты акцентов для активностей

## Контекст

Сохранение per-name акцентов работает нестабильно (см. [task_accent_save_analysis.md]).
Обходное решение: поставлять вместе с приложением набор активностей с готовыми
акцентами, предзаполненными в `session_names`. Пользователь видит их в выпадающем
списке с первого запуска. После исправления бага он сможет менять акценты и они
будут сохраняться.

Список активностей взят из бэкапа `tmp/laplog_backup_2026-05-21_141736.json`.
Таблица `session_names` в бэкапе **пуста** — акценты не сохранились из-за бага,
поэтому пресеты задаются здесь вручную.

---

## Активности (из бэкапа)

| RU | EN | ZH | Среднее время | Акценты |
|---|---|---|---|---|
| дыхание медведя | Bear Breathing | 熊式呼吸 | ~307 с | TICK/1с, TOCK/8с+7, BELL/8с, CHIME/60с, GONG/300с |
| дыхание полоза | Snake Breathing | 蛇式呼吸 | ~327 с | CHIME/60с, GONG/300с |
| дыхание сокола | Falcon Breathing | 鹰式呼吸 | ~390 с | TICK/1с, TOCK/8с+7, BELL/8с, CHIME/60с, GONG/300с |
| задержки на выдохе | Exhale Holds | 呼气保持 | ~363 с | TICK/180с+30, TOCK/180с+60, BELL/180с+90, CHIME/180с+120, CHIME2/180с+150, GONG/180с |
| дерево жизни | Tree of Life | 生命之树 | ~711 с | WOOD/300с, SOFT/900с |

> ZH-переводы предварительные — уточнить при необходимости.

> Перед реализацией нужно заполнить колонку «Акценты» для каждой активности.
> Формат: список `TickAccent(intervalSeconds, TickSoundType, startOffsetSeconds)`.

---

## Реализация

### Принцип: не менять логику, только предзаполнить данные

Приложение работает без изменений. При первом запуске (флаг в `PreferencesManager`)
пресеты вставляются в таблицу `session_names` с заполненными `togglesJson` и
`accentsJson`. Дальше стандартный `loadNameToggles` подхватывает их как обычно.

Язык определяется по текущей локали устройства — вставляется только один вариант
имени (RU / EN / ZH), чтобы в списке не появлялись дубли на других языках.

```kotlin
// PreferencesManager
var activityPresetsSeedDone: Boolean  // ключ "activityPresetsSeedDone"

// StopwatchViewModel.init
seedActivityPresetsIfNeeded(context)

// StopwatchViewModel
private fun seedActivityPresetsIfNeeded(context: Context) {
    if (preferencesManager.activityPresetsSeedDone) return
    val lang = Locale.getDefault().language  // "ru", "zh", "en", ...
    viewModelScope.launch {
        for (preset in ACTIVITY_PRESETS) {
            val name = when (lang) {
                "ru" -> preset.nameRu
                "zh" -> preset.nameZh
                else -> preset.nameEn
            }
            if (sessionNameDao.getByName(name) == null) {
                sessionNameDao.insert(
                    SessionNameEntity(
                        name = name,
                        togglesJson = serializeCurrentToggles(),
                        accentsJson = serializeTickAccents(preset.accents)
                    )
                )
            }
        }
        preferencesManager.activityPresetsSeedDone = true
    }
}
```

### Файл пресетов: `model/ActivityPresets.kt`

```kotlin
data class ActivityPreset(
    val nameRu: String,
    val nameEn: String,
    val nameZh: String,
    val accents: List<TickAccent>
)

val ACTIVITY_PRESETS: List<ActivityPreset> = listOf(
    ActivityPreset("дыхание медведя", "Bear Breathing", "熊式呼吸", listOf(
        TickAccent(1, TickSoundType.TICK, 0),
        TickAccent(8, TickSoundType.TOCK, 7),
        TickAccent(8, TickSoundType.BELL, 0),
        TickAccent(60, TickSoundType.CHIME, 0),
        TickAccent(300, TickSoundType.GONG, 0),
    )),
    ActivityPreset("дыхание полоза", "Snake Breathing", "蛇式呼吸", listOf(
        TickAccent(60,  TickSoundType.CHIME, 0),   // 1:00, 2:00, ...
        TickAccent(300, TickSoundType.GONG,  0),   // 5:00, 10:00, ...
    )),
    ActivityPreset("дыхание сокола", "Falcon Breathing", "鹰式呼吸", listOf(
        TickAccent(1, TickSoundType.TICK, 0),
        TickAccent(8, TickSoundType.TOCK, 7),
        TickAccent(8, TickSoundType.BELL, 0),
        TickAccent(60, TickSoundType.CHIME, 0),
        TickAccent(300, TickSoundType.GONG, 0),
    )),
    ActivityPreset("задержки на выдохе", "Exhale Holds", "呼气保持", listOf(
        TickAccent(180, TickSoundType.TICK,   30),   // 0:30, 3:30, ...
        TickAccent(180, TickSoundType.TOCK,   60),   // 1:00, 4:00, ...
        TickAccent(180, TickSoundType.BELL,   90),   // 1:30, 4:30, ...
        TickAccent(180, TickSoundType.CHIME,  120),  // 2:00, 5:00, ...
        TickAccent(180, TickSoundType.CHIME2, 150),  // 2:30, 5:30, ...
        TickAccent(180, TickSoundType.GONG,   0),    // 3:00, 6:00, ...
    )),
    ActivityPreset("дерево жизни", "Tree of Life", "生命之树", listOf(
        TickAccent(300, TickSoundType.WOOD, 0),   // 5:00, 10:00, ...
        TickAccent(900, TickSoundType.SOFT, 0),   // 15:00, 30:00, ...
    )),
)
```

---

## Файлы, которые изменятся

| Файл | Действие |
|---|---|
| `model/ActivityPresets.kt` | Создать (новый файл с пресетами) |
| `data/PreferencesManager.kt` | Добавить флаг `activityPresetsSeedDone` |
| `viewmodel/StopwatchViewModel.kt` | Добавить `seedActivityPresetsIfNeeded()` в `init` |
| `ui/` | Без изменений |

---

## Уточняющие вопросы — ответы

1. **Акценты** — заполнены вручную на основе анализа сессий из бэкапа. Детали
   в таблице выше и в `model/ActivityPresets.kt`.

2. **Если пользователь удаляет активность** — пресет не восстанавливается
   (флаг `activityPresetsSeedDone` уже установлен). Вариант А.

3. **Второй бэкап** — пресеты не зависят от бэкапов; второй файл не нужен.

---

## Статус

**На проверке** — реализация завершена, ожидает тестирования на устройстве.

- [x] Заполнить акценты для всех 5 активностей
- [x] Ответить на уточняющие вопросы
- [x] Реализовать `ActivityPresets.kt` (коммит `112fb66`)
- [x] Добавить `seedActivityPresetsIfNeeded` в ViewModel (коммит `112fb66`)
- [x] Добавить флаг в PreferencesManager (коммит `112fb66`)
- [ ] Проверить на устройстве: активности появляются в списке при первом запуске
- [ ] Проверить: акценты загружаются при выборе активности
