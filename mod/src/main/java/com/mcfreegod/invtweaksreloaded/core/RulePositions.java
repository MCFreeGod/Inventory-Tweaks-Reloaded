package com.mcfreegod.invtweaksreloaded.core;

import org.jetbrains.annotations.Nullable;

/** Position syntax shared by sorting rules and lock/freeze rules. */
public final class RulePositions {
    private RulePositions() {
    }

    @Nullable
    public static int[] parse(String constraint, int containerSize, int rowSize) {
        if (rowSize <= 0 || containerSize <= 0) {
            return null;
        }
        int columnSize = containerSize / rowSize;
        if (columnSize <= 0) {
            return null;
        }

        if (constraint.length() >= 5) {
            boolean vertical = constraint.indexOf('v') >= 0 || constraint.indexOf('V') >= 0;
            String cleaned = constraint.replace("v", "").replace("V", "");
            boolean reverse = cleaned.indexOf('r') >= 0 || cleaned.indexOf('R') >= 0;
            cleaned = cleaned.replace("r", "").replace("R", "");
            String[] elements = cleaned.split("-");
            if (elements.length != 2) {
                return null;
            }
            int[] first = parse(elements[0], containerSize, rowSize);
            int[] second = parse(elements[1], containerSize, rowSize);
            if (first == null || second == null || first.length != 1 || second.length != 1) {
                return null;
            }
            int[] a = coordinate(first[0], rowSize);
            int[] b = coordinate(second[0], rowSize);
            if (vertical) {
                int tmp = a[0];
                a[0] = a[1];
                a[1] = tmp;
                tmp = b[0];
                b[0] = b[1];
                b[1] = tmp;
            }
            int width = Math.abs(b[1] - a[1]) + 1;
            int height = Math.abs(b[0] - a[0]) + 1;
            int[] result = new int[width * height];
            int index = 0;
            for (int y = a[0]; a[0] < b[0] ? y <= b[0] : y >= b[0]; y += a[0] < b[0] ? 1 : -1) {
                for (int x = a[1]; a[1] < b[1] ? x <= b[1] : x >= b[1]; x += a[1] < b[1] ? 1 : -1) {
                    int slot = vertical ? index(rowSize, x, y) : index(rowSize, y, x);
                    if (slot >= 0 && slot < containerSize) {
                        result[index++] = slot;
                    }
                }
            }
            if (index != result.length) {
                int[] trimmed = new int[index];
                System.arraycopy(result, 0, trimmed, 0, index);
                result = trimmed;
            }
            if (reverse) {
                reverse(result);
            }
            return result;
        }

        // A dash only belongs to the rectangle form above; a leftover dash means
        // the constraint was truncated ("A1-") and must not silently become A1.
        if (constraint.indexOf('-') >= 0) {
            return null;
        }

        int column = -1;
        int row = -1;
        boolean reverse = false;
        for (int i = 0; i < constraint.length(); i++) {
            char c = constraint.charAt(i);
            int digit = Character.digit(c, 36);
            if (digit >= 1 && digit <= rowSize && digit < 10) {
                column = digit - 1;
                continue;
            }
            if (digit >= 10 && digit - 10 < columnSize) {
                row = digit - 10;
                continue;
            }
            if (c == 'r' || c == 'R') {
                reverse = true;
            }
        }

        if (column != -1 && row != -1) {
            return new int[]{index(rowSize, row, column)};
        }
        if (row != -1) {
            int[] result = new int[rowSize];
            for (int i = 0; i < rowSize; i++) {
                result[i] = index(rowSize, row, reverse ? rowSize - 1 - i : i);
            }
            return result;
        }
        if (column != -1) {
            int[] result = new int[columnSize];
            for (int i = 0; i < columnSize; i++) {
                result[i] = index(rowSize, reverse ? i : columnSize - 1 - i, column);
            }
            return result;
        }
        return null;
    }

    public static SortRule.RuleType type(String constraint, int rowSize) {
        String cleaned = constraint.replace("v", "").replace("V", "").replace("r", "").replace("R", "");
        if (cleaned.length() == 1) {
            char c = cleaned.charAt(0);
            int digit = Character.digit(c, 10);
            return digit >= 1 && digit <= rowSize ? SortRule.RuleType.COLUMN : SortRule.RuleType.ROW;
        }
        if (constraint.length() > 4) {
            if (constraint.length() > 4 && Character.toUpperCase(constraint.charAt(1)) == Character.toUpperCase(constraint.charAt(4))) {
                return SortRule.RuleType.COLUMN;
            }
            if (constraint.length() > 3 && Character.toUpperCase(constraint.charAt(0)) == Character.toUpperCase(constraint.charAt(3))) {
                return SortRule.RuleType.ROW;
            }
            return SortRule.RuleType.RECTANGLE;
        }
        return SortRule.RuleType.SLOT;
    }

    private static int[] coordinate(int slot, int rowSize) {
        return new int[]{slot / rowSize, slot % rowSize};
    }

    private static int index(int rowSize, int row, int column) {
        return row * rowSize + column;
    }

    private static void reverse(int[] data) {
        for (int left = 0, right = data.length - 1; left < right; left++, right--) {
            int tmp = data[left];
            data[left] = data[right];
            data[right] = tmp;
        }
    }
}