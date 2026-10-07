package org.restaurantordersmanagement.backend.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.restaurantordersmanagement.backend.TestcontainersConfiguration;
import org.restaurantordersmanagement.backend.guest.service.GuestDeviceService;
import org.restaurantordersmanagement.backend.i18n.Language;
import org.restaurantordersmanagement.backend.menu.model.Meal;
import org.restaurantordersmanagement.backend.menu.model.MealSize;
import org.restaurantordersmanagement.backend.menu.model.MealTranslation;
import org.restaurantordersmanagement.backend.menu.model.RawMaterial;
import org.restaurantordersmanagement.backend.menu.model.Recipe;
import org.restaurantordersmanagement.backend.menu.repository.CategoryRepository;
import org.restaurantordersmanagement.backend.menu.repository.MealRepository;
import org.restaurantordersmanagement.backend.menu.repository.RawMaterialRepository;
import org.restaurantordersmanagement.backend.menu.repository.RecipeRepository;
import org.restaurantordersmanagement.backend.staff.repository.StaffAccountRepository;
import org.restaurantordersmanagement.backend.table.model.Table;
import org.restaurantordersmanagement.backend.table.repository.TableRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Transactional so the seeded rows are rolled back afterwards - the test
 * DB is shared across test classes, and leftover demo tables ("1", "2",
 * "9", ...) collided with tables other tests create, depending on run order.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class DemoDataSeederTest {

    @Autowired
    private DemoDataSeeder demoDataSeeder;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MealRepository mealRepository;

    @Autowired
    private RawMaterialRepository rawMaterialRepository;

    @Autowired
    private RecipeRepository recipeRepository;

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Autowired
    private TableRepository tableRepository;

    @Autowired
    private StaffAccountRepository staffAccountRepository;

    @Autowired
    private GuestDeviceService guestDeviceService;

    @Test
    void seedAllProducesExpectedCountsAndIsIdempotent() {
        demoDataSeeder.seedAll();

        assertCounts();

        // Running it again must not duplicate anything.
        demoDataSeeder.seedAll();

        assertCounts();
    }

    @Test
    void everyMealHasFullContentInAllThreeLanguages() {
        demoDataSeeder.seedAll();

        for (Meal meal : mealRepository.findAll()) {
            int ingredientCount = translation(meal, Language.EN).getIngredients().size();
            for (Language language : Language.values()) {
                MealTranslation translation = translation(meal, language);
                String label = translation.getName() + " / " + language;
                assertFalse(translation.getName().isBlank(), label + " name");
                assertFalse(translation.getDescription() == null || translation.getDescription().isBlank(), label + " description");
                assertFalse(translation.getPreparationMethod() == null || translation.getPreparationMethod().isBlank(), label + " preparation");
                assertEquals(ingredientCount, translation.getIngredients().size(), label + " ingredient count");
            }
            for (MealSize size : meal.getSizes()) {
                assertEquals(Language.values().length, size.getTranslations().size());
                String englishLabel = sizeLabel(size, Language.EN);
                assertFalse(sizeLabel(size, Language.AR).equals(englishLabel), "Arabic size label for " + englishLabel);
            }
        }
    }

    @Test
    void fillsEmptyGermanAndArabicContentOnAnAlreadySeededMenuWithoutOverwritingEdits() {
        demoDataSeeder.seedAll();
        List<Meal> meals = mealRepository.findAll();
        Meal emptied = meals.get(0);
        Meal edited = meals.get(1);

        // A menu seeded before DE/AR content existed: names only.
        for (Language language : List.of(Language.DE, Language.AR)) {
            MealTranslation translation = translation(emptied, language);
            translation.setDescription(null);
            translation.setPreparationMethod(null);
            translation.setIngredients(new ArrayList<>());
        }
        // An admin has already written their own German description.
        translation(edited, Language.DE).setDescription("Eigener Text vom Admin");
        // Arabic size labels that were never translated (still the English text), and one an admin changed.
        for (MealSize size : emptied.getSizes()) {
            size.getTranslations().stream()
                    .filter(translation -> translation.getLanguage() == Language.AR)
                    .forEach(translation -> translation.setLabel(sizeLabel(size, Language.EN)));
        }
        MealSize editedSize = edited.getSizes().get(0);
        editedSize.getTranslations().stream()
                .filter(translation -> translation.getLanguage() == Language.AR)
                .forEach(translation -> translation.setLabel("حجم من المدير"));
        mealRepository.saveAllAndFlush(List.of(emptied, edited));

        demoDataSeeder.seedAll();

        for (Language language : List.of(Language.DE, Language.AR)) {
            MealTranslation refilled = translation(emptied, language);
            assertFalse(refilled.getDescription() == null || refilled.getDescription().isBlank(), language + " description");
            assertFalse(refilled.getIngredients().isEmpty(), language + " ingredients");
        }
        assertEquals("Eigener Text vom Admin", translation(edited, Language.DE).getDescription());
        for (MealSize size : emptied.getSizes()) {
            assertFalse(sizeLabel(size, Language.AR).equals(sizeLabel(size, Language.EN)), "refilled Arabic size label");
        }
        assertEquals("حجم من المدير", sizeLabel(editedSize, Language.AR));
        assertTrue(mealRepository.count() == 8);
    }

    @Test
    void everySeededPairingCodeCanBeTypedAndClaimedOnAGuestDevice() {
        demoDataSeeder.seedAll();

        List<Table> paired = tableRepository.findAll().stream()
                .filter(table -> table.getPairedDeviceId() != null)
                .toList();
        assertFalse(paired.isEmpty());
        for (Table table : paired) {
            String code = table.getPairedDeviceId();
            // What the device screen accepts: six characters, upper case.
            assertTrue(code.matches("[A-Z0-9]{6}"), "table " + table.getTableNumber() + " has an untypeable code: " + code);
            assertEquals(table.getTableNumber(), guestDeviceService.claim(code).getTableNumber());
        }
    }

    @Test
    void legacyDeviceIdsAreReplacedButACodeAnAdminGeneratedIsNot() {
        demoDataSeeder.seedAll();
        Table first = tableRepository.findByTableNumber("1").orElseThrow();
        Table second = tableRepository.findByTableNumber("2").orElseThrow();
        first.setPairedDeviceId("tablet-a1");
        second.setPairedDeviceId("ADMN42");
        tableRepository.saveAllAndFlush(List.of(first, second));

        demoDataSeeder.seedAll();

        assertEquals("ALPHA2", tableRepository.findByTableNumber("1").orElseThrow().getPairedDeviceId());
        assertEquals("ADMN42", tableRepository.findByTableNumber("2").orElseThrow().getPairedDeviceId());
    }

    @Test
    void theCatalogIsConsistentAndEveryImageFileExistsWithTheRightSize() throws IOException {
        SeedCatalog catalog = SeedCatalog.load();
        Set<String> rawSlugs = catalog.rawMaterials().stream().map(SeedCatalog.RawMaterialEntry::slug).collect(Collectors.toSet());
        Set<String> used = catalog.recipes().stream()
                .flatMap(recipe -> recipe.ingredients().stream())
                .map(SeedCatalog.IngredientEntry::rawMaterial)
                .collect(Collectors.toSet());

        assertEquals(31, rawSlugs.size(), "raw material slugs are unique");
        assertTrue(rawSlugs.containsAll(used), "every ingredient of a recipe is a catalog raw material");
        assertEquals(rawSlugs, used, "every raw material is used by some recipe");
        assertEquals(
                catalog.meals().stream().map(SeedCatalog.MealEntry::slug).collect(Collectors.toSet()),
                catalog.recipes().stream().map(SeedCatalog.RecipeEntry::meal).collect(Collectors.toSet()),
                "every meal has a recipe");

        for (SeedCatalog.MealEntry meal : catalog.meals()) {
            assertPng("seed-images/meals/" + meal.slug() + ".png", 800, 600);
        }
        for (SeedCatalog.RawMaterialEntry rawMaterial : catalog.rawMaterials()) {
            assertPng("seed-images/raw-materials/" + rawMaterial.slug() + ".png", 400, 400);
        }
    }

    @Test
    void seedsRawMaterialsWithUnitStockAndSupplierWithoutDuplicatingThem() {
        demoDataSeeder.seedAll();
        SeedCatalog catalog = SeedCatalog.load();

        Map<String, RawMaterial> byName = rawMaterialRepository.findAll().stream()
                .collect(Collectors.toMap(RawMaterial::getName, material -> material, (a, b) -> a));
        for (SeedCatalog.RawMaterialEntry entry : catalog.rawMaterials()) {
            RawMaterial rawMaterial = byName.get(entry.name());
            assertNotNull(rawMaterial, entry.name() + " is seeded");
            assertEquals(entry.unit(), rawMaterial.getUnit());
            assertEquals(0, new BigDecimal(entry.stock()).compareTo(rawMaterial.getInStockQuantity()), entry.name() + " stock");
            assertEquals(entry.supplier(), rawMaterial.getSupplier());
        }
        long materials = rawMaterialRepository.count();
        long recipes = recipeRepository.count();

        demoDataSeeder.seedAll();

        assertEquals(materials, rawMaterialRepository.count(), "a second seed adds no raw materials");
        assertEquals(recipes, recipeRepository.count(), "a second seed adds no recipes");
    }

    @Test
    void everyMealSizeGetsARecipeAndBiggerSizesNeedMore() {
        demoDataSeeder.seedAll();

        for (Meal meal : mealRepository.findAll()) {
            List<MealSize> sizes = meal.getSizes().stream()
                    .sorted((a, b) -> a.getPrice().compareTo(b.getPrice()))
                    .toList();
            Map<Long, BigDecimal> previous = null;
            for (MealSize size : sizes) {
                List<Recipe> recipe = recipeRepository.findByMealSizeId(size.getId());
                String label = translation(meal, Language.EN).getName() + " / " + sizeLabel(size, Language.EN);
                assertFalse(recipe.isEmpty(), label + " has a recipe");
                assertTrue(recipe.stream().allMatch(line -> line.getQuantity().signum() > 0), label + " quantities are positive");
                Map<Long, BigDecimal> quantities = recipe.stream()
                        .collect(Collectors.toMap(line -> line.getRawMaterial().getId(), Recipe::getQuantity));
                if (previous != null) {
                    Map<Long, BigDecimal> smaller = previous;
                    assertEquals(smaller.keySet(), quantities.keySet(), label + " uses the same raw materials as the smaller size");
                    quantities.forEach((material, quantity) ->
                            assertTrue(quantity.compareTo(smaller.get(material)) > 0, label + " needs more than the smaller size"));
                }
                previous = quantities;
            }
        }
    }

    @Test
    void everyMealAndRawMaterialGetsItsImageAsAValidFileInTheUploadFolder() throws IOException {
        demoDataSeeder.seedAll();

        for (Meal meal : mealRepository.findAll()) {
            assertUploadedPng(meal.getImagePath(), translation(meal, Language.EN).getName());
        }
        for (RawMaterial rawMaterial : rawMaterialRepository.findAll()) {
            if (SeedCatalog.load().rawMaterials().stream().anyMatch(entry -> entry.name().equals(rawMaterial.getName()))) {
                assertUploadedPng(rawMaterial.getImagePath(), rawMaterial.getName());
            }
        }
    }

    @Test
    void anImageOrRecipeAnAdminChangedIsLeftAloneOnTheNextSeed() {
        demoDataSeeder.seedAll();
        Meal meal = mealRepository.findAll().get(0);
        meal.setImagePath("meals/admins-own-photo.jpg");
        MealSize size = meal.getSizes().get(0);
        List<Recipe> recipe = recipeRepository.findByMealSizeId(size.getId());
        Recipe edited = recipe.get(0);
        edited.setQuantity(new BigDecimal("777.00"));
        recipeRepository.saveAndFlush(edited);
        // The admin also removed one ingredient of that size's recipe.
        recipeRepository.delete(recipe.get(1));
        mealRepository.saveAndFlush(meal);
        int remaining = recipe.size() - 1;

        demoDataSeeder.seedAll();

        assertEquals("meals/admins-own-photo.jpg", meal.getImagePath());
        List<Recipe> after = recipeRepository.findByMealSizeId(size.getId());
        assertEquals(remaining, after.size(), "the removed ingredient does not come back");
        assertTrue(after.stream().anyMatch(line -> line.getQuantity().compareTo(new BigDecimal("777.00")) == 0));
    }

    private void assertUploadedPng(String imagePath, String label) throws IOException {
        assertNotNull(imagePath, label + " has an image");
        Path file = Path.of(uploadDir).resolve(imagePath);
        assertTrue(Files.isRegularFile(file), label + " image file exists: " + file);
        assertTrue(Files.size(file) <= 5L * 1024 * 1024, label + " image is within the 5 MB upload limit");
        byte[] header = Files.readAllBytes(file);
        assertTrue(header.length > 8 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G', label + " image is a PNG");
    }

    private static void assertPng(String resource, int width, int height) throws IOException {
        ClassPathResource image = new ClassPathResource(resource);
        assertTrue(image.exists(), resource + " exists");
        byte[] bytes = image.getInputStream().readAllBytes();
        assertTrue(bytes.length > 24 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G', resource + " is a PNG");
        int actualWidth = ((bytes[16] & 0xff) << 24) | ((bytes[17] & 0xff) << 16) | ((bytes[18] & 0xff) << 8) | (bytes[19] & 0xff);
        int actualHeight = ((bytes[20] & 0xff) << 24) | ((bytes[21] & 0xff) << 16) | ((bytes[22] & 0xff) << 8) | (bytes[23] & 0xff);
        assertEquals(width + "x" + height, actualWidth + "x" + actualHeight, resource + " size");
        assertTrue(bytes.length <= 5 * 1024 * 1024, resource + " is within the upload limit");
    }

    private static String sizeLabel(MealSize size, Language language) {
        return size.getTranslations().stream()
                .filter(translation -> translation.getLanguage() == language)
                .findFirst()
                .orElseThrow()
                .getLabel();
    }

    private static MealTranslation translation(Meal meal, Language language) {
        return meal.getTranslations().stream()
                .filter(translation -> translation.getLanguage() == language)
                .findFirst()
                .orElseThrow();
    }

    private void assertCounts() {
        assertEquals(4, categoryRepository.count());
        assertEquals(8, mealRepository.count());
        assertEquals(6, tableRepository.count());
        assertEquals(4, staffAccountRepository.count());
    }

}
