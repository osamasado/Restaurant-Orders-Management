package org.restaurantordersmanagement.backend.seed;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The demo catalog read from {@code seed-images/catalog.json}: which raw materials exist (with unit, stock
 * and supplier), which recipe each meal size gets, and which image file belongs to which meal or raw material
 * (the file name is the slug). The same file drives the artwork in {@code backend/seed-art}.
 *
 * Meals are identified by their English name; the art brief ("look") in the file is ignored here.
 */
record SeedCatalog(List<MealEntry> meals, List<RawMaterialEntry> rawMaterials, List<RecipeEntry> recipes) {

    private static final String RESOURCE = "seed-images/catalog.json";

    record MealEntry(String slug, String name) {
    }

    record RawMaterialEntry(String slug, String name, String unit, String stock, String supplier) {
    }

    /**
     * The ingredients for the smallest size, and a factor per size (in price order, smallest first) to scale
     * every quantity for the bigger sizes.
     */
    record RecipeEntry(String meal, List<String> sizeFactors, List<IngredientEntry> ingredients) {
    }

    record IngredientEntry(String rawMaterial, String quantity) {
    }

    static SeedCatalog load() {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            return JsonMapper.builder()
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .build()
                    .readValue(in, SeedCatalog.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + RESOURCE, e);
        }
    }

}
