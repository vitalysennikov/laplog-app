# Задача: перенос настроек из SharedPreferences в SQLite (единый источник правды)

> Долгосрочное решение для бага из [task_accent_save_analysis.md] — там 5 раундов
> точечных фиксов не помогли, потому что per-name toggles/accents дублируются в
> двух хранилищах одновременно.
> Побочный эффект: бэкап (см. [task_restore_perf.md]) сможет опираться на одну БД
> вместо ручной построчной сериализации ~20 полей.

## Проблема

Сейчас данные раскиданы по двум системам с ручной синхронизацией между ними:

1. **SharedPreferences** (`PreferencesManager.kt`) — ~30 отдельных ключей:
   глобальные настройки экрана/таймера/бэкапа + **legacy** per-name карты
   `KEY_NAME_TOGGLES_JSON` (:344) и `KEY_NAME_ACCENTS_JSON` (:345).
2. **Room** (`session_names` таблица, `SessionNameEntity.kt`) — тоже хранит
   per-name `toggles_json`/`accents_json`, это актуальный источник, но код в
   `StopwatchViewModel.loadNameTogglesBody` (:693-721) всё ещё падает в fallback
   на legacy-ключи из SharedPreferences, если Room-значение отсутствует.

Именно эта развилка (Room vs SharedPreferences legacy vs глобальный ключ
`tickAccentsJson`) — причина 4 раундов фиксов подряд (`a2feed5`, `6fd1ad6`,
`3eabe79`, `5558a6c`/`129c882`, все описаны в task_accent_save_analysis.md).
Дополнительно там же зафиксирована нерешённая **«Проблема 1»**: ручной ввод
имени (`updateCurrentName`, `StopwatchViewModel.kt:668`) не сохраняет toggles
предыдущего имени перед переключением — в отличие от `selectNameFromHistory`
(:679), который делает `saveCurrentNameToggles()` перед сменой.

Отдельно, `BackupManager.createBackupData()` (:274-289) и `restoreSettings()`
(:455-476) вручную перечисляют каждое поле `PreferencesManager` — при
добавлении новой настройки легко забыть добавить её в оба места.

## Цель

1. Убрать дублирование per-name хранилища — единственный источник:
   `session_names.toggles_json` / `accents_json` в Room.
2. Перенести оставшиеся глобальные настройки в новую Room-таблицу
   `app_settings` (key-value), чтобы SharedPreferences не участвовали в бэкапе.
3. Починить «Проблему 1».
4. Упростить `BackupManager`: экспорт/импорт настроек через
   `SELECT * FROM app_settings` вместо ~20 отдельных полей.

## Этапы

### Этап 1 — фикс бага без крупного рефакторинга (делать первым)

- `StopwatchViewModel.kt:668` `updateCurrentName` — вызвать
  `saveCurrentNameToggles()` для `prevTrimmed` перед сменой `_currentName`,
  аналогично `selectNameFromHistory` (:680).
- `loadNameTogglesBody` (:693-721) — убрать fallback на
  `preferencesManager.getNameAccents/getNameToggles` из IF-ветки; после фиксов
  причин 4а/4б entity в Room всегда создаётся с непустыми
  `togglesJson`/`accentsJson`, legacy-ключи там не нужны. Оставить fallback
  только в ELSE-ветке (первая встреча имени без entity вообще) на один релиз,
  затем удалить вместе с `KEY_NAME_TOGGLES_JSON`/`KEY_NAME_ACCENTS_JSON`.

Этот этап можно делать и проверять независимо от этапов 2-3.

### Этап 2 и 3 — реализовано иначе, без новой Room-таблицы

Изначальный план (`app_settings` key-value таблица в Room + in-memory кэш
поверх неё) не стал делать: это потребовало бы держать асинхронный
CoroutineScope в `PreferencesManager` и трогать десятки синхронных мест
использования ради выгоды, которую можно получить намного дешевле.

Вместо этого:

- **`PreferencesManager.exportSettingsMap()`/`importSettingsMap()`** —
  generic экспорт/импорт по allowlist'у `BACKUP_KEYS` (список ключей
  SharedPreferences), читает/пишет напрямую через `prefs.all` /
  `SharedPreferences.Editor`. Новая настройка добавляется в бэкап одной
  строкой (ключ в `BACKUP_KEYS`), а не тремя местами (data-класс,
  createBackupData, restoreSettings) как раньше.
- **`BackupData.settings`** стал `Map<String, Any?>?` вместо
  hand-rolled `BackupSettings`; `backupDataToJson`/`jsonToBackupData` пишут/
  читают эту map одним циклом.
- **`BackupSessionName`** (новый тип) — `session_names` (per-name
  `togglesJson`/`accentsJson`) теперь тоже входит в бэкап целиком, а не только
  голые имена. Это отдельно найденный баг (см. ниже) — раньше бэкап/восстановление
  вообще не переносили акценты.
- Бэкапы старого формата (`settings.nameToggles`) по-прежнему читаются для
  обратной совместимости — конвертируются в `legacyNameToggles`, а дальше
  через уже существующий `migrateNameSettingsFromPrefsIfNeeded()` попадут в
  Room при следующем запуске.

Копирование файла `.db` целиком (вместо генерации JSON) осталось нереализованным
и не факт что нужно — merge-режим восстановления (`restoreMerge`) требует
построчного слияния, которое сырой копией файла не сделать; JSON остаётся и
человекочитаемым форматом бэкапа.

## Дополнительно найденный баг (исправлено в этом же заходе)

`BackupManager.restoreSessionNames()` создавал `SessionNameEntity(name = name)`
**без** `togglesJson`/`accentsJson` для каждого имени из списка сессий — т.е.
любое восстановление бэкапа (ручное или через `BackupWorker`) сбрасывало
акценты/toggles всех имён к DEFAULT, независимо от багов в
`StopwatchViewModel`. Отдельно `BackupWorker.kt:23` создавал `BackupManager`
вообще без `sessionNameDao` — автоматический фоновый бэкап никогда не включал
`session_names`. Оба исправлены.

## Файлы (фактически изменённые)

- `viewmodel/StopwatchViewModel.kt` — этап 1 (см. ниже)
- `data/PreferencesManager.kt` — `exportSettingsMap`/`importSettingsMap`, `BACKUP_KEYS`
- `model/BackupData.kt` — `BackupSessionName`, `settings: Map<String, Any?>?`
- `data/BackupManager.kt` — генерация/парсинг JSON, `restoreSessionNames`
- `worker/BackupWorker.kt` — передан `sessionNameDao`

## Проверка

- `grep -n "getNameAccents\|getNameToggles\|KEY_NAME_ACCENTS_JSON\|KEY_NAME_TOGGLES_JSON"` —
  использования legacy-ключей остались только в ELSE-ветке (временный
  fallback) и в коде миграции — подтверждено.
- Ручной тест сценария «Проблема 1»: задать акценты на имени А, вручную
  (без выбора из списка) ввести другое существующее имя Б, вернуться к А —
  акценты А должны сохраниться. **Требует проверки на устройстве.**
- Сделать бэкап, изменить настройки/акценты, восстановить (REPLACE и MERGE) —
  всё должно вернуться как было. **Требует проверки на устройстве.**

## Статус

Этапы 1-3 реализованы (без новой Room-таблицы, см. выше). Не собрано и не
проверено на устройстве — сборка только через GitHub Actions.
