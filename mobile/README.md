# FlipBot Mobile

Mobilny klient Android/iOS dla panelu FlipBot. Aplikacja otwiera ten sam frontend, który działa na komputerze, więc wszystkie istniejące ekrany i akcje pozostają identyczne: Dashboard, Boty, Runtime, Oferty do kupienia, Historia, Cennik modeli, Słowniki oraz formularze tworzenia i edycji.

## Architektura

Telefon nie łączy się bezpośrednio z PostgreSQL ani Playwrightem.

```text
telefon (FlipBot Mobile)
        |
        | Wi-Fi / LAN, HTTP :5173
        v
frontend Vite na PC
        |
        | /api -> proxy
        v
Spring Boot na PC :8081
        |
        v
PostgreSQL + Playwright na PC
```

Dzięki temu backend może nadal działać pod `localhost:8081`; na sieć lokalną wystawiany jest tylko frontend Vite. Nie przekierowuj portu 5173 ani 8081 na routerze do Internetu.

## Uruchomienie na komputerze

1. Uruchom backend jak dotychczas na porcie 8081.
2. Uruchom Playwright jak dotychczas.
3. W osobnym terminalu uruchom frontend dostępny w LAN:

```powershell
cd frontend
npm install
npm run dev -- --host 0.0.0.0
```

Vite powinien pokazać m.in. adres `Network`, np. `http://192.168.1.37:5173/`.

Jeżeli Windows Firewall blokuje połączenie, zezwól Node.js na sieć prywatną albo uruchom PowerShell jako administrator i dodaj regułę tylko dla prywatnego profilu:

```powershell
New-NetFirewallRule -DisplayName "FlipBot frontend LAN" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 5173 -Profile Private
```

Adres IPv4 komputera sprawdzisz poleceniem:

```powershell
ipconfig
```

## Uruchomienie aplikacji na telefonie

Telefon i komputer muszą być w tej samej sieci Wi-Fi/LAN.

```powershell
cd mobile
npm install
npm start
```

Skrypt używa portu 8082 dla Expo, ponieważ backend FlipBot korzysta już z 8081. Otwórz projekt w Expo Go na telefonie. Przy pierwszym uruchomieniu wpisz adres frontendu z komputera, np.:

```text
http://192.168.1.37:5173
```

Nie wpisuj `localhost`: na telefonie `localhost` oznacza sam telefon, a nie komputer.

## Jak działa nawigacja

Linki wewnętrzne pozostają w aplikacji. Linki prowadzące poza serwer FlipBot (np. do Vinted) są otwierane w systemowej przeglądarce, dzięki czemu nie zastępują panelu wewnątrz aplikacji.

Aplikacja zapamiętuje wyłącznie adres frontendu PC. Nie zapisuje osobno loginów ani haseł botów. Dane i cała logika pozostają na komputerze.

## APK / development build

Projekt ma włączony `usesCleartextTraffic` dla Androida, ponieważ w sieci domowej panel działa po HTTP. Do instalowalnego development builda / APK można użyć standardowego Expo Prebuild/EAS. Dla dostępu spoza domu zamiast wystawiania portów użyj VPN do własnej sieci (np. WireGuard/Tailscale) i najlepiej HTTPS.
