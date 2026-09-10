# FlipBot Mobile

Mobilny klient Android/iOS dla panelu FlipBot. Aplikacja otwiera ten sam frontend React, który jest teraz pakowany razem z backendem Spring Boot. Dzięki temu na komputerze nie trzeba uruchamiać dodatkowego Vite ani żadnego nowego procesu.

## Architektura

```text
telefon (FlipBot Mobile)
        |
        | Wi-Fi / LAN, HTTP :8081
        v
Spring Boot na PC
        |
        +-- ten sam frontend React jako static resources
        +-- /api
        |
        v
PostgreSQL + Playwright na PC
```

Na PC uruchamiasz tylko to, co dotychczas:

1. backend Spring Boot na porcie 8081,
2. Playwright.

Frontend webowy jest zbudowany i dołączony do backendu na branchu mobilnym. Backend obsługuje zarówno panel, jak i istniejące `/api`, więc aplikacja widzi dokładnie te same ekrany i operacje: Dashboard, Runtime, Boty, tworzenie i edycję, Oferty do kupienia, Historię, Cennik modeli i Słowniki.

## Połączenie telefonu

Telefon i komputer muszą być w tej samej sieci Wi-Fi/LAN. IPv4 komputera sprawdzisz na Windows:

```powershell
ipconfig
```

W aplikacji wpisz adres w formacie:

```text
http://192.168.1.37:8081
```

Nie wpisuj `localhost`, ponieważ na telefonie oznacza on sam telefon.

Jeżeli Windows Firewall blokuje port 8081, zezwól Javie/IntelliJ na sieć prywatną albo dodaj regułę dla prywatnego profilu:

```powershell
New-NetFirewallRule -DisplayName "FlipBot backend LAN" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8081 -Profile Private
```

Nie przekierowuj portu 8081 na routerze do Internetu. Do dostępu spoza domu użyj prywatnego VPN, np. WireGuard/Tailscale.

## Development aplikacji

Do uruchomienia projektu mobilnego przez Expo Go podczas developmentu nadal potrzebny jest Metro tylko na czas developmentu:

```powershell
cd mobile
npm install
npm start
```

Docelowy instalowalny APK nie potrzebuje Metro ani Vite. W normalnym użyciu na PC działają tylko backend i Playwright.

## Nawigacja

Linki wewnętrzne pozostają wewnątrz aplikacji. Linki zewnętrzne, np. do Vinted, są otwierane w systemowej przeglądarce. Aplikacja zapamiętuje wyłącznie adres komputera; dane botów i cała logika pozostają na PC.
