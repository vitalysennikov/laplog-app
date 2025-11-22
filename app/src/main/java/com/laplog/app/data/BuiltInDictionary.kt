package com.laplog.app.data

/**
 * Built-in dictionary for common sports/training phrases
 * Provides instant offline translations for typical use cases
 */
object BuiltInDictionary {

    private data class Translation(
        val en: String,
        val ru: String,
        val zh: String
    )

    private val dictionary = mapOf(
        // Running
        "running" to Translation("Running", "Бег", "跑步"),
        "run" to Translation("Run", "Пробежка", "跑步"),
        "morning run" to Translation("Morning run", "Утренняя пробежка", "晨跑"),
        "evening run" to Translation("Evening run", "Вечерняя пробежка", "晚跑"),
        "jog" to Translation("Jog", "Бег трусцой", "慢跑"),
        "sprint" to Translation("Sprint", "Спринт", "冲刺"),

        // Swimming
        "swimming" to Translation("Swimming", "Плавание", "游泳"),
        "swim" to Translation("Swim", "Заплыв", "游泳"),

        // Cycling
        "cycling" to Translation("Cycling", "Велоспорт", "骑行"),
        "bike" to Translation("Bike", "Велосипед", "自行车"),
        "cycling training" to Translation("Cycling training", "Тренировка на велосипеде", "自行车训练"),

        // Gym/Training
        "training" to Translation("Training", "Тренировка", "训练"),
        "workout" to Translation("Workout", "Тренировка", "锻炼"),
        "gym" to Translation("Gym", "Спортзал", "健身房"),
        "fitness" to Translation("Fitness", "Фитнес", "健身"),
        "exercise" to Translation("Exercise", "Упражнение", "练习"),

        // Sports
        "football" to Translation("Football", "Футбол", "足球"),
        "basketball" to Translation("Basketball", "Баскетбол", "篮球"),
        "tennis" to Translation("Tennis", "Теннис", "网球"),
        "volleyball" to Translation("Volleyball", "Волейбол", "排球"),
        "yoga" to Translation("Yoga", "Йога", "瑜伽"),
        "boxing" to Translation("Boxing", "Бокс", "拳击"),

        // Time periods
        "morning" to Translation("Morning", "Утро", "早上"),
        "afternoon" to Translation("Afternoon", "День", "下午"),
        "evening" to Translation("Evening", "Вечер", "晚上"),
        "night" to Translation("Night", "Ночь", "夜晚"),

        // Intensity
        "easy" to Translation("Easy", "Легкая", "轻松"),
        "moderate" to Translation("Moderate", "Средняя", "中等"),
        "hard" to Translation("Hard", "Тяжелая", "困难"),
        "intense" to Translation("Intense", "Интенсивная", "激烈"),

        // Types
        "interval" to Translation("Interval", "Интервальная", "间歇"),
        "endurance" to Translation("Endurance", "На выносливость", "耐力"),
        "speed" to Translation("Speed", "Скоростная", "速度"),
        "recovery" to Translation("Recovery", "Восстановительная", "恢复"),

        // Common combinations
        "speed training" to Translation("Speed training", "Скоростная тренировка", "速度训练"),
        "interval training" to Translation("Interval training", "Интервальная тренировка", "间歇训练"),
        "strength training" to Translation("Strength training", "Силовая тренировка", "力量训练"),
        "cardio" to Translation("Cardio", "Кардио", "有氧"),

        // Common actions
        "warm up" to Translation("Warm up", "Разминка", "热身"),
        "cool down" to Translation("Cool down", "Заминка", "放松"),
        "stretching" to Translation("Stretching", "Растяжка", "拉伸"),

        // Weather
        "outdoor" to Translation("Outdoor", "На улице", "户外"),
        "indoor" to Translation("Indoor", "В помещении", "室内"),
        "rain" to Translation("Rain", "Дождь", "雨"),
        "snow" to Translation("Snow", "Снег", "雪"),

        // Competition
        "race" to Translation("Race", "Забег", "比赛"),
        "competition" to Translation("Competition", "Соревнование", "竞赛"),
        "marathon" to Translation("Marathon", "Марафон", "马拉松"),
        "half marathon" to Translation("Half marathon", "Полумарафон", "半程马拉松"),

        // Personal records
        "personal best" to Translation("Personal best", "Личный рекорд", "个人最佳"),
        "pr" to Translation("PR", "ЛР", "个人最佳"),
        "pb" to Translation("PB", "ЛР", "个人最佳")
    )

    /**
     * Get translation for a text in specified language
     * @param text Text to translate (case-insensitive)
     * @param targetLang Target language code (en, ru, zh)
     * @return Translated text or null if not found in dictionary
     */
    fun getTranslation(text: String, targetLang: String): String? {
        val lowerText = text.lowercase().trim()
        val translation = dictionary[lowerText] ?: return null

        return when (targetLang) {
            "en" -> translation.en
            "ru" -> translation.ru
            "zh" -> translation.zh
            else -> null
        }
    }

    /**
     * Check if text exists in dictionary
     */
    fun contains(text: String): Boolean {
        return dictionary.containsKey(text.lowercase().trim())
    }

    /**
     * Get all supported phrases
     */
    fun getAllPhrases(): Set<String> {
        return dictionary.keys
    }
}
