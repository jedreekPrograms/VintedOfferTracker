# Bezpieczne sprawdzanie rzeczywistych data-testid na Vinted

## Dlaczego prosimy o fragment DOM?

Publiczna strona Vinted jest dostępna przez narzędzie odczytu stron jako przetworzona treść, ale to narzędzie nie udostępnia pełnego dynamicznego DOM z atrybutami (i nie obsługuje zalogowanych modali). **Nie zakładamy**, że identyfikator z innej wersji interfejsu jest obecny dziś.

## Wynik z katalogu — 2026-10-09

Użytkownik uruchomił skrypt na stronie z zamkniętym filtrem modelu:
`matchedTestIds=51, modelRows=0`. Dostarczony HTML potwierdza
`catalog--brand_collection-filter--trigger` z `aria-expanded="false"`,
już zastosowany filtr modelu z `catalog--selected-filter-brand_collectionIds-9141`,
oraz **dwa** `input[data-testid="search-text--input"]` (wersje responsywne).
`modelRows=0` oznacza brak wierszy w DOM w tym momencie i **nie jest**
dowodem, że Vinted usunął `selectable-item-brand_collection-<id>` z interfejsu.
Otwórz **przycisk Model (trigger)** i wykonaj pomiar jeszcze raz.

Nie commituj pełnego HTML katalogu z zalogowanego konta; może zawierać
identyfikatory ofert, zdjęcia, dane profilu lub inne informacje prywatne.

## Potwierdzony rozwinięty filtr Galaxy S25 — 2026-10-10

Po otwarciu filtra Model i wpisaniu `Galaxy S25` użytkownik otrzymał
`{ znalezione: 15 }`, czyli **pięć opcji po trzy test ID**:

| Model widoczny | `data-testid` wiersza | `--title` | `--suffix` |
|---|---|---|---|
| Galaxy S25 | `selectable-item-brand_collection-9141` | tekst Galaxy S25 | brak tekstu |
| Galaxy S25 Edge | `selectable-item-brand_collection-9976` | tekst Galaxy S25 Edge | brak tekstu |
| Galaxy S25 FE | `selectable-item-brand_collection-9977` | tekst Galaxy S25 FE | brak tekstu |
| Galaxy S25 Ultra | `selectable-item-brand_collection-9142` | tekst Galaxy S25 Ultra | brak tekstu |
| Galaxy S25+ | `selectable-item-brand_collection-9143` | tekst Galaxy S25+ | brak tekstu |

Wszystkie wiersze są `DIV[role="button"]`, dzieci `--title` i `--suffix`
są `DIV`. Te publiczne identyfikatory kolekcji zapisano w testach
regresji, **nie** jako stałe mapowanie w produkcyjnym filtrze. Potwierdzona
postać DOM pozwoliła zawęzić `exposeModelLabels` do kanonicznych wierszy.
Obserwowany jest widok zalogowany; wariant anonimowego obserwatora, w którym
etykieta wiersza jest poza jego wnętrzem, nadal wymaga osobnego przechwycenia
małego, pozbawionego danych użytkownika fragmentu DOM. Nie usuwamy jego
fallbacku.

## Jak sprawdzić

1. Otwórz publiczny Vinted w Chrome/Edge, np. katalog z wyszukiwaniem modelu.
2. Naciśnij F12, wybierz kartę **Console**. Wklej poniższy kod i naciśnij Enter.
3. Skopiuj tabelę wynikową lub zrób jej zrzut. Kod niczego nie klika ani nie wysyła: tylko odczytuje **nazwy atrybutów data-testid**, nazwy tagów, role i typy elementów. **Nie pobiera** tekstu rozmów, pól input, haseł, sesji, ciasteczek, adresów URL ani danych użytkownika.
4. Powtórz osobno na: katalogu z otwartym filtrem modelu, zwykłym katalogu z wyszukiwarką, niezalogowanym modalu logowania, stronie dostępnego przedmiotu i (jeśli masz publiczny przykład) stronie sprzedanego/usuniętego przedmiotu. Nie wykonuj rzeczywistych ofert.
5. Prześlij tylko tabelę/kod z wynikiem; NIE udostępniaj pełnego HTML stron zalogowanych, cookies ani zrzutów panelu Network z tokenami.

### Kod do konsoli (tylko odczyt)

