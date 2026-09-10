import AsyncStorage from "@react-native-async-storage/async-storage";
import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  ActivityIndicator,
  BackHandler,
  KeyboardAvoidingView,
  Linking,
  Modal,
  Platform,
  Pressable,
  SafeAreaView,
  StatusBar,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";
import { WebView } from "react-native-webview";

const STORAGE_KEY = "flipbot.mobile.serverUrl";
const EXAMPLE_URL = "http://192.168.1.100:8081";

type ConnectionState = "idle" | "loading" | "online" | "offline";

function normalizeUrl(value: string): string {
  let normalized = value.trim();
  if (!normalized) return "";
  if (!/^https?:\/\//i.test(normalized)) normalized = `http://${normalized}`;
  return normalized.replace(/\/+$/, "");
}

function sameOrigin(left: string, right: string): boolean {
  try {
    return new URL(left).origin === new URL(right).origin;
  } catch {
    return false;
  }
}

export default function App() {
  const webViewRef = useRef<WebView>(null);
  const [serverUrl, setServerUrl] = useState("");
  const [draftUrl, setDraftUrl] = useState("");
  const [settingsVisible, setSettingsVisible] = useState(false);
  const [bootstrapping, setBootstrapping] = useState(true);
  const [connection, setConnection] = useState<ConnectionState>("idle");
  const [connectionMessage, setConnectionMessage] = useState<string | null>(null);
  const [canGoBack, setCanGoBack] = useState(false);
  const [pageTitle, setPageTitle] = useState("FlipBot");
  const [webViewKey, setWebViewKey] = useState(0);

  useEffect(() => {
    let active = true;
    void AsyncStorage.getItem(STORAGE_KEY)
      .then((savedUrl) => {
        if (!active) return;
        if (savedUrl) {
          const normalized = normalizeUrl(savedUrl);
          setServerUrl(normalized);
          setDraftUrl(normalized);
        } else {
          setDraftUrl(EXAMPLE_URL);
          setSettingsVisible(true);
        }
      })
      .finally(() => {
        if (active) setBootstrapping(false);
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    const subscription = BackHandler.addEventListener("hardwareBackPress", () => {
      if (settingsVisible) {
        if (serverUrl) {
          setSettingsVisible(false);
          return true;
        }
        return false;
      }
      if (canGoBack) {
        webViewRef.current?.goBack();
        return true;
      }
      return false;
    });
    return () => subscription.remove();
  }, [canGoBack, serverUrl, settingsVisible]);

  const testConnection = useCallback(async (candidate: string) => {
    const normalized = normalizeUrl(candidate);
    if (!normalized) {
      setConnection("offline");
      setConnectionMessage("Wpisz adres komputera z backendem FlipBot.");
      return false;
    }

    setConnection("loading");
    setConnectionMessage("Sprawdzam połączenie z komputerem...");
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 5000);

    try {
      const response = await fetch(normalized, { method: "GET", signal: controller.signal });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      setConnection("online");
      setConnectionMessage("Połączenie działa. Panel FlipBot jest dostępny z backendu.");
      return true;
    } catch {
      setConnection("offline");
      setConnectionMessage(
        `Brak połączenia z ${normalized}. Sprawdź IP komputera, backend na porcie 8081, Wi-Fi i Zaporę Windows.`,
      );
      return false;
    } finally {
      clearTimeout(timeout);
    }
  }, []);

  const saveServer = useCallback(async () => {
    const normalized = normalizeUrl(draftUrl);
    if (!normalized) {
      setConnection("offline");
      setConnectionMessage("Adres serwera nie może być pusty.");
      return;
    }
    if (!(await testConnection(normalized))) return;

    await AsyncStorage.setItem(STORAGE_KEY, normalized);
    setServerUrl(normalized);
    setDraftUrl(normalized);
    setSettingsVisible(false);
    setWebViewKey((current) => current + 1);
  }, [draftUrl, testConnection]);

  const statusLabel = useMemo(() => {
    if (connection === "online") return "PC online";
    if (connection === "offline") return "Brak PC";
    if (connection === "loading") return "Łączenie...";
    return serverUrl ? "Panel" : "Konfiguracja";
  }, [connection, serverUrl]);

  if (bootstrapping) {
    return (
      <SafeAreaView style={styles.safeArea}>
        <StatusBar barStyle="dark-content" backgroundColor="#ffffff" />
        <View style={styles.center}>
          <ActivityIndicator size="large" />
          <Text style={styles.bootText}>Uruchamianie FlipBot...</Text>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="dark-content" backgroundColor="#ffffff" />

      <View style={styles.toolbar}>
        <View style={styles.toolbarTitleWrap}>
          <Text style={styles.toolbarTitle} numberOfLines={1}>{pageTitle || "FlipBot"}</Text>
          <View style={styles.statusRow}>
            <View style={[
              styles.statusDot,
              connection === "online" && styles.statusDotOnline,
              connection === "offline" && styles.statusDotOffline,
            ]} />
            <Text style={styles.statusText}>{statusLabel}</Text>
          </View>
        </View>

        <Pressable style={styles.toolbarButton} onPress={() => webViewRef.current?.reload()}>
          <Text style={styles.toolbarButtonText}>↻</Text>
        </Pressable>
        <Pressable
          style={styles.toolbarButton}
          onPress={() => {
            setDraftUrl(serverUrl || EXAMPLE_URL);
            setConnectionMessage(null);
            setSettingsVisible(true);
          }}
        >
          <Text style={styles.toolbarButtonText}>⚙</Text>
        </Pressable>
      </View>

      {serverUrl ? (
        <WebView
          key={webViewKey}
          ref={webViewRef}
          source={{ uri: serverUrl }}
          style={styles.webView}
          originWhitelist={["http://*", "https://*"]}
          javaScriptEnabled
          domStorageEnabled
          cacheEnabled
          sharedCookiesEnabled
          thirdPartyCookiesEnabled
          mixedContentMode="always"
          setSupportMultipleWindows={false}
          allowsBackForwardNavigationGestures
          onLoadStart={() => {
            setConnection("loading");
            setConnectionMessage(null);
          }}
          onLoadEnd={() => setConnection("online")}
          onError={() => {
            setConnection("offline");
            setConnectionMessage("Nie udało się załadować panelu z komputera.");
          }}
          onHttpError={(event) => {
            setConnection("offline");
            setConnectionMessage(`Serwer odpowiedział HTTP ${event.nativeEvent.statusCode}.`);
          }}
          onNavigationStateChange={(state) => {
            setCanGoBack(state.canGoBack);
            if (state.title) setPageTitle(state.title);
          }}
          onShouldStartLoadWithRequest={(request) => {
            const target = request.url;
            if (
              target === "about:blank" ||
              target.startsWith("data:") ||
              target.startsWith("blob:") ||
              sameOrigin(target, serverUrl)
            ) return true;

            if (/^https?:\/\//i.test(target)) {
              void Linking.openURL(target);
              return false;
            }
            return true;
          }}
        />
      ) : (
        <View style={styles.center}>
          <Text style={styles.emptyTitle}>Połącz aplikację z komputerem</Text>
          <Text style={styles.emptyText}>
            Na PC uruchamiasz tylko to, co dotychczas: backend i Playwright. Backend udostępnia aplikacji ten sam panel webowy.
          </Text>
          <Pressable style={styles.primaryButton} onPress={() => setSettingsVisible(true)}>
            <Text style={styles.primaryButtonText}>Ustaw adres PC</Text>
          </Pressable>
        </View>
      )}

      {connection === "offline" && !settingsVisible && connectionMessage ? (
        <View style={styles.connectionBanner}>
          <Text style={styles.connectionBannerText}>{connectionMessage}</Text>
          <Pressable onPress={() => setSettingsVisible(true)}>
            <Text style={styles.connectionBannerAction}>Ustawienia</Text>
          </Pressable>
        </View>
      ) : null}

      <Modal
        visible={settingsVisible}
        animationType="slide"
        presentationStyle="pageSheet"
        onRequestClose={() => {
          if (serverUrl) setSettingsVisible(false);
        }}
      >
        <SafeAreaView style={styles.modalSafeArea}>
          <KeyboardAvoidingView style={styles.modalBody} behavior={Platform.OS === "ios" ? "padding" : undefined}>
            <View>
              <Text style={styles.modalEyebrow}>Połączenie lokalne</Text>
              <Text style={styles.modalTitle}>Komputer jako serwer</Text>
              <Text style={styles.modalText}>
                Wpisz IPv4 komputera z portem backendu 8081. Nie uruchamiasz osobno Vite ani żadnego nowego serwera.
              </Text>
            </View>

            <View style={styles.field}>
              <Text style={styles.label}>Adres FlipBot na PC</Text>
              <TextInput
                value={draftUrl}
                onChangeText={setDraftUrl}
                autoCapitalize="none"
                autoCorrect={false}
                keyboardType="url"
                placeholder={EXAMPLE_URL}
                style={styles.input}
              />
              <Text style={styles.help}>
                Przykład: http://192.168.1.37:8081. Nie wpisuj localhost, bo na telefonie oznacza sam telefon.
              </Text>
            </View>

            {connectionMessage ? (
              <View style={[styles.message, connection === "online" && styles.messageSuccess]}>
                <Text style={styles.messageText}>{connectionMessage}</Text>
              </View>
            ) : null}

            <View style={styles.modalActions}>
              <Pressable
                style={styles.secondaryButton}
                disabled={connection === "loading"}
                onPress={() => void testConnection(draftUrl)}
              >
                <Text style={styles.secondaryButtonText}>
                  {connection === "loading" ? "Sprawdzam..." : "Testuj"}
                </Text>
              </Pressable>
              <Pressable
                style={styles.primaryButton}
                disabled={connection === "loading"}
                onPress={() => void saveServer()}
              >
                <Text style={styles.primaryButtonText}>Zapisz i połącz</Text>
              </Pressable>
            </View>

            {serverUrl ? (
              <Pressable style={styles.closeButton} onPress={() => setSettingsVisible(false)}>
                <Text style={styles.closeButtonText}>Anuluj</Text>
              </Pressable>
            ) : null}
          </KeyboardAvoidingView>
        </SafeAreaView>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: "#ffffff" },
  modalSafeArea: { flex: 1, backgroundColor: "#f5f7fb" },
  webView: { flex: 1, backgroundColor: "#f5f7fb" },
  toolbar: {
    minHeight: 56,
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    paddingHorizontal: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: "#dfe5ef",
    backgroundColor: "#ffffff",
  },
  toolbarTitleWrap: { flex: 1, minWidth: 0 },
  toolbarTitle: { color: "#172033", fontSize: 16, fontWeight: "700" },
  statusRow: { flexDirection: "row", alignItems: "center", gap: 5, marginTop: 2 },
  statusDot: { width: 7, height: 7, borderRadius: 99, backgroundColor: "#94a3b8" },
  statusDotOnline: { backgroundColor: "#2f855a" },
  statusDotOffline: { backgroundColor: "#b42318" },
  statusText: { color: "#647089", fontSize: 11 },
  toolbarButton: {
    width: 42,
    height: 42,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: 10,
    backgroundColor: "#f1f4f8",
  },
  toolbarButtonText: { color: "#172033", fontSize: 21, fontWeight: "700" },
  center: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    padding: 28,
    backgroundColor: "#f5f7fb",
  },
  bootText: { marginTop: 14, color: "#647089", fontSize: 14 },
  emptyTitle: { color: "#172033", fontSize: 22, fontWeight: "800", textAlign: "center" },
  emptyText: {
    maxWidth: 360,
    marginTop: 10,
    marginBottom: 22,
    color: "#647089",
    fontSize: 14,
    lineHeight: 21,
    textAlign: "center",
  },
  connectionBanner: {
    position: "absolute",
    right: 12,
    bottom: 12,
    left: 12,
    borderRadius: 12,
    padding: 13,
    backgroundColor: "#fff1f0",
    elevation: 4,
  },
  connectionBannerText: { color: "#7a271a", fontSize: 12, lineHeight: 17 },
  connectionBannerAction: { marginTop: 8, color: "#172033", fontWeight: "800" },
  modalBody: { flex: 1, justifyContent: "center", gap: 24, padding: 24 },
  modalEyebrow: { marginBottom: 6, color: "#647089", fontSize: 12, fontWeight: "800", letterSpacing: 0.8 },
  modalTitle: { color: "#172033", fontSize: 28, fontWeight: "800" },
  modalText: { marginTop: 10, color: "#647089", fontSize: 14, lineHeight: 21 },
  field: { gap: 8 },
  label: { color: "#37445d", fontSize: 13, fontWeight: "700" },
  input: {
    minHeight: 50,
    borderWidth: 1,
    borderColor: "#cbd4e3",
    borderRadius: 12,
    paddingHorizontal: 14,
    color: "#172033",
    backgroundColor: "#ffffff",
    fontSize: 15,
  },
  help: { color: "#7d899f", fontSize: 12, lineHeight: 18 },
  message: { borderRadius: 12, padding: 13, backgroundColor: "#eef2f7" },
  messageSuccess: { backgroundColor: "#ecfdf3" },
  messageText: { color: "#37445d", fontSize: 12, lineHeight: 18 },
  modalActions: { flexDirection: "row", gap: 10 },
  primaryButton: {
    minHeight: 46,
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: 11,
    paddingHorizontal: 18,
    backgroundColor: "#172033",
  },
  primaryButtonText: { color: "#ffffff", fontSize: 14, fontWeight: "800" },
  secondaryButton: {
    minHeight: 46,
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1,
    borderColor: "#cbd4e3",
    borderRadius: 11,
    paddingHorizontal: 18,
    backgroundColor: "#ffffff",
  },
  secondaryButtonText: { color: "#172033", fontSize: 14, fontWeight: "800" },
  closeButton: { alignItems: "center", padding: 12 },
  closeButtonText: { color: "#647089", fontSize: 14, fontWeight: "700" },
});
