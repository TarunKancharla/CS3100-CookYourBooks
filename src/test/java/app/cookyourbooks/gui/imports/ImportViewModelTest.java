package app.cookyourbooks.gui.imports;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import app.cookyourbooks.gui.ViewModelTestBase;
import app.cookyourbooks.gui.viewmodel.CollectionSummary;
import app.cookyourbooks.gui.viewmodel.EditableIngredient;
import app.cookyourbooks.gui.viewmodel.ImportViewModelImpl;
import app.cookyourbooks.model.PersonalCollectionImpl;
import app.cookyourbooks.model.Recipe;
import app.cookyourbooks.model.RecipeCollection;
import app.cookyourbooks.model.SourceType;
import app.cookyourbooks.model.Unit;
import app.cookyourbooks.services.LibrarianService;
import app.cookyourbooks.services.ocr.FakeRecipeOcrService;
import app.cookyourbooks.services.ocr.OcrException;
import app.cookyourbooks.services.ocr.RecipeOcrService;

class ImportViewModelTest extends ViewModelTestBase {

  @Test
  void initialState_isIdle() {
    ImportViewModelImpl vm =
        new ImportViewModelImpl(new FakeRecipeOcrService(1), new FakeLibrarianService());

    assertThat(vm.getState()).isEqualTo("idle");
    assertThat(vm.getImportedRecipeTitle()).isNull();
    assertThat(vm.getErrorMessage()).isNull();
  }

  @Test
  void startImport_transitionsToProcessing() {
    ImportViewModelImpl vm =
        new ImportViewModelImpl(new FakeRecipeOcrService(100), new FakeLibrarianService());

    vm.startImport(Path.of("pancakes.jpg"));

    assertThat(vm.getState()).isEqualTo("processing");
    assertThat(vm.getStatusMessage()).contains("Extracting");
  }

  @Test
  void successfulImport_transitionsToReview() throws Exception {
    ImportViewModelImpl vm =
        new ImportViewModelImpl(new FakeRecipeOcrService(10), new FakeLibrarianService());

    vm.startImport(Path.of("pancakes.jpg"));
    waitForState(vm, "review");

    assertThat(vm.getImportedRecipeTitle()).contains("Imported:");
    assertThat(vm.getImportedIngredientNames()).isNotEmpty();
  }

  @Test
  void ocrFailure_transitionsToError() throws Exception {
    RecipeOcrService failing =
        image -> {
          throw new OcrException("boom");
        };
    ImportViewModelImpl vm = new ImportViewModelImpl(failing, new FakeLibrarianService());

    vm.startImport(Path.of("pancakes.jpg"));
    waitForState(vm, "error");

    assertThat(vm.getErrorMessage()).contains("boom");
  }

  @Test
  void cancelImport_transitionsToIdle() {
    ImportViewModelImpl vm =
        new ImportViewModelImpl(new FakeRecipeOcrService(500), new FakeLibrarianService());

    vm.startImport(Path.of("pancakes.jpg"));
    vm.cancelImport();

    assertThat(vm.getState()).isEqualTo("idle");
  }

  @Test
  void acceptImport_savesRecipeToSelectedCollection() throws Exception {
    FakeLibrarianService librarian = new FakeLibrarianService();
    ImportViewModelImpl vm = new ImportViewModelImpl(new FakeRecipeOcrService(10), librarian);

    vm.loadCollections();
    waitForCollections(vm);
    CollectionSummary collection = vm.availableCollectionsProperty().get(0);
    vm.selectTargetCollection(collection.id());

    vm.startImport(Path.of("pancakes.jpg"));
    waitForState(vm, "review");

    vm.importedTitleProperty().set("Edited title");
    EditableIngredient first = vm.importedIngredientsProperty().get(0);
    first.setName("Edited ingredient");

    vm.acceptImport();

    assertThat(vm.getState()).isEqualTo("idle");
    assertThat(librarian.savedCollectionId).isEqualTo(collection.id());
    assertThat(librarian.savedRecipe).isNotNull();
    if (librarian.savedRecipe != null) {
      assertThat(librarian.savedRecipe.getTitle()).isEqualTo("Edited title");
      assertThat(librarian.savedRecipe.getIngredients().get(0).getName())
          .isEqualTo("Edited ingredient");
    }
  }

  @Test
  void rejectImport_discardsRecipe() throws Exception {
    ImportViewModelImpl vm =
        new ImportViewModelImpl(new FakeRecipeOcrService(10), new FakeLibrarianService());

    vm.startImport(Path.of("pancakes.jpg"));
    waitForState(vm, "review");

    vm.rejectImport();

    assertThat(vm.getState()).isEqualTo("idle");
    assertThat(vm.getImportedRecipeTitle()).isNull();
  }

  @Test
  void loadCollections_populatesAvailableCollections() throws Exception {
    ImportViewModelImpl vm =
        new ImportViewModelImpl(new FakeRecipeOcrService(1), new FakeLibrarianService());

    vm.loadCollections();
    waitForCollections(vm);

    assertThat(vm.getAvailableCollectionIds()).isNotEmpty();
  }

