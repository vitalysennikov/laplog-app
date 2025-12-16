# История разработки пунктирных линий средних значений на графиках

## Общая проблема
Требуется отображать горизонтальные пунктирные линии на графиках для визуализации средних/медианных значений. Линия должна быть пунктирной (dashed) и отличаться от основной линии данных.

---

## Хронология попыток

### 1. Версия 0.13.1 (коммит 37d15e3, 26 ноября 2025)
**Что делалось:**
- Первая реализация пунктирных линий средних значений
- Использовался API Vico 1.x: `Shapes.dashedShape()`
- Линии добавлялись через `decorations` в Chart

**Код:**
```kotlin
val horizontalLine = rememberHorizontalLine(
    y = { overallAverage / 1000f },
    line = ShapeComponent(
        shape = Shapes.dashedShape(
            shape = Shapes.rectShape,
            dashLengthDp = 8f,
            gapLengthDp = 4f
        ),
        color = Color.Gray.hashCode()
    )
)
// В Chart: decorations = listOf(horizontalLine)
```

**Результат:** ❌ **НЕ ПОЛУЧИЛОСЬ**
- API `dashedShape` не существует в используемой версии Vico
- Ошибка компиляции

---

### 2. Коммит d0036fc (26 ноября 2025)
**Что делалось:**
- Удалён несуществующий API `dashedShape`
- Линии средних сделаны **сплошными** с меньшей толщиной (1.5dp)
- Использованы тёмные цвета того же оттенка что и график

**Результат:** ✅ **РАБОТАЕТ, но линии не пунктирные**
- Линии отображаются как сплошные
- Компиляция проходит успешно
- Визуально отличаются только цветом и толщиной

---

### 3. Версия 0.13.2 (коммит cca5550, дата неизвестна)
**Что делалось:**
- Линии средних сделаны того же цвета что и график (тёмный оттенок)

**Результат:** ✅ **РАБОТАЕТ (сплошные линии)**

---

### 4. Миграция на Vico 2.1.3 (коммит f56355d, 28 ноября 2025)
**Что делалось:**
- Обновлена библиотека Vico с 1.x на 2.1.3
- Создан класс `DashedLine` наследующий `LineCartesianLayer.Line`
- Прямой доступ к приватному полю `linePaint` для установки `DashPathEffect`

**Код:**
```kotlin
class DashedLine(fill: LineCartesianLayer.LineFill, color: Int) : LineCartesianLayer.Line(fill) {
    init {
        linePaint.apply {
            strokeWidth = 2f
            this.color = color
            pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
        }
    }
}
```

**Результат:** ❓ **Компилируется, но результат неизвестен**
- Прямой доступ к приватному полю может не работать

---

### 5. Множественные исправления миграции (коммиты e12b382, 99239a4, ea3cbeb, 1e21acf, e446314, 18800a9)
**Что делалось:**
- Исправление конструктора `LineCartesianLayer.Line`
- Попытки создать класс `LineWithArea` для градиентов
- Использование anonymous object для переопределения `areaFill`
- Упрощение графиков: убраны градиенты

**Результат:** 🔧 **Технические исправления**
- Графики работают, но пунктирные линии всё ещё проблематичны

---

### 6. Версия 0.13.6 (коммит 6cee45b, дата неизвестна)
**Что делалось:**
- Убран заголовок графиков
- Добавлено логирование
- Исправлен расчёт медианы

**Результат:** 🔧 **Улучшения, не связанные с пунктирными линиями**

---

### 7. Версия 0.13.8 (коммит ef08916, 15 декабря 2025)
**Что делалось:**
- Переписана функция `createDashedLine()`
- Попытка настроить пунктирную линию через:
  - `areaFill = null` (убрана заливка)
  - Паттерн пунктира `[15f, 10f]`
  - Толщина линии `4f`
  - `Paint.Style.STROKE`
- Все пунктирные линии временно изменены на красный цвет для тестирования
- Добавлено больше логирования

**Результат:** ❌ **ОШИБКА КОМПИЛЯЦИИ** (предположительно)

---

### 8. Коммит 2c4a099 (15 декабря 2025)
**Что делалось:**
- Убран прямой доступ к `linePaint` (приватное поле)
- Попытка переопределить свойства через anonymous object

