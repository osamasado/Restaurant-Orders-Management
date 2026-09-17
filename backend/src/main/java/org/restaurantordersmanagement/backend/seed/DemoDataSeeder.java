package org.restaurantordersmanagement.backend.seed;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.restaurantordersmanagement.backend.i18n.Language;
import org.restaurantordersmanagement.backend.menu.model.Category;
import org.restaurantordersmanagement.backend.menu.model.CategoryTranslation;
import org.restaurantordersmanagement.backend.menu.model.Meal;
import org.restaurantordersmanagement.backend.menu.model.MealSize;
import org.restaurantordersmanagement.backend.menu.model.MealSizeTranslation;
import org.restaurantordersmanagement.backend.menu.model.MealTranslation;
import org.restaurantordersmanagement.backend.menu.repository.CategoryRepository;
import org.restaurantordersmanagement.backend.menu.repository.MealRepository;
import org.restaurantordersmanagement.backend.table.model.Table;
import org.restaurantordersmanagement.backend.table.repository.TableRepository;
import org.springframework.stereotype.Component;

/**
 * Demo data reused verbatim from the project's own design prototype
 * (Documentation/Design/Restaurant Orders System.dc.html) rather than
 * invented, since it's already the canonical example data for this
 * project's demos. Always registered as a bean but does nothing on its
 * own - {@link DemoDataStartupRunner} is what actually calls
 * {@link #seedAll()}, and only in the dev profile.
 */
@Slf4j
@Component
public class DemoDataSeeder {

    private final CategoryRepository categoryRepository;
    private final MealRepository mealRepository;
    private final TableRepository tableRepository;

    public DemoDataSeeder(
            CategoryRepository categoryRepository, MealRepository mealRepository, TableRepository tableRepository) {
        this.categoryRepository = categoryRepository;
        this.mealRepository = mealRepository;
        this.tableRepository = tableRepository;
    }

    public void seedAll() {
        seedMenu();
        seedTables();
    }

    private void seedMenu() {
        if (categoryRepository.count() > 0) {
            log.info("Demo menu already seeded, skipping");
            return;
        }

        Category starters = category(1, "Starters", "Vorspeisen", "المقبلات");
        Category mains = category(2, "Mains", "Hauptspeisen", "الأطباق الرئيسية");
        Category desserts = category(3, "Desserts", "Desserts", "الحلويات");
        Category drinks = category(4, "Drinks", "Getränke", "المشروبات");

        meal(starters, "Pumpkin Soup", "Kürbissuppe", "شوربة اليقطين", true,
                "Hokkaido pumpkin, roasted until sweet, finished with pumpkin-seed oil and crème fraîche.",
                "Roasted, blended, passed through a fine sieve. Served at 68°C.",
                List.of("Hokkaido pumpkin", "Onion", "Vegetable stock", "Cream", "Pumpkin-seed oil", "Nutmeg"),
                size("Cup", "Tasse", "كوب", "6.50"),
                size("Bowl", "Schüssel", "وعاء", "8.90"));

        meal(starters, "Flammkuchen", "Flammkuchen", "فلامكوخن", true,
                "Thin Alsatian tart with crème fraîche, smoked bacon and onion, baked on the stone.",
                "Stone oven, 320°C, 4 minutes. Cut into six pieces.",
                List.of("Wheat flour", "Crème fraîche", "Smoked bacon", "Onion", "Chives"),
                size("Half board", "Halbes Blech", "نصف صينية", "9.80"),
                size("Full board", "Ganzes Blech", "صينية كاملة", "14.50"));

        meal(mains, "Wiener Schnitzel", "Wiener Schnitzel", "شنيتزل فيينا", true,
                "Veal escalope in a light breadcrumb coating, pan-fried in clarified butter. With potato salad and lingonberry.",
                "Beaten to 4 mm, breaded in flour, egg and breadcrumbs, fried swimming in clarified butter.",
                List.of("Veal", "Wheat flour", "Egg", "Breadcrumbs", "Clarified butter", "Lingonberry"),
                size("200 g", "200 g", "200 g", "18.90"),
                size("300 g", "300 g", "300 g", "22.50"));

        meal(mains, "Käsespätzle", "Käsespätzle", "شبيتسله بالجبن", true,
                "Hand-scraped spätzle layered with mountain cheese and crowned with dark roasted onions.",
                "Scraped fresh to order, gratinated for 6 minutes.",
                List.of("Wheat flour", "Egg", "Bergkäse", "Emmental", "Onion", "Butter"),
                size("Regular", "Normal", "عادي", "14.50"),
                size("Large", "Groß", "كبير", "17.90"));

        meal(mains, "Rinderroulade", "Rinderroulade", "لفائف لحم البقر", false,
                "Beef roulade with mustard, bacon and pickled cucumber, braised two hours in its own sauce.",
                "Seared, then braised at 140°C for 120 minutes. Sauce reduced and strained.",
                List.of("Beef", "Mustard", "Bacon", "Pickled cucumber", "Onion", "Red wine"),
                size("One roll", "Eine Rolle", "لفة واحدة", "19.80"),
                size("Two rolls", "Zwei Rollen", "لفتان", "26.40"));

        meal(desserts, "Apfelstrudel", "Apfelstrudel", "شتراودل التفاح", true,
                "Pulled strudel dough, Elstar apples, raisins and cinnamon. Served warm with vanilla sauce.",
                "Dough pulled by hand, baked 25 minutes, rested 5 minutes before serving.",
                List.of("Wheat flour", "Elstar apple", "Raisins", "Cinnamon", "Butter", "Vanilla"),
                size("Slice", "Stück", "قطعة", "7.20"));

        meal(desserts, "Kaiserschmarrn", "Kaiserschmarrn", "كايزرشمارن", true,
                "Shredded sweet pancake, caramelised in butter, dusted with icing sugar. With plum compote.",
                "Pan-baked, torn, caramelised. Takes 14 minutes, started on order.",
                List.of("Wheat flour", "Milk", "Egg", "Butter", "Icing sugar", "Plum"),
                size("For one", "Für eine Person", "لشخص واحد", "8.40"),
                size("To share", "Zum Teilen", "للمشاركة", "13.90"));

        meal(drinks, "Radler", "Radler", "رادلر", true,
                "Helles beer cut with cloudy lemonade, poured cold.",
                "Poured on order, 4°C.",
                List.of("Helles beer", "Cloudy lemonade"),
                size("0.3 l", "0,3 l", "0.3 l", "4.20"),
                size("0.5 l", "0,5 l", "0.5 l", "5.60"));

        log.info("Seeded demo menu: 4 categories, 8 meals");
    }

