# Публикация LapLog Free в Google Play

## 1. Регистрация и оплата

- **Создать Google Play Developer аккаунт** ($25 разовый платеж)
- Зарегистрироваться на https://play.google.com/console
- Подготовить платежную информацию

## 2. Подготовка приложения

### 2.1 Release build с подписью

Создать keystore для подписи приложения:

```bash
keytool -genkey -v -keystore laplog-release.keystore \
  -alias laplog -keyalg RSA -keysize 2048 -validity 10000
```

**ВАЖНО**: Сохранить keystore и пароли в безопасном месте! Потеря keystore означает невозможность обновления приложения.

### 2.2 Настройка signing в build.gradle.kts

Добавить в `app/build.gradle.kts`:

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("../laplog-release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = "laplog"
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true  // уже настроено
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

### 2.3 Проверка ProGuard правил

Проверить `app/proguard-rules.pro` для:
- Room Database
- Jetpack Compose
- Kotlin Coroutines

## 3. Маркетинговые материалы

### 3.1 Обязательные графические ресурсы

#### Скриншоты
- **Минимум**: 2 скриншота
- **Рекомендуется**: 8 скриншотов
- **Формат телефон**: 16:9 соотношение (например, 1080x1920)
- **Формат планшет** (опционально): 1536x2048
- **Форматы**: PNG или JPEG, 24-bit RGB

**Какие экраны снять**:
1. Главный экран секундомера (остановлен)
2. Секундомер в действии с несколькими лэпами
3. Экран истории со свернутыми сессиями
4. Экран истории с раскрытой сессией и лэпами
5. Статистика AVG/MEDIAN
6. Выбор комментария
7. Настройки (toggles для milliseconds, screen on, orientation)
8. Темная тема

#### Иконка приложения
- **Размер**: 512×512 пикселей
- **Формат**: PNG (32-bit)
- **Требование**: БЕЗ альфа-канала
- Должна быть отдельной от иконки в приложении

#### Feature Graphic
- **Размер**: 1024×500 пикселей
- **Формат**: PNG или JPEG
- Используется в заголовке страницы приложения

### 3.2 Текстовое описание

#### Краткое описание (до 80 символов)
```
Простой секундомер с лэпами и историей сессий
```

#### Полное описание (до 4000 символов)

**Русская версия**:
```
LapLog Free - минималистичный секундомер с промежуточными отметками (лэпами) и полной историей сессий.

ОСНОВНЫЕ ФУНКЦИИ:
• Точный секундомер с миллисекундами
• Промежуточные отметки (лэпы) с автоматическим расчётом времени
• История всех сессий с возможностью просмотра деталей
• Комментарии к сессиям с автодополнением
• Статистика: AVG и MEDIAN для каждой сессии
• Индикаторы разницы между лэпами (+/-) с цветовой кодировкой

УПРАВЛЕНИЕ:
• Старт/Пауза/Стоп с интуитивным интерфейсом
• Lap - промежуточная отметка без остановки
• Lap+Pause - одновременная отметка и пауза
• Удержание экрана активным во время работы
• Блокировка ориентации экрана

ИСТОРИЯ:
• Автоматическое сохранение всех сессий
• Детальный просмотр каждого лэпа
• Комментарии с автодополнением
• Экспорт в CSV и JSON форматы
• Гибкие опции удаления (отдельная сессия, до даты, все)

ДИЗАЙН:
• Material Design 3
• Темная и светлая темы
• DSEG7 цифровой шрифт для таймера
• Чистый и понятный интерфейс без рекламы

ПРИВАТНОСТЬ:
• Все данные хранятся локально на устройстве
• Никакой рекламы и трекинга
• Не требует интернета
• Open source проект

Идеально для:
✓ Спортивных тренировок
✓ Учёта рабочего времени
✓ Кулинарии и готовки
✓ Любых задач, требующих точного хронометража
```

**Английская версия**:
```
LapLog Free - minimalist stopwatch with lap times and complete session history.

KEY FEATURES:
• Precise stopwatch with milliseconds
• Lap marks with automatic time calculation
• History of all sessions with detailed view
• Session comments with autocomplete
• Statistics: AVG and MEDIAN for each session
• Lap difference indicators (+/-) with color coding

CONTROLS:
• Start/Pause/Stop with intuitive interface
• Lap - intermediate mark without stopping
• Lap+Pause - mark and pause simultaneously
• Keep screen on while running
• Screen orientation lock

HISTORY:
• Automatic saving of all sessions
• Detailed view of each lap
• Comments with autocomplete
• Export to CSV and JSON formats
• Flexible deletion options (single session, before date, all)

DESIGN:
• Material Design 3
• Dark and light themes
• DSEG7 digital font for timer
• Clean and clear interface with no ads

PRIVACY:
• All data stored locally on device
• No ads or tracking
• No internet required
• Open source project

Perfect for:
✓ Sports training
✓ Work time tracking
✓ Cooking and baking
✓ Any task requiring precise timing
```

## 4. Информация о приложении

### 4.1 Базовая информация
- **Название**: LapLog Free
- **Категория**: Tools или Productivity
- **Тип**: Free (бесплатное)
- **Контактный email**: [указать email разработчика]

### 4.2 Возрастной рейтинг
Заполнить анкету в Play Console. Вероятный результат: **Everyone (для всех возрастов)**

### 4.3 Политика конфиденциальности

**Если приложение НЕ собирает пользовательские данные**:
Можно разместить простую политику на GitHub Pages или указать в описании:

```markdown
# Privacy Policy for LapLog Free

LapLog Free does not collect, store, or share any personal data.

All application data (sessions, laps, comments, preferences) is stored
locally on your device only. No data is transmitted to external servers.

The app does not contain:
- Analytics or tracking
- Advertisements
- Third-party services
- Internet connectivity requirements

Contact: [your-email@example.com]
Last updated: [current date]
```

URL политики можно создать как GitHub Pages:
`https://[username].github.io/laplog-app/privacy-policy.html`

### 4.4 Целевая аудитория и контент
- **Целевая аудитория**: взрослые пользователи
- **Рекламный контент**: нет
- **Покупки в приложении**: нет
- **Разрешения**: минимальные (WAKE_LOCK для удержания экрана)

## 5. Техническая подготовка

### 5.1 Проверка манифеста

Убедиться что в `AndroidManifest.xml`:
- Корректное название приложения
- Иконка настроена
- Разрешения минимальны и обоснованы
- versionCode и versionName актуальны

### 5.2 Сборка release AAB

Google Play требует **Android App Bundle (AAB)** формат:

```bash
# Установить переменные окружения с паролями
export KEYSTORE_PASSWORD="your_keystore_password"
export KEY_PASSWORD="your_key_password"

# Собрать release bundle
./gradlew bundleRelease
```

Файл будет создан в:
```
app/build/outputs/bundle/release/app-release.aab
```

### 5.3 Тестирование release build

Перед загрузкой в Play Console протестировать локально:

```bash
# Установить bundletool
# https://github.com/google/bundletool/releases

# Сгенерировать APKs из AAB
bundletool build-apks --bundle=app-release.aab \
  --output=app-release.apks \
  --ks=laplog-release.keystore \
  --ks-key-alias=laplog

# Установить на подключенное устройство
bundletool install-apks --apks=app-release.apks
```

## 6. Загрузка в Play Console

### 6.1 Создание приложения
1. Войти в Play Console
2. "Create app" / "Создать приложение"
3. Заполнить базовую информацию

### 6.2 Настройка страницы в магазине
1. **Main store listing**: загрузить описания, скриншоты, иконки
2. **Store settings**: категория, теги, контакты
3. **Privacy policy**: URL или текст

### 6.3 Настройка контента
1. **App content**: политика конфиденциальности, рейтинг контента
2. **Target audience**: возрастной рейтинг
3. **News apps**: не применимо
4. **COVID-19**: не применимо
5. **Data safety**: указать что данные не собираются

### 6.4 Загрузка сборки

**Этапы тестирования** (рекомендуется):
1. **Internal testing** (внутреннее): для разработчиков
2. **Closed testing** (закрытое): для ограниченной группы тестеров
3. **Open testing** (открытое): для всех желающих
4. **Production** (продакшн): публичный релиз

**Первая загрузка**:
1. Release → Testing → Internal testing
2. Create new release
3. Upload app-release.aab
4. Заполнить Release notes
5. Review and roll out

## 7. Проверка Google

- Обычное время проверки: **1-3 дня**
- Google проверяет на соответствие политикам
- При одобрении приложение появится в Play Store

## 8. Чек-лист перед публикацией

### Обязательно:
- [ ] Google Play Developer аккаунт создан и оплачен
- [ ] Keystore создан и сохранён в безопасном месте
- [ ] Signing config настроен в build.gradle.kts
- [ ] AAB собран и протестирован
- [ ] Минимум 2 скриншота (рекомендуется 8)
- [ ] Иконка 512×512 создана
- [ ] Feature graphic 1024×500 создан
- [ ] Краткое описание написано (≤80 символов)
- [ ] Полное описание написано (≤4000 символов)
- [ ] Политика конфиденциальности подготовлена
- [ ] Контактный email указан
- [ ] Возрастной рейтинг заполнен

### Рекомендуется:
- [ ] Описания на нескольких языках (EN, RU)
- [ ] Скриншоты для планшетов
- [ ] Промо-видео на YouTube
- [ ] Internal/Closed testing перед production
- [ ] ProGuard правила проверены
- [ ] Приложение протестировано на нескольких устройствах

### Документация:
- [ ] README обновлён с ссылкой на Play Store (после публикации)
- [ ] CHANGELOG актуален
- [ ] Версия обновлена в коде

## 9. После публикации

### 9.1 Обновления
Для каждого обновления:
1. Увеличить versionCode и versionName
2. Обновить CHANGELOG
3. Собрать новый AAB
4. Загрузить в Play Console
5. Заполнить Release notes

### 9.2 Мониторинг
- Отслеживать отзывы пользователей
- Проверять crash reports в Play Console
- Анализировать статистику загрузок

### 9.3 Маркетинг
- Добавить badge "Get it on Google Play" в README
- Поделиться на GitHub, Reddit, форумах
- Ответить на отзывы пользователей

## 10. Полезные ссылки

- **Play Console**: https://play.google.com/console
- **Документация Google Play**: https://developer.android.com/distribute
- **Android App Bundle**: https://developer.android.com/guide/app-bundle
- **Bundletool**: https://github.com/google/bundletool
- **Политика контента**: https://play.google.com/about/developer-content-policy/
- **Требования к графике**: https://support.google.com/googleplay/android-developer/answer/9866151

## 11. Текущий статус проекта

### Готово:
- ✅ Приложение полностью функционально
- ✅ ProGuard включен (minifyEnabled = true)
- ✅ Версия определена (0.3.3, versionCode 7)
- ✅ CHANGELOG актуален
- ✅ README оформлен
- ✅ Open source лицензия (см. LICENSE)

### Требуется:
- ❌ Создать keystore для подписи
- ❌ Настроить signing config
- ❌ Сделать скриншоты (8 штук)
- ❌ Создать иконку 512×512
- ❌ Создать feature graphic 1024×500
- ❌ Написать политику конфиденциальности
- ❌ Зарегистрировать Developer аккаунт
- ❌ Собрать release AAB
- ❌ Загрузить в Play Console

---

**Примечание**: Этот документ является руководством и может требовать обновления в соответствии с актуальными требованиями Google Play.