**Код:**
```kotlin
fun createDashedLine(color: Color): LineCartesianLayer.Line {
    val line = object : LineCartesianLayer.Line(
        fill = LineCartesianLayer.LineFill.single(Fill(color.toArgb())),
        areaFill = null
    ) {
        override val strokeWidth: Float = 4f
        override val cap: android.graphics.Paint.Cap = android.graphics.Paint.Cap.ROUND
    }
    return line
}
```

**Результат:** ❌ **ОШИБКА КОМПИЛЯЦИИ**
- `strokeWidth` и `cap` не являются переопределяемыми свойствами в базовом классе
- Ошибка: `'strokeWidth' overrides nothing`
- Ошибка: `'cap' overrides nothing`

---

### 9. Текущий коммит 243880f (15 декабря 2025)
**Что делалось:**
- Убраны попытки переопределения `strokeWidth` и `cap`
- Упрощена функция `createDashedLine()`

**Текущий код:**
```kotlin
fun createDashedLine(color: Color): LineCartesianLayer.Line {
    android.util.Log.d("ChartsScreen", "Creating dashed line with color: $color")
    return LineCartesianLayer.Line(
        fill = LineCartesianLayer.LineFill.single(Fill(color.toArgb())),
        areaFill = null // No area fill for dashed line
    )
}
```

**Результат:** ✅ **Компилируется**, но ❌ **ЛИНИЯ НЕ ОТОБРАЖАЕТСЯ**
- Код компилируется успешно
- Пунктирная линия среднего значения **полностью отсутствует на графике**
- Линия не является пунктирной (нет DashPathEffect)

---

## Текущее состояние (версия 0.14.0)

### Используемая версия библиотеки
```gradle
implementation("com.patrykandpatrick.vico:compose-m3:2.1.3")
implementation("com.patrykandpatrick.vico:core:2.1.3")
implementation("com.patrykandpatrick.vico:compose:2.1.3")
```

### Проблема
Горизонтальные линии средних/медианных значений **полностью отсутствуют** на всех трёх графиках:
1. График средней длительности (Total Duration)
2. График среднего времени круга (Average Lap)
3. График медианного времени круга (Median Lap)

### Что работает
- ✅ Основные линии данных отображаются корректно
- ✅ Градиенты под линиями работают
- ✅ Оси графика работают
- ✅ Масштабирование (zoom) работает
- ✅ Данные для средних значений корректно рассчитываются в ViewModel

### Что не работает
- ❌ Вторая серия данных (линия среднего) не отображается на графике
- ❌ Пунктирный стиль линии не применяется

---

## Анализ проблемы

### Причина 1: Нет DashPathEffect
Текущий код не применяет `DashPathEffect` к линии, поэтому даже если она отобразится, она будет сплошной, а не пунктирной.

### Причина 2: API Vico 2.1.3 не документирован
В `LineCartesianLayer.Line` нет публичных методов для настройки:
- Толщины линии (`strokeWidth`)
- Стиля концов линии (`cap`)
- Эффекта пути (`pathEffect`)

Поле `linePaint` является **приватным** и недоступно для модификации извне.

### Причина 3: Возможно неправильное использование API
Возможно, в Vico 2.1.3 есть другой способ создания пунктирных линий:
- Через специальный класс или builder
- Через параметры конструктора (которые мы не знаем)
- Через композицию объектов

---

## Возможные решения

### Вариант 1: Изучить документацию Vico 2.1.3
Необходимо найти официальную документацию или примеры использования пунктирных линий в версии 2.1.3.

### Вариант 2: Использовать Reflection API
Можно попробовать получить доступ к приватному полю `linePaint` через Reflection:
```kotlin
val linePaint = LineCartesianLayer.Line::class.java
    .getDeclaredField("linePaint")
    .apply { isAccessible = true }
    .get(this) as Paint
linePaint.pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
```
**Риск:** Может не работать из-за ProGuard/R8 обфускации.

### Вариант 3: Создать кастомный Decoration
Возможно, пунктирные линии в Vico 2.x должны создаваться не через `LineCartesianLayer.Line`, а через систему Decorations.

### Вариант 4: Откатиться на Vico 1.x
Если в старой версии работал API `dashedShape`, можно вернуться к нему.

