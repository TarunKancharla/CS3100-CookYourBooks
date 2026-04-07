package app.cookyourbooks.gui.viewmodel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.Duration;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import app.cookyourbooks.gui.BackgroundTaskRunner;
import app.cookyourbooks.gui.NavigationService;
import app.cookyourbooks.model.Recipe;
import app.cookyourbooks.model.RecipeCollection;
import app.cookyourbooks.services.LibrarianService;

/** Implementation for the Library View Model. */
@NullMarked
public class LibraryViewModelImpl implements LibraryViewModel {

  /* Services */
  private final LibrarianService librarianService;
  private final NavigationService navigationService;

  /* ViewModel Values */
  private final StringProperty filterTextProperty;
  private final ObservableList<Recipe> recipesProperty;
  private final ObservableList<RecipeCollectionSummary> collectionMeta;
  private final BooleanProperty loadingProperty;
  private final BooleanProperty undoAvailableProperty;
  private final StringProperty undoMessageProperty;

  /* General Values */
  @Nullable private RecipeCollection selectedCollection;
  private final Duration undoWindow;
  private final List<RecipeCollection> recipeCollections;
  private final List<RecipeCollection> recipeCollectionsPendingDelete;

  /* Private Helpers */

  /**
   * Sets the property of whether undo recipe collection deletion is available. If there are recipes
   * marked as deletion-pending, then they can still be undone.
   */
  private void setUndoAvailable() {
    this.undoAvailableProperty.set(!this.recipeCollectionsPendingDelete.isEmpty());

    // set to the oldest deleted recipe
    if (!this.recipeCollectionsPendingDelete.isEmpty()) {
      this.undoMessageProperty.set(
          "Deleted: %s".formatted(this.recipeCollectionsPendingDelete.getFirst().getTitle()));
    }
  }

  private void exposeFilteredCollections() {
    collectionMeta.clear();
    collectionMeta.addAll(
        recipeCollections.stream()
            // any collection that has an id pending deletion should not be shown to the user
            .filter(
                rc ->
                    recipeCollectionsPendingDelete.stream()
                        .noneMatch(rd -> rd.getId().equals(rc.getId())))
            // any collection that does not match filter text should not be shown to the user
            .filter(
                rc ->
                    rc.getTitle()
                        .toUpperCase(Locale.ROOT)
                        .contains(filterTextProperty.get().toUpperCase(Locale.ROOT)))
            .map(
                rc ->
                    new RecipeCollectionSummary(
                        rc.getId(), rc.getTitle(), rc.getSourceType(), rc.getRecipes().size()))
            .toList());
  }

  /* Constructor */
  /**
   * Constructor for the Library View Model implementation. Dependencies should be injected.
   *
   * @param librarianService implementation for the LibrarianService
   * @param navigationService implementation for the NavigationService
   */
  public LibraryViewModelImpl(
      LibrarianService librarianService, NavigationService navigationService, Duration undoWindow) {
    this.librarianService = librarianService;
    this.navigationService = navigationService;
    this.undoWindow = undoWindow;

    // instantiate general values
    recipeCollections = new ArrayList<>();
    recipeCollectionsPendingDelete = new ArrayList<>();
    selectedCollection = null;

    // instantiate ViewModel values
    filterTextProperty = new SimpleStringProperty();
    filterTextProperty.set("");
    loadingProperty = new SimpleBooleanProperty();
    undoAvailableProperty = new SimpleBooleanProperty();
    undoMessageProperty = new SimpleStringProperty();
    collectionMeta = FXCollections.observableArrayList();
    recipesProperty = FXCollections.observableArrayList();

    // update filter text dynamically
    filterTextProperty.addListener(ignored -> this.exposeFilteredCollections());
  }

  /* Observable Properties */
  @Override
  public ObservableList<RecipeCollectionSummary> collectionsProperty() {
    return this.collectionMeta;
  }

  @Override
  public StringProperty filterTextProperty() {
    return this.filterTextProperty;
  }

