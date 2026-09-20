package org.restaurantordersmanagement.backend.menu.web;

import java.math.BigDecimal;
import org.restaurantordersmanagement.backend.menu.model.RawMaterial;

public record RawMaterialResponse(
        Long id, String name, String unit, BigDecimal inStockQuantity, String supplier, String imageUrl) {

    public static RawMaterialResponse from(RawMaterial rawMaterial) {
        String imageUrl = rawMaterial.getImagePath() != null ? "/images/" + rawMaterial.getImagePath() : null;
        return new RawMaterialResponse(
                rawMaterial.getId(),
                rawMaterial.getName(),
                rawMaterial.getUnit(),
                rawMaterial.getInStockQuantity(),
                rawMaterial.getSupplier(),
                imageUrl);
    }

}
