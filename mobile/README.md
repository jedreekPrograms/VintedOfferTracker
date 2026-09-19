# FlipBot Mobile

Mobilny klient Android/iOS dla panelu FlipBot. Aplikacja otwiera ten sam frontend React, który jest pakowany razem z backendem Spring Boot. Dzięki temu na komputerze nie trzeba uruchamiać dodatkowego Vite ani żadnego nowego procesu FlipBot.

## Docelowy sposób działania: z dowolnego miejsca

Telefon nie musi być w tej samej sieci Wi-Fi co komputer. Do prywatnego dostępu z LTE/5G, innego miasta albo drugiego końca Polski używamy Tailscale Serve.

```text
telefon (FlipBot Mobile + Tailscale)
        |
        | Internet / prywatny tailnet / HTTPS
        v
Tailscale na PC
        |
        | reverse proxy -> localhost:8081
        v
Spring Boot na PC
        |
        +-- ten sam frontend React jako static resources
        +-- /api
        |
        v
PostgreSQL + Playwright na PC
```

Panel nie jest wystawiany publicznie do Internetu. Tailscale Serve udostępnia go wyłącznie urządzeniom uprawnionym w Twoim tailnecie. Nie przekierowuj portu 8081 na routerze i nie używaj Tailscale Funnel do tego panelu.

## Co działa na PC

W normalnym użyciu FlipBot uruchamiasz tak jak dotychczas:

1. backend Spring Boot na porcie 8081,
2. Playwright.

Tailscale działa jako usługa w tle systemu Windows. Nie trzeba uruchamiać Vite, Node ani osobnego serwera webowego.

## Jednorazowa konfiguracja zdalnego dostępu

1. Zainstaluj Tailscale na komputerze z Windows i zaloguj się.
2. Zainstaluj Tailscale na telefonie i zaloguj ten telefon do tego samego tailnetu.
3. Na PC, będąc na branchu mobilnym, uruchom PowerShell i wykonaj:

```powershell
powershell -ExecutionPolicy Bypass -File .\mobile\setup-remote-access.ps1
```

Skrypt konfiguruje prywatny reverse proxy Tailscale do istniejącego backendu:

```powershell
tailscale serve --bg http://127.0.0.1:8081
```

Po konfiguracji `tailscale serve status` pokaże adres HTTPS podobny do:

```text
https://twoj-komputer.twoj-tailnet.ts.net
```

Ten adres wpisujesz w aplikacji FlipBot Mobile. Jest stały dla tego urządzenia/tailnetu, więc nie musisz zmieniać go przy przełączaniu Wi-Fi, LTE ani podczas podróży.

Warunki działania są tylko dwa: komputer z FlipBotem musi być włączony i mieć Internet, a telefon musi mieć aktywne połączenie Tailscale.

## Aktualizacje aplikacji i błąd `domain undefined`

Standalone APK zawiera własny bundle JavaScript, więc zwykłe `git pull` na komputerze nie aktualizuje już zainstalowanej aplikacji na telefonie. Po zmianach w `mobile/App.tsx` trzeba zbudować i zainstalować nowszy APK albo uruchomić projekt przez Expo podczas developmentu.

Od wersji `1.0.1` aplikacja korzysta z kodu, który odrzuca zapisane hosty `undefined`, `null` i `nan`. Nieprawidłowy adres jest usuwany z AsyncStorage, a aplikacja otwiera ekran ustawień połączenia zamiast próbować ładować `http://undefined` i kończyć na `net::ERR_NAME_NOT_RESOLVED`.

Jeżeli telefon nadal pokazuje `domain undefined`, oznacza to, że uruchomiony jest starszy bundle APK. Najprostsza ścieżka naprawy to zainstalować aktualny build aplikacji. Doraźnie można też wyczyścić dane aplikacji lub ją odinstalować i zainstalować ponownie, a następnie wpisać prawidłowy adres `.ts.net` z `tailscale serve status`.

Samego Tailscale Serve nie trzeba konfigurować ponownie, jeśli jego adres HTTPS otwiera panel FlipBot w zwykłej przeglądarce telefonu przy aktywnym Tailscale.

## Alternatywa w domu

Aplikacja nadal akceptuje zwykły adres LAN, np.:

```text
http://192.168.1.37:8081
```

ale ten adres działa tylko w tej samej sieci. Do normalnego używania aplikacji poza domem zalecany jest adres HTTPS Tailscale.

## Development aplikacji

Do uruchomienia projektu mobilnego przez Expo Go podczas developmentu nadal potrzebny jest Metro tylko na czas developmentu:

```powershell
cd mobile
npm install
npm start
```

Docelowy instalowalny APK nie potrzebuje Metro ani Vite. W normalnym użyciu na PC działają backend, Playwright i Tailscale w tle.

## Nawigacja

Linki wewnętrzne pozostają wewnątrz aplikacji. Linki zewnętrzne, np. do Vinted, są otwierane w systemowej przeglądarce. Aplikacja zapamiętuje wyłącznie adres serwera; dane botów i cała logika pozostają na PC.
