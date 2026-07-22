package pl.flipbot.playwright.scanner.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class Listing {

    private String id;

    private String url;

    private String title;

    private BigDecimal price;

    private String condition;

    private String imageUrl;

    private Integer favoriteCount;

}