from pathlib import Path

APP_PATH = Path(__file__).resolve().parents[1] / "App.tsx"

STATUS_BAR_OLD = '<StatusBar barStyle="dark-content" backgroundColor="#ffffff" translucent={false} />'
STATUS_BAR_FIXED = '<StatusBar barStyle="dark-content" backgroundColor="#ffffff" translucent />'

APP_ROOT_OLD = '''  appRoot: {
    flex: 1,
    backgroundColor: "#172033",
  },'''
APP_ROOT_FIXED = '''  appRoot: {
    flex: 1,
    paddingTop: Platform.OS === "android" ? StatusBar.currentHeight ?? 0 : 0,
    backgroundColor: "#ffffff",
  },'''

MODAL_ROOT_OLD = '''  modalRoot: {
    flex: 1,
    backgroundColor: "#f5f7fb",
  },'''
MODAL_ROOT_FIXED = '''  modalRoot: {
    flex: 1,
    paddingTop: Platform.OS === "android" ? StatusBar.currentHeight ?? 0 : 0,
    backgroundColor: "#ffffff",
  },'''

MODAL_BODY_OLD = '''  modalBody: {
    flex: 1,
    justifyContent: "center",'''
MODAL_BODY_FIXED = '''  modalBody: {
    flex: 1,
    justifyContent: "center",
    backgroundColor: "#f5f7fb",'''


def replace_required(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        print(f"{label} already patched")
        return source
    if old not in source:
        raise RuntimeError(f"Could not find expected {label} block in App.tsx")
    print(f"Patched {label}")
    return source.replace(old, new)


def main() -> None:
    source = APP_PATH.read_text(encoding="utf-8")

    # Android 15+ enforces edge-to-edge for modern target SDKs, so merely setting
    # StatusBar.backgroundColor is not enough. We intentionally make the status
    # bar translucent and reserve its exact native height ourselves. The root
    # behind that inset is solid white, therefore WebView content can never show
    # through behind the clock / signal / battery icons while scrolling.
    if STATUS_BAR_FIXED not in source:
        count = source.count(STATUS_BAR_OLD)
        if not count:
            raise RuntimeError("Could not find expected white StatusBar configuration in App.tsx")
        source = source.replace(STATUS_BAR_OLD, STATUS_BAR_FIXED)
        print(f"Made {count} StatusBar instance(s) use explicit edge-to-edge handling")
    else:
        print("StatusBar edge-to-edge handling already patched")

    source = replace_required(source, APP_ROOT_OLD, APP_ROOT_FIXED, "app root status-bar inset")
    source = replace_required(source, MODAL_ROOT_OLD, MODAL_ROOT_FIXED, "modal status-bar inset")
    source = replace_required(source, MODAL_BODY_OLD, MODAL_BODY_FIXED, "modal content background")

    APP_PATH.write_text(source, encoding="utf-8")


if __name__ == "__main__":
    main()
