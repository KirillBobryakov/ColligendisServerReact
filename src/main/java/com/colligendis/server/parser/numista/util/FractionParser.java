package com.colligendis.server.parser.numista.util;

public class FractionParser {
    // Маппинг Unicode-символов дробей к их числовым значениям
    private static Float getUnicodeFractionValue(char ch) {
        switch (ch) {
            case '½':
                return 0.5f; // 1/2
            case '⅓':
                return 1.0f / 3.0f; // 1/3
            case '⅔':
                return 2.0f / 3.0f; // 2/3
            case '¼':
                return 0.25f; // 1/4
            case '¾':
                return 0.75f; // 3/4
            case '⅕':
                return 0.2f; // 1/5
            case '⅖':
                return 0.4f; // 2/5
            case '⅗':
                return 0.6f; // 3/5
            case '⅘':
                return 0.8f; // 4/5
            case '⅙':
                return 1.0f / 6.0f; // 1/6
            case '⅚':
                return 5.0f / 6.0f; // 5/6
            case '⅛':
                return 0.125f; // 1/8
            case '⅜':
                return 0.375f; // 3/8
            case '⅝':
                return 0.625f; // 5/8
            case '⅞':
                return 0.875f; // 7/8
            case '↉':
                return 0.0f; // 0/3 (zero thirds)
            default:
                return null;
        }
    }

    public static Float parseToFloat(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String trimmedInput = input.trim();

        // 1. Один Unicode-символ дроби (½, ⅓, ⅝, ↉ и т.п.)
        if (trimmedInput.length() == 1) {
            char ch = trimmedInput.charAt(0);
            Float unicodeFraction = getUnicodeFractionValue(ch);
            if (unicodeFraction != null) {
                return unicodeFraction;
            }
            // Если это не Unicode дробь, пробуем обычное числовое значение
            int numeric = Character.getNumericValue(ch);
            if (numeric >= 0 && numeric <= 9) {
                return (float) numeric;
            }
        }

        // 2. Смешанное число: "2 ⅓"
        if (trimmedInput.matches("\\d+\\s+.")) {
            String[] parts = trimmedInput.split("\\s+");
            float whole = Float.parseFloat(parts[0]);
            char fractionChar = parts[1].charAt(0);

            Float fractionValue = getUnicodeFractionValue(fractionChar);
            if (fractionValue != null) {
                return whole + fractionValue;
            }
            // Fallback для обычных чисел
            int numeric = Character.getNumericValue(fractionChar);
            if (numeric >= 0 && numeric <= 9) {
                return whole + numeric;
            }
        }

        // 3. Смешанное число: "2 1/3"
        if (trimmedInput.matches("\\d+\\s+\\d+\\s*/\\s*\\d+")) {
            String[] parts = trimmedInput.split("\\s+");
            return Float.parseFloat(parts[0]) + parseFraction(parts[1]);
        }

        // 4. Обычная дробь: "1/2"
        if (trimmedInput.contains("/")) {
            return parseFraction(trimmedInput);
        }

        // 5. Десятичные числа (1.5 / 1,5)
        return Float.parseFloat(trimmedInput.replace(',', '.'));
    }

    private static float parseFraction(String value) {
        String[] parts = value.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Некорректная дробь: " + value);
        }
        return Float.parseFloat(parts[0].trim())
                / Float.parseFloat(parts[1].trim());
    }
}
