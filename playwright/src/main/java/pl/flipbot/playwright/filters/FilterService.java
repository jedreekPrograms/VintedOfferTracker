package pl.flipbot.playwright.filters;

import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.model.BotDetailsDto;


public class FilterService {

    private final BotContext context;

    private final Page page;

    private final FilterActions actions;

    public FilterService(BotContext context) {

        this.context = context;

        this.page = context.getPage();

        this.actions = new FilterActions(page);

    }

    public void applyFilters(BotDetailsDto bot) {

        if (hasCategory(bot)) {
            applyCategory(bot);
        }

        if (hasBrand(bot)) {
            applyBrand(bot);
        }

        if (hasModel(bot)) {
            applyModel(bot);
        }

        if (hasPrice(bot)) {
            applyPrice(bot);
        }
    }

    private void applyCategory(BotDetailsDto bot) {

        actions.openFilter(
                FilterSelectors.CATEGORY_FILTER
        );

        var categoryPath =
                bot.getConfiguration().getCategoryPath();

        if (categoryPath == null || categoryPath.isEmpty()) {
            return;
        }

        for (int i = 0; i < categoryPath.size(); i++) {

            String category = categoryPath.get(i);

            actions.selectOption(category);

            if (i < categoryPath.size() - 1) {

                actions.waitForOption(
                        categoryPath.get(i + 1)
                );

            }

        }

    }

    private void applyBrand(BotDetailsDto bot) {

    }

    private void applyModel(BotDetailsDto bot) {

    }

    private void applyPrice(BotDetailsDto bot) {

    }

    private boolean hasCategory(BotDetailsDto bot) {

        return bot.getConfiguration().getCategoryPath() != null
                && !bot.getConfiguration().getCategoryPath().isEmpty();

    }

    private boolean hasBrand(BotDetailsDto bot) {

        return bot.getConfiguration().getBrand() != null
                && !bot.getConfiguration().getBrand().isBlank();

    }

    private boolean hasModel(BotDetailsDto bot) {

        return bot.getConfiguration().getModel() != null
                && !bot.getConfiguration().getModel().isBlank();

    }

    private boolean hasPrice(BotDetailsDto bot) {

        return bot.getConfiguration().getMinPrice() != null
                || bot.getConfiguration().getMaxPrice() != null;

    }

}