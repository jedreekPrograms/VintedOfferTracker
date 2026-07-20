package pl.flipbot.playwright.filters;

import lombok.RequiredArgsConstructor;
import pl.flipbot.playwright.context.BotContext;

@RequiredArgsConstructor
public class FilterService {

    private final BotContext context;

    public void applyFilters() {

        applyCategory();

        applyBrand();

        applyModel();

        applyPrice();

    }

    private void applyCategory() {

    }

    private void applyBrand() {

    }

    private void applyModel() {

    }

    private void applyPrice() {

    }

}