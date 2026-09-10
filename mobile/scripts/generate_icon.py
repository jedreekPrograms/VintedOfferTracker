from pathlib import Path

from PIL import Image, ImageDraw


SIZE = 1024
BACKGROUND = "#172033"
FOREGROUND = "#FFFFFF"
OUTPUT_DIR = Path(__file__).resolve().parents[1] / "assets"


def draw_flipbot_f(draw: ImageDraw.ImageDraw, color: str) -> None:
    # Keep the mark well inside Android's adaptive-icon safe zone.
    # Samsung launchers apply a fairly aggressive mask/crop, so the F is
    # intentionally smaller than the web-sidebar mark.
    draw.rounded_rectangle((340, 270, 450, 755), radius=24, fill=color)
    draw.rounded_rectangle((420, 270, 700, 380), radius=24, fill=color)
    draw.rounded_rectangle((420, 455, 645, 560), radius=22, fill=color)


def create_regular_icon() -> Image.Image:
    image = Image.new("RGBA", (SIZE, SIZE), BACKGROUND)
    draw = ImageDraw.Draw(image)

    draw.rounded_rectangle(
        (112, 112, 912, 912),
        radius=210,
        fill="#1C2940",
    )
    draw_flipbot_f(draw, FOREGROUND)
    return image


def create_adaptive_foreground() -> Image.Image:
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw_flipbot_f(draw, FOREGROUND)
    return image


def main() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    create_regular_icon().save(OUTPUT_DIR / "icon.png", optimize=True)
    create_adaptive_foreground().save(
        OUTPUT_DIR / "adaptive-icon.png",
        optimize=True,
    )


if __name__ == "__main__":
    main()