    private void seedTables() {
        Instant online = Instant.now();
        Instant offline = Instant.now().minus(Duration.ofHours(3));

        table("1", "Front room", 2, "tablet-a1", online);
        table("2", "Front room", 4, "tablet-a2", online);
        table("7", "Garden room", 4, "tablet-b3", online);
        table("9", "Garden room", 6, "tablet-b5", online);
        table("11", "Terrace", 4, "tablet-c1", offline);
        table("14", "Terrace", 2, null, null);
    }

    private void table(String tableNumber, String room, int seats, String pairedDeviceId, Instant lastSeenAt) {
        if (tableRepository.findByTableNumber(tableNumber).isPresent()) {
            log.info("Demo table {} already seeded, skipping", tableNumber);
            return;
        }

        Table table = new Table();
        table.setTableNumber(tableNumber);
        table.setRoom(room);
        table.setSeats(seats);
        table.setPairedDeviceId(pairedDeviceId);
        table.setLastSeenAt(lastSeenAt);
        tableRepository.saveAndFlush(table);
    }

    private Category category(int sortOrder, String nameEn, String nameDe, String nameAr) {
        Category category = new Category();
        category.setSortOrder(sortOrder);
        addCategoryTranslation(category, Language.EN, nameEn);
        addCategoryTranslation(category, Language.DE, nameDe);
        addCategoryTranslation(category, Language.AR, nameAr);
        return categoryRepository.saveAndFlush(category);
    }

    private void addCategoryTranslation(Category category, Language language, String name) {
        CategoryTranslation translation = new CategoryTranslation();
        translation.setCategory(category);
        translation.setLanguage(language);
        translation.setName(name);
        category.getTranslations().add(translation);
    }

    private void meal(
            Category category,
            String nameEn,
            String nameDe,
            String nameAr,
            boolean available,
            String description,
            String preparationMethod,
            List<String> ingredients,
            SizeSeed... sizes) {
        Meal meal = new Meal();
        meal.setCategory(category);
        meal.setAvailable(available);

        addMealTranslation(meal, Language.EN, nameEn, description, preparationMethod, ingredients);
        addMealTranslation(meal, Language.DE, nameDe, null, null, List.of());
        addMealTranslation(meal, Language.AR, nameAr, null, null, List.of());

        for (SizeSeed sizeSeed : sizes) {
            MealSize mealSize = new MealSize();
            mealSize.setMeal(meal);
            mealSize.setPrice(new BigDecimal(sizeSeed.price()));
            addSizeTranslation(mealSize, Language.EN, sizeSeed.labelEn());
            addSizeTranslation(mealSize, Language.DE, sizeSeed.labelDe());
            addSizeTranslation(mealSize, Language.AR, sizeSeed.labelAr());
            meal.getSizes().add(mealSize);
        }

        mealRepository.saveAndFlush(meal);
    }

    private void addMealTranslation(
            Meal meal,
            Language language,
            String name,
            String description,
            String preparationMethod,
            List<String> ingredients) {
        MealTranslation translation = new MealTranslation();
        translation.setMeal(meal);
        translation.setLanguage(language);
        translation.setName(name);
        translation.setDescription(description);
        translation.setPreparationMethod(preparationMethod);
        translation.setIngredients(ingredients);
        meal.getTranslations().add(translation);
    }

    private void addSizeTranslation(MealSize mealSize, Language language, String label) {
        MealSizeTranslation translation = new MealSizeTranslation();
        translation.setMealSize(mealSize);
        translation.setLanguage(language);
        translation.setLabel(label);
        mealSize.getTranslations().add(translation);
    }

    private static SizeSeed size(String labelEn, String labelDe, String labelAr, String price) {
        return new SizeSeed(labelEn, labelDe, labelAr, price);
    }

    private record SizeSeed(String labelEn, String labelDe, String labelAr, String price) {
    }

}
