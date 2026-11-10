"""Generates the mod icon.

The icon is a chest grid with the three classic sorting arrows, drawn on a
20x20 pixel grid at 1:1 and upscaled with NEAREST so the pixels stay crisp.
Everything is drawn from vanilla-ish palette values; no external asset is used.
"""
from PIL import Image

CELL = 20          # logical pixel grid
SCALE = 13         # 20 * 13 = 260, cropped to 256
OUT = 256

# Vanilla-ish palette
BG_DARK = (26, 26, 30, 255)
BG_MID = (44, 46, 54, 255)
FRAME = (120, 122, 132, 255)
FRAME_LIGHT = (168, 170, 182, 255)
SLOT = (66, 68, 78, 255)
ARROW = (232, 236, 244, 255)
ARROW_ACCENT = (110, 200, 130, 255)
ACCENT_DIM = (74, 132, 92, 255)


def draw():
    img = Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))
    px = img.load()

    # Rounded-ish dark panel with a frame.
    for y in range(CELL):
        for x in range(CELL):
            if x in (0, CELL - 1) or y in (0, CELL - 1):
                continue
            px[x, y] = BG_MID
    for i in range(1, CELL - 1):
        px[i, 0] = FRAME
        px[i, CELL - 1] = FRAME
        px[0, i] = FRAME
        px[CELL - 1, i] = FRAME
    for i in range(1, CELL - 1):
        px[i, 1] = FRAME_LIGHT
        px[1, i] = FRAME_LIGHT
    px[0, 0] = BG_DARK
    px[CELL - 1, 0] = BG_DARK
    px[0, CELL - 1] = BG_DARK
    px[CELL - 1, CELL - 1] = BG_DARK

    # 3x3 slot grid, the "chest" motif.
    for row in range(3):
        for col in range(3):
            x0 = 3 + col * 5
            y0 = 3 + row * 5
            for y in range(y0, y0 + 4):
                for x in range(x0, x0 + 4):
                    px[x, y] = SLOT
            px[x0, y0] = BG_DARK
            px[x0 + 3, y0] = BG_DARK
            px[x0, y0 + 3] = BG_DARK
            px[x0 + 3, y0 + 3] = BG_DARK

    # Three sorting symbols in the middle row. At 20x20 a "bar + arrow" glyph
    # turns into a blob, so the default symbol is a descending bar staircase,
    # which reads as "sort" even at 32px and still matches the button trio.
    for x in range(3, 7):
        px[x, 8] = ARROW
    for x in range(3, 6):
        px[x, 9] = ARROW
    for x in range(3, 5):
        px[x, 10] = ARROW

    # Vertical icon: two columns.
    for y in range(8, 12):
        px[9, y] = ARROW
        px[11, y] = ARROW

    # Horizontal icon: two rows.
    for x in range(14, 18):
        px[x, 8] = ARROW_ACCENT
        px[x, 10] = ARROW_ACCENT

    # A small accent dot keeps the icon readable at 32px.
    px[16, 12] = ACCENT_DIM
    px[15, 12] = ACCENT_DIM

    return img


def main():
    img = draw()
    big = img.resize((CELL * SCALE, CELL * SCALE), Image.NEAREST)
    # Crop to 256 with a 2px logical margin preserved on all sides.
    margin = (CELL * SCALE - OUT) // 2
    big = big.crop((margin, margin, margin + OUT, margin + OUT))
    big.save("icon.png")
    print("wrote icon.png", big.size)


if __name__ == "__main__":
    main()
