package org.restaurantordersmanagement.backend.menu.web;

import java.math.BigDecimal;
import org.restaurantordersmanagement.backend.menu.model.Recipe;

/**
 * rawMaterialName/unit are denormalized from RawMaterial for display -
 * Recipe.rawMaterial is a @ManyToOne (EAGER by default), so this needs no
 * special session handling to read.
 */
public record RecipeLineDto(Long rawMaterialId, String rawMaterialName, String unit, BigDecimal quantity) {

    public static RecipeLineDto from(Recipe recipe) {
        return new RecipeLineDto(
                recipe.getRawMaterial().getId(),
                recipe.getRawMaterial().getName(),
                recipe.getRawMaterial().getUnit(),
                recipe.getQuantity());
    }

}