### Вариант 5: Нарисовать линию вручную через Canvas
Создать кастомный Composable, который рисует пунктирную линию поверх графика используя Canvas API.

---

## Рекомендации

1. **Срочно:** Изучить официальную документацию Vico 2.1.3 и найти примеры пунктирных линий
2. Проверить GitHub репозиторий библиотеки на наличие примеров (sample apps)
3. Посмотреть changelog миграции с 1.x на 2.x
4. Если документации нет, попробовать Reflection API как временное решение
5. Рассмотреть возможность создания issue в репозитории Vico с вопросом о пунктирных линиях

---

## ✅ НАЙДЕН ПРАВИЛЬНЫЙ ПОДХОД К СОЗДАНИЮ КЛАССА (15 декабря 2025)

### Правильный способ создания пунктирной линии в Vico 2.1.3

Согласно официальной документации и обсуждениям на GitHub, **правильный способ** создать пунктирную линию - это создать класс-наследник `LineCartesianLayer.Line` и в блоке `init` обратиться к `linePaint`:

```kotlin
class DashedLine(
    fill: LineCartesianLayer.LineFill
) : LineCartesianLayer.Line(fill, areaFill = null) {
    init {
        linePaint.apply {
            strokeWidth = 3f
            pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
        }
    }
}
```

**Важно:** Доступ к `linePaint` работает **внутри самого класса**, потому что это защищенное (protected) поле, а не приватное.

