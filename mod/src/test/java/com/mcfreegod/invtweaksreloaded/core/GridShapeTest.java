package com.mcfreegod.invtweaksreloaded.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The grid detector decides whether the mod touches a container at all, so its
 * accept/reject behaviour is pinned down here.
 */
class GridShapeTest {
    private static List<int[]> grid(int columns, int rows) {
        List<int[]> positions = new ArrayList<>();
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < columns; x++) {
                positions.add(new int[]{8 + x * 18, 18 + y * 18});
            }
        }
        return positions;
    }

    @Test
    void chestGridIsRowMajor() {
        ContainerLayout.GridShape shape = ContainerLayout.GridShape.of(grid(9, 3));
        assertNotNull(shape);
        assertEquals(9, shape.rowSize());
        int[] expected = new int[27];
        for (int i = 0; i < 27; i++) {
            expected[i] = i;
        }
        assertArrayEquals(expected, shape.order());
    }

    @Test
    void unsortedInputIsNormalised() {
        List<int[]> positions = grid(9, 2);
        java.util.Collections.reverse(positions);
        ContainerLayout.GridShape shape = ContainerLayout.GridShape.of(positions);
        assertNotNull(shape);
        // Reversed row-major input must come back in reading order.
        int[] expected = new int[18];
        for (int i = 0; i < 18; i++) {
            expected[i] = 17 - i;
        }
        assertArrayEquals(expected, shape.order());
    }

    @Test
    void dispenserGridIsAccepted() {
        ContainerLayout.GridShape shape = ContainerLayout.GridShape.of(grid(3, 3));
        assertNotNull(shape);
        assertEquals(3, shape.rowSize());
    }

    @Test
    void singleColumnIsRejected() {
        // Furnaces and most machine screens: a vertical strip must not be
        // mistaken for a chest.
        assertNull(ContainerLayout.GridShape.of(grid(1, 3)));
    }

    @Test
    void singleSlotIsRejected() {
        assertNull(ContainerLayout.GridShape.of(List.of(new int[]{8, 18})));
    }

    @Test
    void raggedRowsAreRejected() {
        List<int[]> positions = grid(9, 2);
        positions.remove(positions.size() - 1);
        assertNull(ContainerLayout.GridShape.of(positions));
    }

    @Test
    void scatteredMachineSlotsAreRejected() {
        List<int[]> positions = List.of(
                new int[]{8, 18}, new int[]{26, 18}, new int[]{44, 18},
                new int[]{8, 54});
        assertNull(ContainerLayout.GridShape.of(positions));
    }

    @Test
    void subPixelRowOffsetIsTolerated() {
        List<int[]> positions = new ArrayList<>();
        for (int x = 0; x < 9; x++) {
            positions.add(new int[]{8 + x * 18, 18});
        }
        for (int x = 0; x < 9; x++) {
            // A modded screen that is off by one pixel still reads as one row.
            positions.add(new int[]{8 + x * 18, 36 + 1});
        }
        ContainerLayout.GridShape shape = ContainerLayout.GridShape.of(positions);
        assertNotNull(shape);
        assertEquals(9, shape.rowSize());
        assertEquals(18, shape.order().length);
    }

    @Test
    void emptyInputIsRejected() {
        assertNull(ContainerLayout.GridShape.of(List.of()));
    }
}
