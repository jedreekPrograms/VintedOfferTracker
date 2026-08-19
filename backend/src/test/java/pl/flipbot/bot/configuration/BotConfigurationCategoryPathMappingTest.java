package pl.flipbot.bot.configuration;

import jakarta.persistence.OrderColumn;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BotConfigurationCategoryPathMappingTest {

    @Test
    void categoryPathPersistsHierarchyOrderExplicitly() throws Exception {
        Field categoryPath = BotConfiguration.class.getDeclaredField("categoryPath");

        OrderColumn orderColumn = categoryPath.getAnnotation(OrderColumn.class);

        assertNotNull(
                orderColumn,
                "categoryPath must have an @OrderColumn because category hierarchy order is semantic"
        );
        assertEquals("path_index", orderColumn.name());
    }
}
