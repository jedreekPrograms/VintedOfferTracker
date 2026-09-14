interface ProductProvenance {
    additionalTargetId: number | null;
    productTargetLabel: string | null;
}

export function formatProductProvenance({
    additionalTargetId,
    productTargetLabel,
}: ProductProvenance): string {
    const scope = additionalTargetId === null
        ? "Produkt główny"
        : `Dodatkowy #${additionalTargetId}`;
    const label = productTargetLabel?.trim();

    return label === undefined || label.length === 0
        ? scope
        : `${scope} · ${label}`;
}
