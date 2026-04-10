package app.cookyourbooks.gui;

import static org.mockito.Mockito.*;
import static org.testfx.assertions.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.util.Duration;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import app.cookyourbooks.gui.viewmodel.*;
import app.cookyourbooks.model.PersonalCollectionImpl;
import app.cookyourbooks.model.Recipe;
import app.cookyourbooks.model.RecipeCollection;
import app.cookyourbooks.model.Servings;
import app.cookyourbooks.repository.RecipeRepository;
import app.cookyourbooks.services.LibrarianService;
import app.cookyourbooks.services.ocr.FakeRecipeOcrService;

/** Class containing tests to verify behavior among features is correct. */
@NullMarked
public class IntegrationTests extends ViewModelTestBase {

  private LibrarianService mockLibrarianService;
  private NavigationService navigationService;
  private LibraryViewModel libraryViewModel;
  private ImportViewModel importViewModel;
  private SearchViewModel searchViewModel;
  private RecipeEditorViewModel recipeEditorViewModel;
  private RecipeRepository recipeRepository;
  private StringProperty selectedRecipeIdProperty;

  // Helper methods
  Recipe makeRecipe(String id, String title) {
    return new Recipe(id, title, new Servings(10), List.of(), List.of(), List.of());
  }

  RecipeCollection makeCollection(String id, String title, @Nullable List<Recipe> recipes) {
    return PersonalCollectionImpl.builder()
        .id(id)
        .title(title)
        .recipes(recipes == null ? List.of() : recipes)
        .build();
  }

  @BeforeEach
  void beforeEach() {
    this.mockLibrarianService = mock(LibrarianService.class);
    this.navigationService = mock(NavigationService.class);

    this.selectedRecipeIdProperty = new SimpleStringProperty();
    when(navigationService.selectedRecipeIdProperty()).thenAnswer((s) -> selectedRecipeIdProperty);

    this.libraryViewModel =
        new LibraryViewModelImpl(mockLibrarianService, navigationService, Duration.seconds(5));
    this.importViewModel =
        new ImportViewModelImpl(new FakeRecipeOcrService(500), mockLibrarianService);
    this.searchViewModel =
        new SearchViewModelImpl(
            mockLibrarianService, navigationService, java.time.Duration.ofMillis(50));
    this.recipeRepository = new InMemoryRecipeRepository(makeRecipe("r-default", "Default Recipe"));
    this.recipeEditorViewModel =
        new RecipeEditorViewModelImpl(this.recipeRepository, navigationService);
  }

  @Test
  @DisplayName("Selecting recipe in library editor opens it up in recipe editor")
  void libraryEditorToRecipeEditor() {

    // mocks: respond with proper recipe collection and recipe when asked
    var recipe = makeRecipe("r-1", "Test Recipe");
    var collection = makeCollection("c-1", "Test Collection", List.of(recipe));
    this.recipeRepository.save(recipe);
    when(mockLibrarianService.listCollections()).thenReturn(List.of(collection));
    when(mockLibrarianService.findCollectionById(collection.getId()))
        .thenReturn(Optional.of(collection));
    doAnswer(
            invocation -> {
              String id = invocation.getArgument(0);
              selectedRecipeIdProperty.set(id);
              return null;
            })
        .when(navigationService)
        .navigateToRecipe(anyString());

    libraryViewModel.refresh();
    WaitForAsyncUtils.waitForFxEvents();
    Platform.runLater(() -> libraryViewModel.selectCollection(collection.getId()));
    WaitForAsyncUtils.waitForFxEvents();
    Platform.runLater(() -> libraryViewModel.selectRecipe(recipe.getId()));
    WaitForAsyncUtils.waitForFxEvents();

    // check to see that its loaded in the recipe editor
    verify(navigationService).navigateToRecipe(recipe.getId());
    assertThat(recipeEditorViewModel.getRecipeId()).isEqualTo(recipe.getId());
    assertThat(recipeEditorViewModel.getTitle()).isEqualTo(recipe.getTitle());
  }

  @Test
  @DisplayName("Selecting a recipe in search + filter opens it up in recipe editor")
  void searchFilterToRecipeEditor() throws Exception {
    doAnswer(
            invocation -> {
              String id = invocation.getArgument(0);
              selectedRecipeIdProperty.set(id);
              return null;
            })
        .when(navigationService)
        .navigateToRecipe(anyString());

    Recipe recipe = makeRecipe("r-1", "Pasta");
    this.recipeRepository.save(recipe);

    when(mockLibrarianService.resolveRecipes(anyString())).thenReturn(List.of(recipe));
    when(mockLibrarianService.listAllRecipes()).thenReturn(List.of(recipe));

    searchViewModel.setQuery("pasta");
    Thread.sleep(150);
    WaitForAsyncUtils.waitForFxEvents();

    assertThat(searchViewModel.getResultIds()).containsExactly("r-1");

    searchViewModel.selectNextResult();
    WaitForAsyncUtils.waitForFxEvents();
    assertThat(searchViewModel.getSelectedResultId()).isEqualTo("r-1");

    searchViewModel.navigateToSelectedResult();
    WaitForAsyncUtils.waitForFxEvents();

    verify(navigationService).navigateToRecipe("r-1");
    assertThat(recipeEditorViewModel.getRecipeId()).isEqualTo("r-1");
    assertThat(recipeEditorViewModel.getTitle()).isEqualTo("Pasta");
  }

  @Test
  @DisplayName("Importing a recipe in import service opens it up in recipe editor")
  void importToRecipeEditor() throws Exception {
    doAnswer(
            invocation -> {
              String id = invocation.getArgument(0);
              selectedRecipeIdProperty.set(id);
              return null;
            })
        .when(navigationService)
        .navigateToRecipe(anyString());

    RecipeCollection collection = makeCollection("c-1", "Test Collection", List.of());
    when(mockLibrarianService.listCollections()).thenReturn(List.of(collection));

    // Save imported recipe into repo, then navigate to it (integration flow).
    doAnswer(
            invocation -> {
              Recipe saved = invocation.getArgument(0);
              this.recipeRepository.save(saved);
              navigationService.navigateToRecipe(saved.getId());
              return null;
            })
        .when(mockLibrarianService)
        .saveRecipe(any(Recipe.class), anyString());

    importViewModel.loadCollections();
    WaitForAsyncUtils.waitFor(
        2, TimeUnit.SECONDS, () -> !importViewModel.getAvailableCollectionIds().isEmpty());

    importViewModel.selectTargetCollection("c-1");
    importViewModel.startImport(java.nio.file.Path.of("test.jpg"));

    // Wait until OCR finishes; acceptImport() is a no-op unless state == review.
    WaitForAsyncUtils.waitFor(
        3, TimeUnit.SECONDS, () -> "review".equals(importViewModel.getState()));

    importViewModel.importedTitleProperty().set("Imported Recipe");
    importViewModel.acceptImport();
    WaitForAsyncUtils.waitForFxEvents();

    verify(mockLibrarianService).saveRecipe(any(Recipe.class), eq("c-1"));
    verify(navigationService).navigateToRecipe(anyString());
    assertThat(recipeEditorViewModel.getRecipeId()).isNotNull();
    assertThat(recipeEditorViewModel.getTitle()).isEqualTo("Imported Recipe");
  }
}
