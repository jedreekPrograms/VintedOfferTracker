from pathlib import Path

APP_PATH = Path(__file__).resolve().parents[1] / "App.tsx"
MARKER = "  root.style.webkitTextSizeAdjust = '100%';\n"
STYLE_ID = "flipbot-mobile-app-styles"
STATUS_BAR_DARK = '<StatusBar barStyle="light-content" backgroundColor="#172033" translucent={false} />'
STATUS_BAR_LIGHT = '<StatusBar barStyle="dark-content" backgroundColor="#ffffff" translucent={false} />'

INJECTION = r'''

  const mobileStyleId = 'flipbot-mobile-app-styles';
  if (!document.getElementById(mobileStyleId)) {
    const mobileStyle = document.createElement('style');
    mobileStyle.id = mobileStyleId;
    mobileStyle.textContent = [
      'html.flipbot-mobile-app, html.flipbot-mobile-app body { width: 100%; min-width: 0 !important; overflow-x: hidden; }',
      'html.flipbot-mobile-app body { -webkit-font-smoothing: antialiased; }',
      'html.flipbot-mobile-app .app-layout, html.flipbot-mobile-app .page, html.flipbot-mobile-app .main-content { min-width: 0; }',
      'html.flipbot-mobile-app .flipbot-native-server-settings { width: 100%; border: 0; font-family: inherit; cursor: pointer; }',
      'html.flipbot-mobile-app img, html.flipbot-mobile-app video, html.flipbot-mobile-app canvas { max-width: 100%; height: auto; }',
      'html.flipbot-mobile-app .content-card, html.flipbot-mobile-app .stat-card, html.flipbot-mobile-app .empty-state { min-width: 0; max-width: 100%; }',
      'html.flipbot-mobile-app .content-card { overflow-x: auto; }',
      '@media (max-width: 760px) {',
      '  html.flipbot-mobile-app .sidebar { padding: 12px 10px 10px; }',
      '  html.flipbot-mobile-app .sidebar-header { gap: 8px; padding: 0 4px 10px; }',
      '  html.flipbot-mobile-app .sidebar-logo { width: 34px; height: 34px; border-radius: 10px; font-size: 17px; }',
      '  html.flipbot-mobile-app .sidebar-title { font-size: 15px; }',
      '  html.flipbot-mobile-app .sidebar-subtitle { margin-top: 1px; font-size: 10px; }',
      '  html.flipbot-mobile-app .sidebar-navigation { gap: 5px; padding-top: 10px; }',
      '  html.flipbot-mobile-app .navigation-link { min-width: 0; padding: 9px 8px; font-size: 12px; line-height: 1.25; text-align: center; overflow-wrap: anywhere; }',
      '  html.flipbot-mobile-app .sidebar-footer { margin-top: 10px; padding: 10px 12px; }',
      '  html.flipbot-mobile-app .sidebar-footer-title { margin-bottom: 4px; font-size: 10px; }',
      '  html.flipbot-mobile-app .sidebar-footer-text { font-size: 10.5px; line-height: 1.4; }',
      '  html.flipbot-mobile-app .main-content { padding: 16px 12px 24px; }',
      '  html.flipbot-mobile-app .page-header, html.flipbot-mobile-app .page-header-with-action, html.flipbot-mobile-app .bots-page-header, html.flipbot-mobile-app .action-required-page-header { gap: 12px; margin-bottom: 18px; }',
      '  html.flipbot-mobile-app .page-eyebrow { margin-bottom: 4px; font-size: 10px; }',
      '  html.flipbot-mobile-app .page-title { font-size: 26px; line-height: 1.12; }',
      '  html.flipbot-mobile-app .page-description { margin-top: 7px; font-size: 13px; line-height: 1.5; }',
      '  html.flipbot-mobile-app .stats-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-bottom: 16px; }',
      '  html.flipbot-mobile-app .stat-card { padding: 14px; border-radius: 13px; }',
      '  html.flipbot-mobile-app .stat-label { font-size: 11px; }',
      '  html.flipbot-mobile-app .stat-value { margin-top: 8px; font-size: 26px; }',
      '  html.flipbot-mobile-app .stat-description { margin-top: 6px; font-size: 10.5px; line-height: 1.4; }',
      '  html.flipbot-mobile-app .content-card { padding: 16px; border-radius: 13px; }',
      '  html.flipbot-mobile-app .content-card-title { margin-bottom: 9px; font-size: 17px; }',
      '  html.flipbot-mobile-app .content-card-text { font-size: 13px; line-height: 1.5; }',
      '  html.flipbot-mobile-app .empty-state { min-height: 230px; padding: 24px 16px; }',
      '  html.flipbot-mobile-app .empty-state-title { font-size: 19px; }',
      '  html.flipbot-mobile-app .empty-state-description { font-size: 13px; line-height: 1.5; }',
      '  html.flipbot-mobile-app .primary-button, html.flipbot-mobile-app .secondary-button { min-height: 42px; padding: 9px 13px; font-size: 13px; }',
      '  html.flipbot-mobile-app input, html.flipbot-mobile-app select, html.flipbot-mobile-app textarea { max-width: 100%; font-size: 16px; }',
      '  html.flipbot-mobile-app .bots-table-wrapper { max-width: 100%; padding-bottom: 3px; }',
      '  html.flipbot-mobile-app .bots-table { min-width: 610px; }',
      '  html.flipbot-mobile-app .bots-table th { padding: 8px 9px; font-size: 10px; }',
      '  html.flipbot-mobile-app .bots-table td { padding: 11px 9px; font-size: 12px; }',
      '  html.flipbot-mobile-app .bot-name-cell { min-width: 135px; }',
      '  html.flipbot-mobile-app .bot-name-cell strong { font-size: 13px; }',
      '  html.flipbot-mobile-app .bot-start-button, html.flipbot-mobile-app .bot-stop-button { min-width: 82px; padding: 8px 10px; font-size: 11px; }',
      '  html.flipbot-mobile-app .bot-form { gap: 14px; }',
      '  html.flipbot-mobile-app .bot-form-section-header { margin-bottom: 16px; }',
      '  html.flipbot-mobile-app .negotiation-step-card { padding: 14px; }',
      '  html.flipbot-mobile-app .negotiation-step-header { margin-bottom: 14px; }',
      '  html.flipbot-mobile-app .negotiation-summary { gap: 8px; margin-top: 16px; padding-top: 14px; }',
      '  html.flipbot-mobile-app .action-required-grid { grid-template-columns: minmax(0, 1fr); gap: 12px; margin-top: 16px; }',
      '  html.flipbot-mobile-app .action-required-card { min-width: 0; gap: 16px; padding: 16px; border-radius: 14px; }',
      '  html.flipbot-mobile-app .action-required-card-header { gap: 10px; }',
      '  html.flipbot-mobile-app .action-required-card-header h2 { font-size: 18px; }',
      '  html.flipbot-mobile-app .action-required-bot { padding: 6px 8px; font-size: 11px; }',
      '  html.flipbot-mobile-app .action-required-prices { gap: 8px; }',
      '  html.flipbot-mobile-app .action-required-prices > div { padding: 12px; }',
      '  html.flipbot-mobile-app .action-required-prices strong { font-size: 18px; }',
      '  html.flipbot-mobile-app .action-required-details { min-width: 0; }',
      '  html.flipbot-mobile-app .action-required-details dd { min-width: 0; overflow-wrap: anywhere; }',
      '  html.flipbot-mobile-app .action-required-login { padding: 14px; }',
      '  html.flipbot-mobile-app .action-required-credentials > div { grid-template-columns: 50px minmax(0, 1fr) auto auto; gap: 6px; padding: 7px 7px 7px 10px; }',
      '  html.flipbot-mobile-app .action-required-credentials .secondary-button { padding: 7px 8px; font-size: 11px; }',
      '}',
      '@media (max-width: 390px) {',
      '  html.flipbot-mobile-app .main-content { padding-right: 10px; padding-left: 10px; }',
      '  html.flipbot-mobile-app .page-title { font-size: 24px; }',
      '  html.flipbot-mobile-app .stats-grid { grid-template-columns: 1fr; }',
      '  html.flipbot-mobile-app .content-card, html.flipbot-mobile-app .stat-card { padding: 14px; }',
      '  html.flipbot-mobile-app .action-required-credentials > div { grid-template-columns: 1fr 1fr; }',
      '  html.flipbot-mobile-app .action-required-credentials span, html.flipbot-mobile-app .action-required-credentials strong { grid-column: 1 / -1; }',
      '  html.flipbot-mobile-app .action-required-credentials strong { white-space: normal; overflow-wrap: anywhere; }',
      '}',
    ].join('\\n');
    document.head.appendChild(mobileStyle);
  }
'''


def main() -> None:
    source = APP_PATH.read_text(encoding="utf-8")
    patched = source
    changed = False

    if STYLE_ID not in patched:
        if MARKER not in patched:
            raise RuntimeError("Could not find WebView text-size marker in App.tsx")
        patched = patched.replace(MARKER, MARKER + INJECTION, 1)
        changed = True
        print("Applied mobile-only responsive polish to App.tsx")
    else:
        print("Mobile UI polish already present")

    status_count = patched.count(STATUS_BAR_DARK)
    if status_count:
        patched = patched.replace(STATUS_BAR_DARK, STATUS_BAR_LIGHT)
        changed = True
        print(f"Switched {status_count} native status bar instance(s) to white with dark icons")
    elif STATUS_BAR_LIGHT in patched:
        print("Native status bar is already white with dark icons")
    else:
        raise RuntimeError("Could not find expected native StatusBar configuration in App.tsx")

    if changed:
        APP_PATH.write_text(patched, encoding="utf-8")


if __name__ == "__main__":
    main()