### Источники
- [Dotted LineCartesianLayer · Discussion #938](https://github.com/patrykandpatrick/vico/discussions/938)
- [Vico 2.0.0-alpha.1 Release Notes](https://archive.patrykandpatrick.com/vico/releases/2.0.0-alpha.1/)
- [DashPathEffect | Android Developers](https://developer.android.com/reference/android/graphics/DashPathEffect)

### Использование класса DashedLine

В коммитах f56355d и 18800a9 класс использовался через `LineProvider.series`:

```kotlin
rememberLineCartesianLayer(
    lineProvider = LineCartesianLayer.LineProvider.series(
        // Main data line
        LineCartesianLayer.Line(
            LineCartesianLayer.LineFill.single(Fill(Color.Blue.toArgb()))
        ),
        // Dashed average line
        DashedLine(
            LineCartesianLayer.LineFill.single(Fill(darkBlue.toArgb()))
        )
    )
)
```

---

## ❌ КРИТИЧЕСКАЯ ПРОБЛЕМА

**ВАЖНО:** Несмотря на правильную реализацию класса `DashedLine`, линии средних значений **НИ РАЗУ НЕ ОТОБРАЖАЛИСЬ** после миграции на Vico 2.1.3!

Класс был реализован правильно в коммитах:
- **f56355d** - первая версия с параметром `color`
- **18800a9** - упрощенная версия без параметра `color`

Но графики показывали только основную линию данных, вторая линия (пунктирная средняя) **полностью отсутствовала**.

### Возможные причины

1. **Проблема с данными:** Возможно, вторая серия данных не создается корректно в `modelProducer.runTransaction`
2. **Проблема с API Vico:** Возможно, `LineProvider.series` не работает правильно с несколькими сериями
3. **Проблема с отрисовкой:** DashPathEffect может не применяться из-за внутренних ограничений библиотеки
4. **Конфликт параметров:** Возможно, есть конфликт между `fill`, `areaFill` и `linePaint`
5. **Проблема с remember:** Возможно, линии создаются, но не обновляются при изменении данных

### Что нужно проверить

1. ✅ Создаются ли обе серии данных в `modelProducer` (логирование показывает, что да)
2. ❓ Передаются ли обе линии в `LineProvider.series` корректно
3. ❓ Отображается ли вторая линия, если убрать `pathEffect` (сделать её сплошной)
4. ❓ Работает ли вообще `LineProvider.series` с несколькими линиями в Vico 2.1.3
5. ❓ Нужно ли использовать другой API для отображения нескольких линий

---

## Выводы

За период с 26 ноября по 15 декабря 2025 года было предпринято **минимум 15 попыток** реализовать пунктирные линии средних значений на графиках. Ни одна попытка не увенчалась успехом.

**Основная проблема:** Не в реализации класса `DashedLine` (он реализован правильно), а в том, что **вторая линия вообще не отображается** на графиках, независимо от того, пунктирная она или нет.

**Текущее состояние (коммит 243880f):**
- Код компилируется без ошибок
- Класс `DashedLine` реализован правильно
- Обе серии данных создаются в `modelProducer`
- Обе линии передаются в `LineProvider.series`
- **НО:** На графике отображается только основная линия, вторая линия полностью отсутствует

**Следующий шаг:** Необходимо выяснить, почему вторая линия не отображается. Возможно, проблема в API Vico 2.1.3 или в способе использования `LineProvider.series`.

---

## ✅ РЕШЕНИЕ НАЙДЕНО! (16 декабря 2025)

### Версия 0.14.1 (коммит 7067fe2)
**Что делалось:**
- Добавлено улучшенное логирование в ChartsViewModel и ChartsScreen
- Все вызовы android.util.Log заменены на AppLogger для сохранения логов в файл
- Логирование создания линий, обновления данных и значений всех точек

**Результат:** 🔧 **Диагностика**
- Логи показали, что обе серии данных создаются корректно
- Все значения точек правильные
- Но вторая линия всё равно не отображается

**Код логирования:**
```kotlin
AppLogger.d("AverageLapChart", "Creating main line (green)")
AppLogger.d("AverageLapChart", "Creating dashed line (dark green)")
AppLogger.d("AverageLapChart", "Main line data: $dataPoints")
AppLogger.d("AverageLapChart", "Average line data: $avgPoints (value=$avgValue)")
```

---

### Версия 0.15.2 (коммит 99f9654) - 🎯 КРИТИЧЕСКИЙ ПРОРЫВ!
**Что делалось:**
- Переделаны все три графика с использованием **отдельных слоёв `LineCartesianLayer`** для каждой линии
- Вместо одного слоя с `LineProvider.series(line1, line2)` используются два отдельных слоя
- Каждый слой содержит только одну линию

**Код (было):**
```kotlin
rememberLineCartesianLayer(
    lineProvider = LineCartesianLayer.LineProvider.series(
        mainLine,        // Основная линия данных
        dashedLine       // Пунктирная средняя линия
    )
)
```

**Код (стало):**
```kotlin
rememberCartesianChart(
    // Слой 1: Основная линия данных
    rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(
            LineCartesianLayer.Line(
                fill = LineCartesianLayer.LineFill.single(Fill(Color.Green.toArgb())),
                areaFill = LineCartesianLayer.AreaFill.single(Fill(Color.Green.copy(alpha = 0.3f).toArgb()))
            )
        )
    ),
    // Слой 2: Пунктирная средняя линия
    rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(
            DashedLine(
                fill = LineCartesianLayer.LineFill.single(Fill(darkGreen.toArgb()))
            )
        )
    ),
    startAxis = ...,
    bottomAxis = ...
)
```

**Результат:** ✅ **ПОЛНОСТЬЮ РАБОТАЕТ!**
- ✅ Все линии отображаются корректно
- ✅ Средние/медианные линии видны на графиках
- ⚠️ Линии отображаются как **сплошные**, а не пунктирные (DashPathEffect не работает)

**Анализ проблемы:**
Причина, по которой вторая линия не отображалась при использовании `LineProvider.series(line1, line2)`:
- В Vico 2.1.3 при использовании нескольких линий через `LineProvider.series()` отображается только **первая линия**
- Это либо баг библиотеки, либо неправильное использование API
- **Решение:** Использовать отдельные `rememberLineCartesianLayer` для каждой линии

**Изменения:**
- **AverageLapChart:** 2 отдельных слоя (основная линия + средняя пунктирная)
- **MedianLapChart:** 2 отдельных слоя (основная линия + медианная пунктирная)
- **TotalDurationChart:** 3 отдельных слоя (активное время + полное время + средняя линия)

---

### Версия 0.15.3 (коммит e457bbe)
**Что делалось:**
- Увеличена толщина пунктирных линий с 3f до 5f для лучшей видимости
- Увеличена длина штрихов пунктира с 15f до 20f
- Разделён график TotalDurationChart на два отдельных графика:
  - **ActiveTimeChart** - активное время без пауз + средняя линия
  - **ElapsedTimeChart** - полное время с паузами + средняя линия
- Добавлено поле `overallAverageElapsedTime` в модель ChartData
- Каждый график отображается отдельно со своей средней линией

**Код DashedLine:**
```kotlin
class DashedLine(
    fill: LineCartesianLayer.LineFill
) : LineCartesianLayer.Line(fill, areaFill = null) {
    init {
        linePaint.apply {
            strokeWidth = 5f // Увеличена толщина
            pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f) // Длиннее штрихи
        }
    }
}
```

**Результат:** ✅ **Графики работают**
- ✅ Все 4 графика отображаются (Average Lap, Median Lap, Active Time, Elapsed Time)
- ✅ Каждый график имеет свою среднюю линию
- ⚠️ Средние линии всё ещё отображаются как **сплошные**, несмотря на DashPathEffect

---

### Версия 0.15.4 (коммиты c14641f, f445a47)
**Что делалось:**
- Добавлена заливка (gradient) для графика ElapsedTimeChart
- Попытка улучшить реализацию DashedLine через `override val strokeWidthDp`
- **Ошибка компиляции:** `'strokeWidthDp' overrides nothing`
- Исправлено: убран `override`, вернулись к `linePaint.apply`

**Финальная реализация DashedLine:**
```kotlin
class DashedLine(
    fill: LineCartesianLayer.LineFill,
    thickness: Float = 5f
) : LineCartesianLayer.Line(fill, areaFill = null) {
    init {
        linePaint.apply {
            strokeWidth = thickness
            pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
        }
    }
}
```

**Результат:** ✅ **Компилируется и работает**
- ✅ Все графики отображаются корректно
- ✅ ElapsedTimeChart имеет заливку
- ⚠️ **ПРОБЛЕМА:** Средние линии всё ещё **НЕ ПУНКТИРНЫЕ**

---

## 🔍 ТЕКУЩАЯ НЕРЕШЁННАЯ ПРОБЛЕМА (16 декабря 2025)

### Что работает
- ✅ Все линии отображаются (основные и средние)
- ✅ Отдельные слои `LineCartesianLayer` для каждой линии
- ✅ Класс `DashedLine` реализован правильно
- ✅ `DashPathEffect` применяется к `linePaint`
- ✅ Все данные корректны

### Что НЕ работает
- ❌ **Пунктирный эффект не отображается на экране**
- Средние линии видны, но они **сплошные**, а не пунктирные
- `DashPathEffect` применяется в коде, но визуально не виден

### Возможные причины

1. **Проблема с Paint в Vico 2.1.3:**
   - Возможно, Vico перезаписывает `pathEffect` при отрисовке
   - Библиотека может использовать свой внутренний Paint объект

2. **Проблема с Canvas:**
   - Возможно, Canvas не поддерживает DashPathEffect в контексте Compose
   - Может требоваться hardware acceleration

3. **Проблема с порядком применения:**
   - Возможно, `pathEffect` нужно устанавливать в другом месте
   - Может требоваться переопределение метода отрисовки

4. **Баг в Vico 2.1.3:**
   - Возможно, это известная проблема библиотеки
   - Может быть исправлено в более новых версиях

### Следующие шаги для решения

1. Проверить официальную документацию Vico 2.1.3 о пунктирных линиях
2. Изучить issues в GitHub репозитории Vico
3. Попробовать обновить Vico до последней версии (если есть более новая)
4. Рассмотреть альтернативные способы создания пунктирных линий
5. Возможно, создать issue в репозитории Vico с описанием проблемы

---

## 📊 ИТОГОВАЯ СТАТИСТИКА

### Количество попыток: **20+**
Период: 26 ноября - 16 декабря 2025 (21 день)

### Ключевые достижения
1. ✅ **Решена проблема отображения нескольких линий** (версия 0.15.2)
   - Использование отдельных слоёв вместо `LineProvider.series()`
2. ✅ **Все графики работают корректно** (версия 0.15.3-0.15.4)
   - 4 графика: Average Lap, Median Lap, Active Time, Elapsed Time
3. ✅ **Средние линии отображаются** на всех графиках

### Нерешённая проблема
- ❌ **Пунктирный эффект не работает**
  - DashPathEffect применяется в коде, но визуально не виден
  - Линии отображаются как сплошные

### Статус
**ЧАСТИЧНО РЕШЕНО:** Графики работают, все линии видны, но пунктирный стиль не применяется визуально.