~~~javascript
(() => {
  const wanted = /(catalog|filter|brand|collection|price|search|login|auth|error|message|send|offer|item-attributes|sold|unblock|status|consent|modal|dialog|composer)/i;
  // Collection IDs are public model taxonomy IDs, needed to match exact rows.
  // Redact unrelated listing IDs and long identifiers.
  const redacted = value => {
    const text = String(value || "");
    if (/^(selectable-item-brand_collection-\d+(?:--(?:title|suffix))?|catalog--selected-filter-brand_collectionIds-\d+(?:--(?:text|suffix))?)$/.test(text)) {
      return text.slice(0, 120);
    }
    return text.replace(/\d{4,}/g, "#").slice(0, 120);
  };

  const results = new Map();
  for (const element of document.querySelectorAll("[data-testid]")) {
    const id = element.getAttribute("data-testid") || "";
    if (!wanted.test(id)) continue;
    const entry = {
      testId: redacted(id),
      tag: element.tagName.toLowerCase(),
      role: element.getAttribute("role") || "",
      type: element.getAttribute("type") || "",
      visible: !!element.getClientRects().length
    };
    const key = JSON.stringify(entry);
    const previous = results.get(key);
    if (previous) previous.count++;
    else results.set(key, { ...entry, count: 1 });
  }

  const sorted = [...results.values()].sort((a, b) =>
    a.testId.localeCompare(b.testId)
  );
  console.table(sorted);

  const modelItems = [...document.querySelectorAll(
    '[data-testid^="selectable-item-brand_collection-"]'
  )].filter(el => /^selectable-item-brand_collection-\d+$/.test(
    el.getAttribute("data-testid") || ""
  ));
  const modelSummary = modelItems.slice(0, 20).map(row => ({
    rowId: redacted(row.getAttribute("data-testid")),
    role: row.getAttribute("role") || "",
    hasTitleTestId: !!row.querySelector(
      '[data-testid$="--title"]'
    ),
    hasSuffixTestId: !!row.querySelector(
      '[data-testid$="--suffix"]'
    ),
    hasCheckbox: !!row.querySelector(
      'input[type="checkbox"], [role="checkbox"]'
    )
  }));
  console.table(modelSummary);
  const modelTrigger = document.querySelector(
    '[data-testid="catalog--brand_collection-filter--trigger"]'
  );
  const modelFilterOpen = modelTrigger?.getAttribute("aria-expanded");
  if (modelItems.length === 0) {
    console.info("Brak otwartych opcji modelu. Model trigger aria-expanded:", modelFilterOpen);
  }
  return { matchedTestIds: sorted.length, modelRows: modelItems.length,
           modelFilterOpen };
})();
~~~

## Co sprawdzić dodatkowo, gdy model jest widoczny, ale nie ma --title

W DevTools **Elements** zaznacz konkretną opcję modelu (np. Galaxy S25). Prawy klik -> Copy -> Copy outerHTML, ale **tylko jednego publicznego wiersza filtra z jego etykietą**, bez formularza logowania lub całej strony. Wklej ten mały fragment do rozmowy. Wtedy można bez zgadywania zaprojektować locator obsługujący aktualny wariant.

## Następny pomiar: rozwinięta lista marek (Samsung)

Po potwierdzeniu pięciu opcji S25 następnym miejscem do zamiany
`getByRole(BUTTON, name=option)` na dokładny `data-testid` jest
`FilterActions.getOptionLocator`, używany do wyboru **marki/kategorii**.
Na katalogu otwórz **Marka**, wpisz `Samsung`, zostaw otwartą listę i
uruchom poniższy **odczytowy** kod. Nie wybieraj marki ani nie zmieniaj filtrów.

~~~javascript
(() => {
  const nodes = [...document.querySelectorAll("[data-testid]")];
  const rows = nodes
    .filter(el => /^(selectable-item-|catalog--brand-filter|filter-selection-button)/i
      .test(el.getAttribute("data-testid") || ""))
    .map(el => ({
      testId: (el.getAttribute("data-testid") || "")
        .replace(/\d{6,}/g, "#"), // zachowuje krótkie ID taksonomii
      tag: el.tagName,
      role: el.getAttribute("role"),
      label: (el.innerText || el.getAttribute("aria-label") || "")
        .replace(/\s+/g, " ").trim().slice(0, 80)
    }));
  console.table(rows);
  copy(JSON.stringify(rows, null, 2));
  return { matched: rows.length };
})();
~~~

Wklej tutaj wynik ze schowka. Ponieważ filtr marek wyświetla publiczne
nazwy marek, ten pomiar nie wymaga tekstu prywatnych rozmów. Nie wysyłaj
całego DOM strony ani zrzutu HTML zalogowanego konta.

## Potwierdzony otwarty filtr marek — 2026-10-10

