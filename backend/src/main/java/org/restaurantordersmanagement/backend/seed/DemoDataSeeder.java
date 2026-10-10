package org.restaurantordersmanagement.backend.seed;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.restaurantordersmanagement.backend.i18n.Language;
import org.restaurantordersmanagement.backend.menu.model.Category;
import org.restaurantordersmanagement.backend.menu.model.CategoryTranslation;
import org.restaurantordersmanagement.backend.menu.model.Meal;
import org.restaurantordersmanagement.backend.menu.model.MealSize;
import org.restaurantordersmanagement.backend.menu.model.MealSizeTranslation;
import org.restaurantordersmanagement.backend.menu.model.MealTranslation;
import org.restaurantordersmanagement.backend.menu.model.RawMaterial;
import org.restaurantordersmanagement.backend.menu.model.Recipe;
import org.restaurantordersmanagement.backend.menu.repository.CategoryRepository;
import org.restaurantordersmanagement.backend.menu.repository.MealRepository;
import org.restaurantordersmanagement.backend.menu.repository.RawMaterialRepository;
import org.restaurantordersmanagement.backend.menu.repository.RecipeRepository;
import org.restaurantordersmanagement.backend.staff.model.Role;
import org.restaurantordersmanagement.backend.staff.model.StaffAccount;
import org.restaurantordersmanagement.backend.staff.repository.StaffAccountRepository;
import org.restaurantordersmanagement.backend.table.model.Table;
import org.restaurantordersmanagement.backend.table.repository.TableRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo data reused verbatim from the project's own design prototype
 * (Documentation/Design/Restaurant Orders System.dc.html) rather than
 * invented, since it's already the canonical example data for this
 * project's demos. Always registered as a bean but does nothing on its
 * own - {@link DemoDataStartupRunner} is what actually calls
 * {@link #seedAll()}, and only when {@code app.seed.demo} is on (the dev profile, or APP_SEED_DEMO=true).
 *
 * The German and Arabic meal content (descriptions, preparation, ingredients)
 * was written for this project, since the prototype only has English text;
 * have a native speaker review the Arabic before a real demo.
 */
@Slf4j
@Component
public class DemoDataSeeder {

    /** Demo login PIN for every seeded staff account - see the task-summary doc. */
    private static final String DEMO_PIN = "1234";

    /** Device ids the first seed used (not typeable on a guest device) and the codes that replace them. */
    private static final Map<String, String> LEGACY_DEVICE_IDS = Map.of(
            "tablet-a1", "ALPHA2",
            "tablet-a2", "BRAVE3",
            "tablet-b3", "GULF77",
            "tablet-b5", "NAVY99",
            "tablet-c1", "KAYAK2");

    private final CategoryRepository categoryRepository;
    private final MealRepository mealRepository;
    private final RawMaterialRepository rawMaterialRepository;
    private final RecipeRepository recipeRepository;
    private final TableRepository tableRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final Path uploadDir;

    public DemoDataSeeder(
            CategoryRepository categoryRepository,
            MealRepository mealRepository,
            RawMaterialRepository rawMaterialRepository,
            RecipeRepository recipeRepository,
            TableRepository tableRepository,
            StaffAccountRepository staffAccountRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.upload.dir}") String uploadDir) {
        this.categoryRepository = categoryRepository;
        this.mealRepository = mealRepository;
        this.rawMaterialRepository = rawMaterialRepository;
        this.recipeRepository = recipeRepository;
        this.tableRepository = tableRepository;
        this.staffAccountRepository = staffAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.uploadDir = Path.of(uploadDir);
    }

    /**
     * One transaction, so a failed seed leaves nothing half-written - and so
     * the lazy translations are loaded when an already-seeded menu is topped up.
     */
    @Transactional
    public void seedAll() {
        seedMenu();
        SeedCatalog catalog = SeedCatalog.load();
        seedRawMaterials(catalog);
        seedRecipes(catalog);
        seedImages(catalog);
        seedTables();
        seedStaff();
    }

    /** Every raw material of the catalog that does not exist yet (matched by name), so an admin's own entries stay. */
    private void seedRawMaterials(SeedCatalog catalog) {
        List<String> existing = rawMaterialRepository.findAll().stream().map(RawMaterial::getName).toList();
        int created = 0;
        for (SeedCatalog.RawMaterialEntry entry : catalog.rawMaterials()) {
            if (existing.contains(entry.name())) {
                continue;
            }
            RawMaterial rawMaterial = new RawMaterial();
            rawMaterial.setName(entry.name());
            rawMaterial.setUnit(entry.unit());
            rawMaterial.setInStockQuantity(new BigDecimal(entry.stock()));
            rawMaterial.setSupplier(entry.supplier());
            rawMaterialRepository.save(rawMaterial);
            created++;
        }
        if (created > 0) {
            log.info("Seeded {} demo raw materials", created);
        }
    }

    /**
     * The recipe of each meal size, only for sizes that have none yet: a recipe an admin already edited is never
     * touched, and a second run adds nothing. Sizes are paired with the catalog's factors in price order.
     */
    private void seedRecipes(SeedCatalog catalog) {
        Map<String, RawMaterial> bySlug = rawMaterialsBySlug(catalog);
        for (SeedCatalog.RecipeEntry recipeEntry : catalog.recipes()) {
            Meal meal = mealForSlug(catalog, recipeEntry.meal());
            if (meal == null) {
                continue;
            }
            List<MealSize> sizes = meal.getSizes().stream().sorted(Comparator.comparing(MealSize::getPrice)).toList();
            for (int i = 0; i < sizes.size() && i < recipeEntry.sizeFactors().size(); i++) {
                MealSize size = sizes.get(i);
                if (!recipeRepository.findByMealSizeId(size.getId()).isEmpty()) {
                    continue;
                }
                BigDecimal factor = new BigDecimal(recipeEntry.sizeFactors().get(i));
                for (SeedCatalog.IngredientEntry ingredient : recipeEntry.ingredients()) {
                    Recipe recipe = new Recipe();
                    recipe.setMealSize(size);
                    recipe.setRawMaterial(bySlug.get(ingredient.rawMaterial()));
                    recipe.setQuantity(new BigDecimal(ingredient.quantity()).multiply(factor).setScale(2, RoundingMode.HALF_UP));
                    recipeRepository.save(recipe);
                }
            }
        }
    }

    /**
     * Attaches the illustration of each catalog meal and raw material that has no image yet, by copying the PNG
     * from the classpath into the upload folder (served at /images/...). An image an admin set is never replaced;
     * one the admin removed comes back on the next start of the dev profile. The seed's own file is copied again when
     * it is gone from the upload folder: the database outlives the files on a host that erases them (a free Render
     * service loses its files whenever it sleeps or is redeployed), and the path would then point at nothing.
     */
    private void seedImages(SeedCatalog catalog) {
        int attached = 0;
        for (SeedCatalog.MealEntry entry : catalog.meals()) {
            Meal meal = mealForSlug(catalog, entry.slug());
            if (meal != null && needsSeedImage(meal.getImagePath(), "meals", entry.slug())) {
                meal.setImagePath(copySeedImage("meals", entry.slug()));
                attached++;
            }
        }
        Map<String, RawMaterial> bySlug = rawMaterialsBySlug(catalog);
        for (SeedCatalog.RawMaterialEntry entry : catalog.rawMaterials()) {
            RawMaterial rawMaterial = bySlug.get(entry.slug());
            if (rawMaterial != null && needsSeedImage(rawMaterial.getImagePath(), "raw-materials", entry.slug())) {
                rawMaterial.setImagePath(copySeedImage("raw-materials", entry.slug()));
                attached++;
            }
        }
        if (attached > 0) {
            log.info("Attached {} demo images", attached);
        }
    }

    /** No image yet, or the seed's own image whose file is missing. An admin's upload (another name) is never touched. */
    private boolean needsSeedImage(String currentPath, String folder, String slug) {
        if (currentPath == null) {
            return true;
        }
        String seedPath = folder + "/" + slug + ".png";
        return currentPath.equals(seedPath) && !Files.exists(uploadDir.resolve(seedPath));
    }

    /** @return the path to store on the entity, relative to the upload folder, like ImageStorageService does */
    private String copySeedImage(String folder, String slug) {
        String relativePath = folder + "/" + slug + ".png";
        try (InputStream in = new ClassPathResource("seed-images/" + relativePath).getInputStream()) {
            Path target = uploadDir.resolve(relativePath);
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            return relativePath;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not copy the seed image " + relativePath, e);
        }
    }

    private Map<String, RawMaterial> rawMaterialsBySlug(SeedCatalog catalog) {
        Map<String, RawMaterial> byName =
                rawMaterialRepository.findAll().stream().collect(Collectors.toMap(RawMaterial::getName, Function.identity(), (a, b) -> a));
        return catalog.rawMaterials().stream()
                .filter(entry -> byName.containsKey(entry.name()))
                .collect(Collectors.toMap(SeedCatalog.RawMaterialEntry::slug, entry -> byName.get(entry.name())));
    }

    /** The meal whose English name is the catalog's name for this slug, or null if the menu has no such meal. */
    private Meal mealForSlug(SeedCatalog catalog, String slug) {
        String name = catalog.meals().stream()
                .filter(entry -> entry.slug().equals(slug))
                .map(SeedCatalog.MealEntry::name)
                .findFirst()
                .orElse(null);
        if (name == null) {
            return null;
        }
        return mealRepository.findAll().stream()
                .filter(meal -> meal.getTranslations().stream()
                        .anyMatch(t -> t.getLanguage() == Language.EN && name.equals(t.getName())))
                .findFirst()
                .orElse(null);
    }

    private void seedMenu() {
        if (categoryRepository.count() > 0) {
            log.info("Demo menu already seeded, filling in any missing DE/AR meal content");
            fillMissingMealContent();
            return;
        }

        Map<Integer, Category> categories = Map.of(
                1, category(1, "Starters", "Vorspeisen", "المقبلات"),
                2, category(2, "Mains", "Hauptspeisen", "الأطباق الرئيسية"),
                3, category(3, "Desserts", "Desserts", "الحلويات"),
                4, category(4, "Drinks", "Getränke", "المشروبات"));

        for (MealSeed seed : mealSeeds()) {
            meal(categories.get(seed.categorySortOrder()), seed);
        }

        log.info("Seeded demo menu: 4 categories, 8 meals");
    }

    /** The demo menu: category 1 starters, 2 mains, 3 desserts, 4 drinks. Ingredient lists are in the same order in every language. */
    private List<MealSeed> mealSeeds() {
        return List.of(
                mealSeed(1, "Pumpkin Soup", "Kürbissuppe", "شوربة اليقطين", true,
                        text("Hokkaido pumpkin, roasted until sweet, finished with pumpkin-seed oil and crème fraîche.",
                                "Roasted, blended, passed through a fine sieve. Served at 68°C.",
                                "Hokkaido pumpkin", "Onion", "Vegetable stock", "Cream", "Pumpkin-seed oil", "Nutmeg"),
                        text("Hokkaido-Kürbis, bis zur Süße geröstet, verfeinert mit Kürbiskernöl und Crème fraîche.",
                                "Geröstet, püriert und durch ein feines Sieb gestrichen. Serviert bei 68 °C.",
                                "Hokkaido-Kürbis", "Zwiebel", "Gemüsebrühe", "Sahne", "Kürbiskernöl", "Muskat"),
                        text("يقطين هوكايدو محمّص حتى تظهر حلاوته، ويُقدَّم مع زيت بذور اليقطين والكريم فريش.",
                                "يُحمَّص ثم يُهرس ويُمرَّر عبر منخل ناعم. يُقدَّم على حرارة 68 درجة مئوية.",
                                "يقطين هوكايدو", "بصل", "مرق خضار", "قشدة", "زيت بذور اليقطين", "جوزة الطيب"),
                        size("Cup", "Tasse", "كوب", "6.50"),
                        size("Bowl", "Schüssel", "وعاء", "8.90")),

                mealSeed(1, "Flammkuchen", "Flammkuchen", "فلامكوخن", true,
                        text("Thin Alsatian tart with crème fraîche, smoked bacon and onion, baked on the stone.",
                                "Stone oven, 320°C, 4 minutes. Cut into six pieces.",
                                "Wheat flour", "Crème fraîche", "Smoked bacon", "Onion", "Chives"),
                        text("Dünner elsässischer Fladen mit Crème fraîche, geräuchertem Speck und Zwiebeln, auf dem Stein gebacken.",
                                "Steinofen, 320 °C, 4 Minuten. In sechs Stücke geschnitten.",
                                "Weizenmehl", "Crème fraîche", "Geräucherter Speck", "Zwiebel", "Schnittlauch"),
                        text("فطيرة رقيقة من منطقة الألزاس بالكريم فريش والبيكون المدخّن والبصل، تُخبز على الحجر.",
                                "فرن حجري، 320 درجة مئوية، 4 دقائق. تُقطَّع إلى ست قطع.",
                                "طحين القمح", "كريم فريش", "بيكون مدخّن", "بصل", "ثوم معمّر"),
                        size("Half board", "Halbes Blech", "نصف صينية", "9.80"),
                        size("Full board", "Ganzes Blech", "صينية كاملة", "14.50")),

                mealSeed(2, "Wiener Schnitzel", "Wiener Schnitzel", "شنيتزل فيينا", true,
                        text("Veal escalope in a light breadcrumb coating, pan-fried in clarified butter. With potato salad and lingonberry.",
                                "Beaten to 4 mm, breaded in flour, egg and breadcrumbs, fried swimming in clarified butter.",
                                "Veal", "Wheat flour", "Egg", "Breadcrumbs", "Clarified butter", "Lingonberry"),
                        text("Kalbsschnitzel in leichter Semmelbrösel-Panade, in Butterschmalz ausgebacken. Dazu Kartoffelsalat und Preiselbeeren.",
                                "Auf 4 mm geklopft, in Mehl, Ei und Semmelbröseln gewendet und schwimmend in Butterschmalz ausgebacken.",
                                "Kalbfleisch", "Weizenmehl", "Ei", "Semmelbrösel", "Butterschmalz", "Preiselbeeren"),
                        text("شريحة لحم عجل بغلاف خفيف من فتات الخبز، مقلية في السمن المصفّى. تُقدَّم مع سلطة البطاطا والتوت البري الأحمر.",
                                "تُطرَّق حتى سماكة 4 ملم، وتُغطّى بالطحين والبيض وفتات الخبز، ثم تُقلى غائصةً في السمن المصفّى.",
                                "لحم عجل", "طحين القمح", "بيض", "فتات الخبز", "سمن مصفّى", "التوت البري الأحمر"),
                        size("200 g", "200 g", "200 غرام", "18.90"),
                        size("300 g", "300 g", "300 غرام", "22.50")),

                mealSeed(2, "Käsespätzle", "Käsespätzle", "شبيتسله بالجبن", true,
                        text("Hand-scraped spätzle layered with mountain cheese and crowned with dark roasted onions.",
                                "Scraped fresh to order, gratinated for 6 minutes.",
                                "Wheat flour", "Egg", "Bergkäse", "Emmental", "Onion", "Butter"),
                        text("Handgeschabte Spätzle, geschichtet mit Bergkäse und gekrönt von dunkel gerösteten Zwiebeln.",
                                "Frisch auf Bestellung geschabt, 6 Minuten überbacken.",
                                "Weizenmehl", "Ei", "Bergkäse", "Emmentaler", "Zwiebel", "Butter"),
                        text("شبيتسله مبشورة يدويًا، مطبوخة على طبقات مع جبن الجبال، وتعلوها شرائح بصل محمّرة داكنة.",
                                "تُحضَّر طازجة عند الطلب وتُحمَّر في الفرن 6 دقائق.",
                                "طحين القمح", "بيض", "جبن الجبال (بيرغكيزه)", "جبن إيمنتال", "بصل", "زبدة"),
                        size("Regular", "Normal", "عادي", "14.50"),
                        size("Large", "Groß", "كبير", "17.90")),

                mealSeed(2, "Rinderroulade", "Rinderroulade", "لفائف لحم البقر", false,
                        text("Beef roulade with mustard, bacon and pickled cucumber, braised two hours in its own sauce.",
                                "Seared, then braised at 140°C for 120 minutes. Sauce reduced and strained.",
                                "Beef", "Mustard", "Bacon", "Pickled cucumber", "Onion", "Red wine"),
                        text("Rinderroulade mit Senf, Speck und Gewürzgurke, zwei Stunden in der eigenen Sauce geschmort.",
                                "Angebraten, dann 120 Minuten bei 140 °C geschmort. Sauce eingekocht und passiert.",
                                "Rindfleisch", "Senf", "Speck", "Gewürzgurke", "Zwiebel", "Rotwein"),
                        text("لفائف لحم بقري محشوة بالخردل والبيكون والخيار المخلّل، تُطهى ببطء ساعتين في صلصتها.",
                                "تُحمَّر أولًا ثم تُطهى ببطء على 140 درجة مئوية لمدة 120 دقيقة. تُركَّز الصلصة وتُصفّى.",
                                "لحم بقري", "خردل", "بيكون", "خيار مخلّل", "بصل", "نبيذ أحمر"),
                        size("One roll", "Eine Rolle", "لفة واحدة", "19.80"),
                        size("Two rolls", "Zwei Rollen", "لفتان", "26.40")),

                mealSeed(3, "Apfelstrudel", "Apfelstrudel", "شتراودل التفاح", true,
                        text("Pulled strudel dough, Elstar apples, raisins and cinnamon. Served warm with vanilla sauce.",
                                "Dough pulled by hand, baked 25 minutes, rested 5 minutes before serving.",
                                "Wheat flour", "Elstar apple", "Raisins", "Cinnamon", "Butter", "Vanilla"),
                        text("Handgezogener Strudelteig, Elstar-Äpfel, Rosinen und Zimt. Warm serviert mit Vanillesauce.",
                                "Teig von Hand ausgezogen, 25 Minuten gebacken, vor dem Servieren 5 Minuten ruhen gelassen.",
                                "Weizenmehl", "Elstar-Apfel", "Rosinen", "Zimt", "Butter", "Vanille"),
                        text("عجينة شتراودل مشدودة يدويًا، مع تفاح إلستار والزبيب والقرفة. تُقدَّم دافئة مع صلصة الفانيليا.",
                                "تُمدّ العجينة يدويًا، وتُخبز 25 دقيقة، وتُترك 5 دقائق قبل التقديم.",
                                "طحين القمح", "تفاح إلستار", "زبيب", "قرفة", "زبدة", "فانيليا"),
                        size("Slice", "Stück", "قطعة", "7.20")),

                mealSeed(3, "Kaiserschmarrn", "Kaiserschmarrn", "كايزرشمارن", true,
                        text("Shredded sweet pancake, caramelised in butter, dusted with icing sugar. With plum compote.",
                                "Pan-baked, torn, caramelised. Takes 14 minutes, started on order.",
                                "Wheat flour", "Milk", "Egg", "Butter", "Icing sugar", "Plum"),
                        text("Zerrissener süßer Pfannkuchen, in Butter karamellisiert, mit Puderzucker bestäubt. Dazu Zwetschgenröster.",
                                "In der Pfanne gebacken, zerrissen, karamellisiert. Dauert 14 Minuten, wird auf Bestellung gestartet.",
                                "Weizenmehl", "Milch", "Ei", "Butter", "Puderzucker", "Zwetschgen"),
                        text("فطيرة حلوة مقطّعة إلى قطع صغيرة، مكرمَلة بالزبدة ومرشوشة بسكر البودرة. تُقدَّم مع كومبوت البرقوق.",
                                "تُخبز في المقلاة ثم تُمزَّق وتُكرمَل. تستغرق 14 دقيقة وتبدأ عند الطلب.",
                                "طحين القمح", "حليب", "بيض", "زبدة", "سكر بودرة", "برقوق"),
                        size("For one", "Für eine Person", "لشخص واحد", "8.40"),
                        size("To share", "Zum Teilen", "للمشاركة", "13.90")),

                mealSeed(4, "Radler", "Radler", "رادلر", true,
                        text("Helles beer cut with cloudy lemonade, poured cold.",
                                "Poured on order, 4°C.",
                                "Helles beer", "Cloudy lemonade"),
                        text("Helles Bier mit trüber Zitronenlimonade gemischt, kalt eingeschenkt.",
                                "Auf Bestellung eingeschenkt, 4 °C.",
                                "Helles Bier", "Trübe Zitronenlimonade"),
                        text("بيرة هيلس ممزوجة بليموناضة غائمة، تُقدَّم باردة.",
                                "تُسكب عند الطلب على حرارة 4 درجات مئوية.",
                                "بيرة هيلس", "ليموناضة غائمة"),
                        size("0.3 l", "0,3 l", "0.3 لتر", "4.20"),
                        size("0.5 l", "0,5 l", "0.5 لتر", "5.60")));
    }

    /**
     * Tops up a menu that was seeded before DE/AR content existed: a German or
     * Arabic translation with no description, preparation or ingredients gets
     * the seed text. Anything an admin has already written is left alone.
     */
    private void fillMissingMealContent() {
        Map<String, MealSeed> seedsByEnglishName =
                mealSeeds().stream().collect(Collectors.toMap(MealSeed::nameEn, Function.identity()));

        int filled = 0;
        for (Meal meal : mealRepository.findAll()) {
            MealSeed seed = meal.getTranslations().stream()
                    .filter(translation -> translation.getLanguage() == Language.EN)
                    .findFirst()
                    .map(translation -> seedsByEnglishName.get(translation.getName()))
                    .orElse(null);
            if (seed == null) {
                continue;
            }
            for (MealTranslation translation : meal.getTranslations()) {
                MealText text = switch (translation.getLanguage()) {
                    case DE -> seed.de();
                    case AR -> seed.ar();
                    default -> null;
                };
                if (text != null && isEmpty(translation)) {
                    applyText(translation, text);
                    filled++;
                }
            }
            filled += fillUntranslatedSizeLabels(meal, seed);
            mealRepository.saveAndFlush(meal);
        }
        log.info("Filled {} empty DE/AR meal translations and size labels", filled);
    }

    /**
     * A German or Arabic size label that is still identical to the English one
     * was never translated (for example "200 g" in Arabic), so it gets the seed
     * label. A label an admin has already changed no longer matches English and
     * is left alone.
     */
    private int fillUntranslatedSizeLabels(Meal meal, MealSeed seed) {
        int filled = 0;
        for (MealSize size : meal.getSizes()) {
            SizeSeed sizeSeed = seed.sizes().stream()
                    .filter(candidate -> new BigDecimal(candidate.price()).compareTo(size.getPrice()) == 0)
                    .findFirst()
                    .orElse(null);
            if (sizeSeed == null) {
                continue;
            }
            String englishLabel = size.getTranslations().stream()
                    .filter(translation -> translation.getLanguage() == Language.EN)
                    .map(MealSizeTranslation::getLabel)
                    .findFirst()
                    .orElse(null);
            for (MealSizeTranslation translation : size.getTranslations()) {
                String seedLabel = switch (translation.getLanguage()) {
                    case DE -> sizeSeed.labelDe();
                    case AR -> sizeSeed.labelAr();
                    default -> null;
                };
                if (seedLabel != null
                        && translation.getLabel().equals(englishLabel)
                        && !seedLabel.equals(englishLabel)) {
                    translation.setLabel(seedLabel);
                    filled++;
                }
            }
        }
        return filled;
    }

    private static boolean isEmpty(MealTranslation translation) {
        return translation.getDescription() == null
                && translation.getPreparationMethod() == null
                && (translation.getIngredients() == null || translation.getIngredients().isEmpty());
    }

    private static void applyText(MealTranslation translation, MealText text) {
        translation.setDescription(text.description());
        translation.setPreparationMethod(text.preparationMethod());
        // A mutable copy: Hibernate clear()s the old collection when it merges a managed entity, which an immutable List.of() rejects.
        translation.setIngredients(new ArrayList<>(text.ingredients()));
    }

    private void seedTables() {
        Instant online = Instant.now();
        Instant offline = Instant.now().minus(Duration.ofHours(3));

        table("1", "Front room", 2, "ALPHA2", online);
        table("2", "Front room", 4, "BRAVE3", online);
        table("7", "Garden room", 4, "GULF77", online);
        table("9", "Garden room", 6, "NAVY99", online);
        table("11", "Terrace", 4, "KAYAK2", offline);
        table("14", "Terrace", 2, null, null);

        replaceLegacyDeviceIds();
    }

    /**
     * Pairing codes are what a guest device types in: six characters, upper
     * case (the device uppercases what it is given and the server looks codes up
     * in upper case). The first seed used ids like "tablet-a1", which can never
     * be entered, so a database seeded then is moved to the new codes. Only an id
     * that is exactly an old seed value is replaced; a code an admin generated is
     * never touched.
     */
    private void replaceLegacyDeviceIds() {
        for (Map.Entry<String, String> legacy : LEGACY_DEVICE_IDS.entrySet()) {
            if (tableRepository.findByPairedDeviceId(legacy.getValue()).isPresent()) {
                continue;
            }
            tableRepository.findByPairedDeviceId(legacy.getKey()).ifPresent(table -> {
                table.setPairedDeviceId(legacy.getValue());
                tableRepository.saveAndFlush(table);
                log.info("Demo table {}: replaced the legacy device id with {}", table.getTableNumber(), legacy.getValue());
            });
        }
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

    private void seedStaff() {
        staffAccount("O. Sado", Role.ADMIN);
        staffAccount("M. Behr", Role.KITCHEN);
        staffAccount("L. Adler", Role.WAITER);
        staffAccount("T. Nowak", Role.CASHIER);
    }

    private void staffAccount(String name, Role role) {
        if (staffAccountRepository.findByName(name).isPresent()) {
            log.info("Demo staff account {} already seeded, skipping", name);
            return;
        }

        StaffAccount staffAccount = new StaffAccount();
        staffAccount.setName(name);
        staffAccount.setRole(role);
        staffAccount.setPinHash(passwordEncoder.encode(DEMO_PIN));
        staffAccountRepository.saveAndFlush(staffAccount);
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

    private void meal(Category category, MealSeed seed) {
        Meal meal = new Meal();
        meal.setCategory(category);
        meal.setAvailable(seed.available());

        addMealTranslation(meal, Language.EN, seed.nameEn(), seed.en());
        addMealTranslation(meal, Language.DE, seed.nameDe(), seed.de());
        addMealTranslation(meal, Language.AR, seed.nameAr(), seed.ar());

        for (SizeSeed sizeSeed : seed.sizes()) {
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

    private void addMealTranslation(Meal meal, Language language, String name, MealText text) {
        MealTranslation translation = new MealTranslation();
        translation.setMeal(meal);
        translation.setLanguage(language);
        translation.setName(name);
        applyText(translation, text);
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

    private static MealText text(String description, String preparationMethod, String... ingredients) {
        return new MealText(description, preparationMethod, List.of(ingredients));
    }

    private static MealSeed mealSeed(
            int categorySortOrder,
            String nameEn,
            String nameDe,
            String nameAr,
            boolean available,
            MealText en,
            MealText de,
            MealText ar,
            SizeSeed... sizes) {
        return new MealSeed(categorySortOrder, nameEn, nameDe, nameAr, available, en, de, ar, List.of(sizes));
    }

    private record SizeSeed(String labelEn, String labelDe, String labelAr, String price) {
    }

    private record MealText(String description, String preparationMethod, List<String> ingredients) {
    }

    private record MealSeed(
            int categorySortOrder,
            String nameEn,
            String nameDe,
            String nameAr,
            boolean available,
            MealText en,
            MealText de,
            MealText ar,
            List<SizeSeed> sizes) {
    }

}
