package org.restaurantordersmanagement.backend.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(TestcontainersConfiguration.class)
class RawMaterialRepositoryTest {

    @Autowired
    private RawMaterialRepository rawMaterialRepository;

    @Test
    void savesAndFindsRawMaterial() {
        RawMaterial veal = new RawMaterial();
        veal.setName("Veal");
        veal.setUnit("g");
        veal.setInStockQuantity(new BigDecimal("5000.00"));
        veal.setSupplier("Local Butcher");

        RawMaterial saved = rawMaterialRepository.saveAndFlush(veal);

        Optional<RawMaterial> found = rawMaterialRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("Veal", found.get().getName());
        assertEquals("g", found.get().getUnit());
        assertEquals(0, new BigDecimal("5000.00").compareTo(found.get().getInStockQuantity()));
        assertEquals("Local Butcher", found.get().getSupplier());
    }

}
