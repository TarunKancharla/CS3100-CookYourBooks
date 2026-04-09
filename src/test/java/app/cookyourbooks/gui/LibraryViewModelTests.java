package app.cookyourbooks.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javafx.application.Platform;
import javafx.util.Duration;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import app.cookyourbooks.gui.viewmodel.LibraryViewModel;
import app.cookyourbooks.gui.viewmodel.LibraryViewModelImpl;
import app.cookyourbooks.gui.viewmodel.RecipeCollectionSummary;
import app.cookyourbooks.model.PersonalCollectionImpl;
import app.cookyourbooks.model.Recipe;
import app.cookyourbooks.model.RecipeCollection;
import app.cookyourbooks.model.Servings;
import app.cookyourbooks.services.LibrarianService;

/**
 * Contains test for the ViewModel for the Library View Feature. Requirements to grading are listed
 */
@DisplayName("LibraryViewModelTests")
@NullMarked
public class LibraryViewModelTests {

  private LibrarianService mockLibrarianService;
  private NavigationService navigationService;
  private LibraryViewModel libraryViewModel;

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
    this.libraryViewModel =
        new LibraryViewModelImpl(mockLibrarianService, navigationService, Duration.seconds(5));
  }

  @BeforeAll
  static void initJfx() {
    javafx.application.Platform.startup(() -> {});
  }

  @Test
  @DisplayName(
      "L1\trefresh() loads collections from the service layer and populates the observable list")
  void refreshLoadsIntoList() throws TimeoutException {
    RecipeCollection rc1 = makeCollection("rc-1", "rc-1", null);
    RecipeCollection rc2 = makeCollection("rc-2", "rc-2", null);

    when(mockLibrarianService.listCollections()).thenReturn(List.of(rc1, rc2));

    libraryViewModel.refresh();
    WaitForAsyncUtils.waitFor(
        10, TimeUnit.SECONDS, () -> !libraryViewModel.loadingProperty().get());
    assertThat(new ArrayList<>(libraryViewModel.collectionsProperty()))
        .isEqualTo(List.of(RecipeCollectionSummary.of(rc1), RecipeCollectionSummary.of(rc2)));
  }

  @Test
  @DisplayName("L2\tEach collection entry exposes ID, title, source type, and recipe count")
  void collectionExposesMetadata() throws TimeoutException {
    RecipeCollection rc1 = makeCollection("rc-1", "rc-1", List.of(makeRecipe("r-1", "r-1")));
    RecipeCollection rc2 = makeCollection("rc-2", "rc-2", null);

    // verify with different recipe sizes

    when(mockLibrarianService.listCollections()).thenReturn(List.of(rc1, rc2));

    libraryViewModel.refresh();
    WaitForAsyncUtils.waitFor(
        10, TimeUnit.SECONDS, () -> !libraryViewModel.loadingProperty().get());
    assertThat(new ArrayList<>(libraryViewModel.collectionsProperty()))
        .isEqualTo(List.of(RecipeCollectionSummary.of(rc1), RecipeCollectionSummary.of(rc2)));
  }

  @Test
  @DisplayName(
      "L3\tselectCollection(id) updates the selected collection and populates the recipe list")
  void selectCollectionPopulatesRecipeList() throws TimeoutException {
    // create dummy recipes and collections
    Recipe r1 = makeRecipe("r-1", "Recipe 1");
    Recipe r2 = makeRecipe("r-2", "Recipe 2");
    Recipe r3 = makeRecipe("r-3", "Recipe 3");

    RecipeCollection rc1 = makeCollection("rc-1", "Collection 1", List.of(r1, r2, r3));

    // ensure the recipe list is empty
    when(mockLibrarianService.findCollectionById("rc-1")).thenReturn(Optional.of(rc1));
    assertThat(libraryViewModel.getRecipeIds()).isEqualTo(List.of());
    libraryViewModel.selectCollection("rc-1");
    assertThat(new ArrayList<>(libraryViewModel.recipesProperty())).isEqualTo(List.of(r1, r2, r3));
  }

  @Test
  @DisplayName("L4\tcreateCollection(title) adds a new collection and it appears after refresh")
  void createCollectionCreatesCollection() throws TimeoutException {

    RecipeCollection rc3 = makeCollection("rc-3", "rc-3", null);
    when(mockLibrarianService.listCollections()).thenReturn(List.of(), List.of(rc3));

    // verify the collection is not already in the observable list
    libraryViewModel.refresh();
    WaitForAsyncUtils.waitFor(
        10, TimeUnit.SECONDS, () -> !libraryViewModel.loadingProperty().get());
    assertThat(new ArrayList<>(libraryViewModel.collectionsProperty())).isEqualTo(List.of());

    // confirm the librarian service is called to add the collection, then mock the listCollections
    // method to return the new collection
    libraryViewModel.createCollection("rc-3");
    verify(mockLibrarianService).createCollection("rc-3");

    // assert the new collection appears
    libraryViewModel.refresh();
    WaitForAsyncUtils.waitFor(
        10, TimeUnit.SECONDS, () -> !libraryViewModel.loadingProperty().get());
    assertThat(new ArrayList<>(libraryViewModel.collectionsProperty()))
        .isEqualTo(List.of(RecipeCollectionSummary.of(rc3)));
  }

  @Test
  @DisplayName("L5\tdeleteCollection(id) removes the collection (after undo timeout expires)")
  void deleteCollectionRemovesCollection() throws TimeoutException {

    RecipeCollection rc5 = makeCollection("rc-5", "rc-5", null);

    // First call: collection exists
    // Second call (after delete): collection is gone
    when(mockLibrarianService.listCollections()).thenReturn(List.of(rc5), List.of());

    // Load initial state
    Platform.runLater(() -> libraryViewModel.refresh());
    WaitForAsyncUtils.waitFor(
        10, TimeUnit.SECONDS, () -> !libraryViewModel.loadingProperty().get());

    long startTime = System.currentTimeMillis();

    // Call delete on FX thread
    Platform.runLater(() -> libraryViewModel.deleteCollection("rc-5"));
    WaitForAsyncUtils.waitForFxEvents();

    // Wait until undo expires (should be ~5 seconds)
    WaitForAsyncUtils.waitFor(
        6, TimeUnit.SECONDS, () -> !libraryViewModel.undoAvailableProperty().get());

    // Verify backend delete was called
    verify(mockLibrarianService).deleteCollection("rc-5");

    // Ensure at least 5 seconds passed
    assertThat(System.currentTimeMillis() - startTime).isGreaterThanOrEqualTo(5 * 1000);
  }

  @Test
  @DisplayName(
      "L6\tAfter delete, undo is available for 5 seconds; undoDelete() restores the collection")
  void undoWorks() throws TimeoutException {

    RecipeCollection rc5 = makeCollection("rc-5", "rc-5", null);

    when(mockLibrarianService.listCollections())
        .thenReturn(List.of(rc5), List.of()); // after delete it's gone

    // initial load
    Platform.runLater(() -> libraryViewModel.refresh());
    WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !libraryViewModel.loadingProperty().get());

    // delete
    Platform.runLater(() -> libraryViewModel.deleteCollection("rc-5"));
    WaitForAsyncUtils.waitForFxEvents();

    assertThat(libraryViewModel.undoAvailableProperty().get()).isTrue();

    // undo before timeout
    Platform.runLater(() -> libraryViewModel.undoDelete());
    WaitForAsyncUtils.waitForFxEvents();

    assertThat(libraryViewModel.undoAvailableProperty().get()).isFalse();

    // should NOT call backend delete
    verify(mockLibrarianService, never()).deleteCollection("rc-5");
  }

  @Test
  @DisplayName("L7\tUndo state clears after the 5-second timeout")
  void undoClearsAfterTimeout() throws TimeoutException {

    RecipeCollection rc5 = makeCollection("rc-5", "rc-5", null);

    when(mockLibrarianService.listCollections()).thenReturn(List.of(rc5), List.of());

    Platform.runLater(() -> libraryViewModel.refresh());
    WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !libraryViewModel.loadingProperty().get());

    Platform.runLater(() -> libraryViewModel.deleteCollection("rc-5"));
    WaitForAsyncUtils.waitForFxEvents();

    assertThat(libraryViewModel.undoAvailableProperty().get()).isTrue();

    // wait past timeout
    WaitForAsyncUtils.waitFor(
        6, TimeUnit.SECONDS, () -> !libraryViewModel.undoAvailableProperty().get());

    assertThat(libraryViewModel.undoAvailableProperty().get()).isFalse();
  }

  @Test
  @DisplayName(
      "L8\trefresh() runs on a background thread; loading indicator is true while fetching")
  void refreshRunsOnBackgroundWithLoadingIndicator() throws TimeoutException {

    when(mockLibrarianService.listCollections())
        .thenAnswer(
            invocation -> {
              Thread.sleep(1000); // simulate delay
              return List.of();
            });

    Platform.runLater(() -> libraryViewModel.refresh());
    WaitForAsyncUtils.waitForFxEvents();

    // should be loading during fetch
    assertThat(libraryViewModel.loadingProperty().get()).isTrue();

    // wait until done
    WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !libraryViewModel.loadingProperty().get());

    assertThat(libraryViewModel.loadingProperty().get()).isFalse();
  }

  @Test
  @DisplayName(
      "L9\tSelecting a collection then selecting a recipe provides the recipe ID for navigation")
  void selectingRecipeProvidesIdForNavigation() throws TimeoutException {

    Recipe r1 = makeRecipe("r-1", "Recipe 1");
    RecipeCollection rc1 = makeCollection("rc-1", "rc-1", List.of(r1));

    when(mockLibrarianService.listCollections()).thenReturn(List.of(rc1));
    when(mockLibrarianService.findCollectionById("rc-1")).thenReturn(Optional.of(rc1));

    Platform.runLater(() -> libraryViewModel.refresh());
    WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !libraryViewModel.loadingProperty().get());

    Platform.runLater(() -> libraryViewModel.selectCollection("rc-1"));
    WaitForAsyncUtils.waitForFxEvents();

    Platform.runLater(() -> libraryViewModel.selectRecipe("r-1"));
    WaitForAsyncUtils.waitForFxEvents();

    verify(navigationService).navigateToRecipe("r-1");
  }

  @Test
  @DisplayName("L10\tEdge case: selecting a nonexistent collection ID is handled gracefully")
  void selectingNonexistentCollectionIsHandled() throws TimeoutException {
    when(mockLibrarianService.findCollectionById("missing")).thenReturn(Optional.empty());

    Platform.runLater(() -> libraryViewModel.selectCollection("missing"));
    WaitForAsyncUtils.waitForFxEvents();

    // Since the collection doesn't exist, the selected collection should still be null
    assertThat(libraryViewModel.getSelectedCollectionId()).isNull();

    // Recipes list should be empty
    assertThat(libraryViewModel.recipesProperty()).isEmpty();
  }

  @Test
  @DisplayName(
      "L11\tfilterTextProperty() filters collections by title (case-insensitive substring match)")
  void filterTextWorks() throws TimeoutException {
    RecipeCollection rc1 = makeCollection("rc-1", "Apple Pie", null);
    RecipeCollection rc2 = makeCollection("rc-2", "Banana Bread", null);
    RecipeCollection rc3 = makeCollection("rc-3", "Cherry Tart", null);

    when(mockLibrarianService.listCollections()).thenReturn(List.of(rc1, rc2, rc3));

    Platform.runLater(() -> libraryViewModel.refresh());
    WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !libraryViewModel.loadingProperty().get());

    Platform.runLater(() -> libraryViewModel.filterTextProperty().set("pie"));
    WaitForAsyncUtils.waitForFxEvents();
    assertThat(libraryViewModel.getCollectionIds()).containsExactly("rc-1");

    Platform.runLater(() -> libraryViewModel.filterTextProperty().set("BREAD"));
    WaitForAsyncUtils.waitForFxEvents();
    assertThat(libraryViewModel.getCollectionIds()).containsExactly("rc-2");

    Platform.runLater(() -> libraryViewModel.filterTextProperty().set(" "));
    WaitForAsyncUtils.waitForFxEvents();
    assertThat(libraryViewModel.getCollectionIds()).containsExactly("rc-1", "rc-2", "rc-3");
  }

  @Test
  @DisplayName(
      "L12\tFiltered list updates immediately as the user types (no debounce — it's in-memory)")
  void filteredListUpdatesImmediatelyUponTyping() throws TimeoutException {
    RecipeCollection rc1 = makeCollection("rc-1", "Alpha", null);
    RecipeCollection rc2 = makeCollection("rc-2", "Bete", null);
    RecipeCollection rc3 = makeCollection("rc-3", "Gamma", null);

    when(mockLibrarianService.listCollections()).thenReturn(List.of(rc1, rc2, rc3));

    Platform.runLater(() -> libraryViewModel.refresh());
    WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !libraryViewModel.loadingProperty().get());

    Platform.runLater(() -> libraryViewModel.filterTextProperty().set("a"));
    WaitForAsyncUtils.waitForFxEvents();
    assertThat(libraryViewModel.getCollectionIds()).containsExactlyInAnyOrder("rc-1", "rc-3");

    Platform.runLater(() -> libraryViewModel.filterTextProperty().set("al"));
    WaitForAsyncUtils.waitForFxEvents();
    assertThat(libraryViewModel.getCollectionIds()).containsExactly("rc-1");
  }

  @Test
  @DisplayName(
      "L13\tUndo-delete works correctly with an active filter (restored collection reappears only if it matches the current filter; clearing the filter always shows all collections)")
  void collectionOnlyReappearsAfterUndoIfMatchingFilter() throws TimeoutException {
    RecipeCollection rc1 = makeCollection("rc-1", "Apple", null);
    RecipeCollection rc2 = makeCollection("rc-2", "Banana", null);

    when(mockLibrarianService.listCollections()).thenReturn(List.of(rc1, rc2));

    // initial load
    Platform.runLater(() -> libraryViewModel.refresh());
    WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !libraryViewModel.loadingProperty().get());

    // delete rc1
    Platform.runLater(() -> libraryViewModel.deleteCollection("rc-1"));
    WaitForAsyncUtils.waitForFxEvents();
    assertThat(libraryViewModel.undoAvailableProperty().get()).isTrue();

    // set a filter that hides rc1
    Platform.runLater(() -> libraryViewModel.filterTextProperty().set("Banana"));
    WaitForAsyncUtils.waitForFxEvents();
    assertThat(libraryViewModel.getCollectionIds()).containsExactly("rc-2");

    // undo delete
    Platform.runLater(() -> libraryViewModel.undoDelete());
    WaitForAsyncUtils.waitForFxEvents();

    // rc1 still hidden because it doesn't match filter
    assertThat(libraryViewModel.getCollectionIds()).containsExactly("rc-2");

    // clear filter, rc1 should reappear
    Platform.runLater(() -> libraryViewModel.filterTextProperty().set(""));
    WaitForAsyncUtils.waitForFxEvents();
    assertThat(libraryViewModel.getCollectionIds()).containsExactlyInAnyOrder("rc-1", "rc-2");
  }
}
