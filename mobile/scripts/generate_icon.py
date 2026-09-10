from pathlib import Path

from PIL import Image, ImageDraw


SIZE = 1024
BACKGROUND = "#172033"
FOREGROUND = "#FFFFFF"
OUTPUT_DIR = Path(__file__).resolve().parents[1] / "assets"


def draw_flipbot_f(draw: ImageDraw.ImageDraw, color: str) -> None:
    # Block F matching the simple mark used by the web sidebar.
    draw.rounded_rectangle((300, 230, 430, 800), radius=28, fill=color)
    draw.rounded_rectangle((390, 230, 760, 360), radius=28, fill=color)
    draw.rounded_rectangle((390, 455, 675, 575), radius=26, fill=color)


def create_regular_icon() -> Image.Image:
    image = Image.new("RGBA", (SIZE, SIZE), BACKGROUND)
    draw = ImageDraw.Draw(image)

    # Subtle inner plate keeps the icon readable under different launcher masks.
    draw.rounded_rectangle(
        (92, 92, 932, 932),
        radius=220,
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