Po wyszukaniu „Samsung” użytkownik przesłał dokładne dane 10 wyników
po trzy elementy na markę (wiersz / \`--title\` / \`--suffix\`).
Każdy wiersz jest \`DIV[role="button"]\`, etykieta nazwy jest w
\`selectable-item-brand-<id>--title\`, a \`--suffix\` nie zawiera tekstu.

| Marka | Zweryfikowane ID |
| --- | --- |
| Samsung | \`109048\` |
| Samsonite | \`26963\` |
| Sass & Belle | \`482197\` |
| SAM & JO | \`1034997\` |
| Samson | \`191894\` |
| Sass & Bide | \`186294\` |
| Sam & Libby | \`274001\` |
| Disney x Samsonite | \`7136871\` |
| Sam & Lili | \`109246\` |
| Sass & Me | \`4892497\` |

Na tej podstawie zaimplementowano resolver \`VintedBrandOptionResolver\`
wyłącznie dla aktywnego filtra Marki, nie dla kategorii. ID nie są
zaszywane w logice produkcyjnej. Dobór kategorii pozostaje do zbadania.

## Kolejny opcjonalny pomiar: kategorie

Na katalogu kliknij „Kategoria” (otwórz panel), ale **nie zatwierdzaj
zmiany filtra**. Wklej:

~~~javascript
(() => {
  const rows = [...document.querySelectorAll("[data-testid]")]
    .filter(el => /^(selectable-item-|catalog--catalog-filter|filter-selection-button)/i
      .test(el.getAttribute("data-testid") || ""))
    .map(el => ({
      testId: el.getAttribute("data-testid"),
      tag: el.tagName.toLowerCase(),
      role: el.getAttribute("role") || "",
      text: (el.innerText || el.getAttribute("aria-label") || "")
        .trim().replace(/\s+/g, " ").slice(0, 80)
    }));
  console.table(rows);
  copy(JSON.stringify(rows, null, 2));
  return { entries: rows.length };
})();
~~~

Prześlij skopiowaną listę, bez cookies, sesji i pełnego HTML.
Jeżeli kategorie mają własne \`data-testid\` i zachowują się stabilnie,
będzie można ograniczyć \`getByRole\` również w \`CategoryNavigator\`.

## Potwierdzony HTML kategorii — 2026-10-10

W przeciwieństwie do marek i modeli **wiersze kategorii nie mają własnych
`data-testid` w przechwyconym widoku**. Są to `DIV` o `role="button"`
oraz natywnych ID `catalog_ids-list-item-<ID>`:

| Kategoria | ID wiersza w filtrze |
| --- | --- |
| Kobiety | `catalog_ids-list-item-1904` |
| Mężczyźni | `catalog_ids-list-item-5` |
| Przedmioty designerskie | `catalog_ids-list-item-2993` |
| Dzieci | `catalog_ids-list-item-1193` |
| Dom | `catalog_ids-list-item-1918` |
| Elektronika | `catalog_ids-list-item-2994` |
| Książki i multimedia | `catalog_ids-list-item-2309` |
| Hobby i kolekcjonerstwo | `catalog_ids-list-item-4824` |
| Sport | `catalog_ids-list-item-4332` |

**Korekta dotycząca „Elektroniki”:** pierwszy skrypt szukający nazwy
na całej stronie znalazł `A` w `LI` nawigacji *breadcrumbs*, a nie wiersz
filtra. **Drugi niezależny odczyt samych wierszy kategorii potwierdził
`catalog_ids-list-item-2994` dla Elektroniki.** Wszystkie powyższe dane
pochodzą z poziomu kategorii głównych; nie są jeszcze dowodem na strukturę
podkategorii. Nazw i ID nie kodujemy na stałe w produkcyjnych selektorach.

Nowy `VintedCategoryOptionResolver` używa `id^="catalog_ids-list-item-"`
z dokładnym dopasowaniem widocznej etykiety, a następnie dopuszcza awaryjne
`getByRole(BUTTON)` z zakotwiczoną dokładną nazwą. **Nie wymyślamy test ID**
i nie kodujemy na stałe numerów kategorii.

### Opcjonalnie: sprawdzenie kolejnego poziomu kategorii

Jeśli chcesz, rozwiń w filtrze **Elektronika → Telefony komórkowe i komunikacja
→ Telefony komórkowe** i uruchom poniższy odczytowy kod przy każdym poziomie.
Nie klikaj „Pokaż wyniki”. Wynik pokaże, czy identyfikatory pozostają w tej
samej strukturze również dla podkategorii.

~~~javascript
(() => {
  const rows = [...document.querySelectorAll(
    '[id^="catalog_ids-list-item-"][role="button"]'
  )].filter(el => el.getClientRects().length > 0)
    .map(el => ({
      id: el.id,
      role: el.getAttribute("role"),
      name: (el.innerText || "").replace(/\s+/g, " ").trim().slice(0, 90)
    }));
  const result = JSON.stringify(rows, null, 2);
  copy(result);
  console.log(result);
  return { visibleCategoryRows: rows.length };
})();
~~~

## Priorytetowe fakty, które chcemy uzyskać

- Czy nazwa modelu jest w dokładnym wierszu z identyfikatorem kolekcji, czy dopiero w jego rodzicu?
- Czy wyszukiwarka ma jednoznaczny test ID na desktopie i w trybie mobilnym?
- Jakie identyfikatory mają przyciski wysyłania wiadomości, informacja o sprzedaży/usunięciu, komunikat o zbyt niskiej ofercie i walidacja logowania?
- Czy data „Dodane” znajduje się w istniejącym item-attributes-upload_date, czy jest przekazywana gdzieś indziej?
- Czy interfejs anonimowego obserwatora różni się od zalogowanego widoku?

**Nigdy nie zastępuj obecnych zabezpieczeń ofert/filtrów przypuszczalnym test ID.** Zanim zmienimy fallback, potrzebujemy potwierdzonego zrzutu DOM i testu regresji.