  @Test
  void preSaveEditing_updatesTitleAndIngredients() throws Exception {
    ImportViewModelImpl vm =
        new ImportViewModelImpl(new FakeRecipeOcrService(10), new FakeLibrarianService());

    vm.startImport(Path.of("pancakes.jpg"));
    waitForState(vm, "review");

    vm.importedTitleProperty().set("Custom title");
    vm.importedIngredientsProperty().add(new EditableIngredient("New ingredient"));

    assertThat(vm.getImportedRecipeTitle()).isEqualTo("Custom title");
    assertThat(vm.getImportedIngredientNames()).contains("New ingredient");
  }

  @Test
  void acceptImport_noCollectionOrRecipe_isNoOp() throws Exception {
    FakeLibrarianService librarian = new FakeLibrarianService();
    ImportViewModelImpl vm = new ImportViewModelImpl(new FakeRecipeOcrService(10), librarian);

    vm.acceptImport();
    assertThat(librarian.savedRecipe).isNull();

    vm.startImport(Path.of("pancakes.jpg"));
    waitForState(vm, "review");
    vm.selectTargetCollection("missing-id");
    vm.acceptImport();

    assertThat(librarian.savedRecipe).isNull();
  }

  private void waitForState(ImportViewModelImpl vm, String expectedState) throws Exception {
    long deadline = System.currentTimeMillis() + 2000;
    while (!expectedState.equals(vm.getState()) && System.currentTimeMillis() < deadline) {
      waitForFxEvents();
      Thread.sleep(20);
    }
    assertThat(vm.getState()).isEqualTo(expectedState);
  }

  private void waitForCollections(ImportViewModelImpl vm) throws Exception {
    long deadline = System.currentTimeMillis() + 2000;
    while (vm.getAvailableCollectionIds().isEmpty() && System.currentTimeMillis() < deadline) {
      waitForFxEvents();
      Thread.sleep(20);
    }
    assertThat(vm.getAvailableCollectionIds()).isNotEmpty();
  }

  private static final class FakeLibrarianService implements LibrarianService {
    private final List<RecipeCollection> collections =
        List.of(PersonalCollectionImpl.builder().title("Test collection").build());
    private @Nullable Recipe savedRecipe;
    private @Nullable String savedCollectionId;

    @Override
    public @NonNull List<RecipeCollection> listCollections() {
      return new ArrayList<>(collections);
    }

    @Override
    public void saveRecipe(@NonNull Recipe recipe, @NonNull String collectionId) {
      savedRecipe = recipe;
      savedCollectionId = collectionId;
    }

    @Override
    public @NonNull List<Recipe> listAllRecipes() {
      throw new UnsupportedOperationException("Not used");
    }

    @Override
    public @NonNull RecipeCollection createCollection(@NonNull String name) {
      throw new UnsupportedOperationException("Not used");
    }

    @Override
    public @NonNull RecipeCollection createCollection(
        @NonNull String name, @NonNull SourceType sourceType) {
      throw new UnsupportedOperationException("Not used");
    }

    @Override
    public Optional<RecipeCollection> findCollectionById(@NonNull String collectionId) {
      throw new UnsupportedOperationException("Not used");
    }

    @Override
    public void deleteCollection(@NonNull String collectionId) {
      throw new UnsupportedOperationException("Not used");
    }

    @Override
    public @NonNull List<RecipeCollection> findAllCollectionsByTitle(@NonNull String name) {
      throw new UnsupportedOperationException("Not used");
    }

    @Override
    public @NonNull List<Recipe> listRecipes(@NonNull String collectionName) {
      throw new UnsupportedOperationException("Not used");
    }

    @Override
    public Optional<Recipe> findRecipe(@NonNull String title) {
      throw new UnsupportedOperationException("Not used");
    }

    @Override
    public @NonNull List<Recipe> findAllRecipesByTitle(@NonNull String title) {
      throw new UnsupportedOperationException("Not used");
    }

    @Override
    public @NonNull List<Recipe> resolveRecipes(@NonNull String query) {
      throw new UnsupportedOperationException("Not used");
    }

    @Override
    public @NonNull List<Recipe> searchByIngredient(@NonNull String ingredientName) {
      throw new UnsupportedOperationException("Not used");
    }

    @Override
    public void deleteRecipe(@NonNull String recipeId) {
      throw new UnsupportedOperationException("Not used");
    }

    @Override
    public @NonNull Recipe importFromJson(@NonNull Path file, @NonNull String collectionName) {
      throw new UnsupportedOperationException("Not used");
    }

    @Override
    public @NonNull List<app.cookyourbooks.conversion.ConversionRule> listHouseConversions() {
      throw new UnsupportedOperationException("Not used");
    }

    @Override
    public void addHouseConversion(
        double fromAmount,
        @NonNull Unit fromUnit,
        @NonNull String ingredientName,
        double toAmount,
        @NonNull Unit toUnit) {
      throw new UnsupportedOperationException("Not used");
    }

    @Override
    public boolean removeHouseConversion(@NonNull String identifier) {
      throw new UnsupportedOperationException("Not used");
    }
  }
}