  @Override
  public ObservableList<Recipe> recipesProperty() {
    return this.recipesProperty;
  }

  @Override
  public BooleanProperty loadingProperty() {
    return this.loadingProperty;
  }

  @Override
  public BooleanProperty undoAvailableProperty() {
    return this.undoAvailableProperty;
  }

  @Override
  public StringProperty undoMessageProperty() {
    return this.undoMessageProperty;
  }

  /* Commands */
  @Override
  public void refresh() {
    loadingProperty.set(true);
    BackgroundTaskRunner.run(
        librarianService::listCollections,
        result -> {
          recipeCollections.clear();
          recipeCollections.addAll(result);
          this.exposeFilteredCollections();
          loadingProperty.set(false);
        },
        err -> {
          // TODO: Handle Loading Error
        });
  }

  @Override
  public void selectCollection(@Nullable String collectionId) {
    recipesProperty.clear();
    selectedCollection = null;

    if (collectionId == null) {
      return;
    } // we can just clear out the selected collection
    Optional<RecipeCollection> recipeCollection = librarianService.findCollectionById(collectionId);

    // should never happen, but just in case
    if (recipeCollection.isEmpty()) {
      // TODO: Handle error
      return;
    }

    // add all recipes summaries
    selectedCollection = recipeCollection.get();
    recipesProperty.addAll(recipeCollection.get().getRecipes());
  }

  @Override
  public void createCollection(String title) {
    if (title.isBlank()) {
      throw new IllegalArgumentException("Title must not be blank.");
    }
    librarianService.createCollection(title);
  }

  @Override
  public void deleteCollection(String collectionId) {
    // delete is not immediate
    // undo window is provided

    // find the collection with that id
    Optional<RecipeCollection> toDelete =
        recipeCollections.stream().filter(rc -> rc.getId().equals(collectionId)).findFirst();

    if (toDelete.isEmpty()) {
      // should never happen
      // TODO: handle error
      return;
    }

    recipeCollectionsPendingDelete.add(toDelete.get());
    setUndoAvailable();
    refresh(); // hide the deletion-pending recipe

    // after 5sec, see if it's still deletion-marked
    BackgroundTaskRunner.run(
        () -> {
          Thread.sleep((long) undoWindow.toMillis() * 1000);
          return undoWindow.toMillis() * 1000;
        },
        (result) -> {
          if (recipeCollectionsPendingDelete.stream()
              .anyMatch(rc -> rc.getId().equals(collectionId))) {
            // fully process the deletion
            recipeCollectionsPendingDelete.removeIf(rc -> rc.getId().equals(collectionId));
            librarianService.deleteCollection(collectionId);
            setUndoAvailable();
          }
        },
        (err) -> {
          // TODO: handle err
        });
  }

  @Override
  public void undoDelete() {
    // the first object in the list was removed the latest
    // remove it from the list so it is no longer deletion-marked
    recipeCollectionsPendingDelete.removeFirst(); // pop
    setUndoAvailable();
    refresh();
  }

  @Override
  public void selectRecipe(String recipeId) {
    Optional<Recipe> selectedRecipe =
        recipesProperty.stream().filter(r -> r.getId().equals(recipeId)).findFirst();

    // should never happen, but just in case
    if (selectedRecipe.isEmpty()) {
      // TODO: handle error
      return;
    }

    // open in recipe view
    navigationService.navigateToRecipe(recipeId);
  }

  /* Non-JavaFX accessors */
  public List<String> getCollectionIds() {
    return this.collectionMeta.stream().map(RecipeCollectionSummary::id).toList();
  }

  public @Nullable String getSelectedCollectionId() {
    return selectedCollection == null ? null : selectedCollection.getId();
  }

  public List<String> getRecipeIds() {
    return this.recipesProperty.stream().map(Recipe::getId).toList();
  }

  public boolean isLoading() {
    return this.loadingProperty.get();
  }

  public boolean isUndoAvailable() {
    return this.undoAvailableProperty.get();
  }

  @Override
  public String getFilterText() {
    return this.filterTextProperty.get();
  }
}
