# Bezpieczne sprawdzanie rzeczywistych data-testid na Vinted

## Dlaczego prosimy o fragment DOM?

Publiczna strona Vinted jest dostępna przez narzędzie odczytu stron jako przetworzona treść, ale to narzędzie nie udostępnia pełnego dynamicznego DOM z atrybutami (i nie obsługuje zalogowanych modali). **Nie zakładamy**, że identyfikator z innej wersji interfejsu jest obecny dziś.

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
  const redacted = value => String(value || "")
    .replace(/\d{4,}/g, "#") // ukrywa numery ofert i inne długie identyfikatory
    .slice(0, 120);

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
  return { matchedTestIds: sorted.length, modelRows: modelItems.length };
})();
~~~

## Co sprawdzić dodatkowo, gdy model jest widoczny, ale nie ma --title

W DevTools **Elements** zaznacz konkretną opcję modelu (np. Galaxy S25). Prawy klik -> Copy -> Copy outerHTML, ale **tylko jednego publicznego wiersza filtra z jego etykietą**, bez formularza logowania lub całej strony. Wklej ten mały fragment do rozmowy. Wtedy można bez zgadywania zaprojektować locator obsługujący aktualny wariant.

## Priorytetowe fakty, które chcemy uzyskać

- Czy nazwa modelu jest w dokładnym wierszu z identyfikatorem kolekcji, czy dopiero w jego rodzicu?
- Czy wyszukiwarka ma jednoznaczny test ID na desktopie i w trybie mobilnym?
- Jakie identyfikatory mają przyciski wysyłania wiadomości, informacja o sprzedaży/usunięciu, komunikat o zbyt niskiej ofercie i walidacja logowania?
- Czy data „Dodane” znajduje się w istniejącym item-attributes-upload_date, czy jest przekazywana gdzieś indziej?
- Czy interfejs anonimowego obserwatora różni się od zalogowanego widoku?

**Nigdy nie zastępuj obecnych zabezpieczeń ofert/filtrów przypuszczalnym test ID.** Zanim zmienimy fallback, potrzebujemy potwierdzonego zrzutu DOM i testu regresji.
