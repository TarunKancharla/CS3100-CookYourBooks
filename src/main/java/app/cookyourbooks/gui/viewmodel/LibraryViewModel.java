package app.cookyourbooks.gui.viewmodel;

import java.util.List;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;

import org.jspecify.annotations.Nullable;

/**
 * ViewModel interface for the Library View feature.
 *
 * <p>Browse and manage the user's recipe collections. This ViewModel exposes the list of
 * collections, handles selection, creation, deletion (with undo), and async loading.
 *
 * <h2>Observable list types</h2>
 *
 * <p>Methods that return {@code ObservableList<?>} let you choose your own entry type. For example,
 * {@link #collectionsProperty()} might return {@code ObservableList<RecipeCollectionSummary>} where
 * {@code RecipeCollectionSummary} is a record you define. Each collection entry should make the
 * collection's <b>ID, title, source type, and recipe count</b> accessible to the View.
 *
 * <h2>Grading contract</h2>
 *
 * <p>Grading tests call the non-JavaFX accessors (methods returning plain Java types like {@code
 * List<String>} and {@code boolean}). Make sure these return the correct state.
 *
 * <h2>Requirement mapping</h2>
 *
 * <ul>
 *   <li><b>L1:</b> {@link #refresh()} loads collections from the service layer
 *   <li><b>L2:</b> Each collection entry exposes ID, title, source type, recipe count
 *   <li><b>L3:</b> {@link #selectCollection(String)} updates selected collection and recipe list
 *   <li><b>L4:</b> {@link #createCollection(String)} adds a new collection
 *   <li><b>L5:</b> {@link #deleteCollection(String)} removes a collection (after undo timeout)
 *   <li><b>L6:</b> {@link #undoDelete()} restores the most recently deleted collection
 *   <li><b>L7:</b> Undo state clears after the 5-second timeout
 *   <li><b>L8:</b> {@link #refresh()} runs on a background thread; loading indicator is true while
 *       fetching
 *   <li><b>L9:</b> Selecting a recipe provides the recipe ID for navigation
 *   <li><b>L10:</b> Selecting a nonexistent collection ID is handled gracefully
 *   <li><b>L11:</b> {@link #filterTextProperty()} filters collections by title (case-insensitive
 *       substring)
 *   <li><b>L12:</b> Filtered list updates immediately as the user types (no debounce)
 *   <li><b>L13:</b> Undo-delete works correctly with an active filter
 * </ul>
 */
public interface LibraryViewModel {

  // ──────────────────────────────────────────────────────────────────────────
  // Observable properties (for JavaFX binding in the View)
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * The list of collections, filtered by the current {@link #filterTextProperty() filter text}.
   * Each entry should expose the collection's ID, title, source type, and recipe count. You choose
   * the entry type.
   */
  ObservableList<?> collectionsProperty();

  /** The filter text for narrowing the collection list by title (case-insensitive substring). */
  StringProperty filterTextProperty();

  /**
   * The list of recipes in the currently selected collection. Each entry should expose the recipe's
   * ID and title. You choose the entry type.
   */
  ObservableList<?> recipesProperty();

  /** Whether collections are currently being loaded from the service layer. */
  BooleanProperty loadingProperty();

  /** Whether an undo action is currently available (true for 5 seconds after a delete). */
  BooleanProperty undoAvailableProperty();

  /** A human-readable message describing what can be undone (e.g., "Deleted: My Recipes"). */
  StringProperty undoMessageProperty();

  // ──────────────────────────────────────────────────────────────────────────
  // Commands (user actions)
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Loads (or reloads) the collection list from the service layer.
   *
   * <p>Must run on a background thread via {@code BackgroundTaskRunner}. While loading, {@link
   * #loadingProperty()} must be {@code true}.
   */
  void refresh();

  /**
   * Selects a collection by ID, populating the recipe list for that collection.
   *
   * @param collectionId the ID of the collection to select
   */
  void selectCollection(String collectionId);

  /**
   * Creates a new collection with the given title.
   *
   * @param title the title for the new collection (must not be blank)
   */
  void createCollection(String title);

  /**
   * Deletes a collection by ID. The delete is not permanent immediately — an undo window of 5
   * seconds is provided. If {@link #undoDelete()} is not called within that window, the delete
   * becomes permanent.
   *
   * @param collectionId the ID of the collection to delete
   */
  void deleteCollection(String collectionId);

  /**
   * Restores the most recently deleted collection (if within the 5-second undo window).
   *
   * <p>After calling this, {@link #undoAvailableProperty()} becomes {@code false}.
   */
  void undoDelete();

  /**
   * Selects a recipe from the currently selected collection, making its ID available for
   * navigation.
   *
   * @param recipeId the ID of the recipe to select
   */
  void selectRecipe(String recipeId);

  // ──────────────────────────────────────────────────────────────────────────
  // Non-JavaFX accessors (for grading tests)
  // ──────────────────────────────────────────────────────────────────────────

  /** Returns the IDs of all collections currently in the list. */
  List<String> getCollectionIds();

  /** Returns the ID of the currently selected collection, or null if none. */
  @Nullable String getSelectedCollectionId();

  /** Returns the IDs of recipes in the currently selected collection. */
  List<String> getRecipeIds();

  /** Returns whether collections are currently loading. */
  boolean isLoading();

  /** Returns whether an undo action is available. */
  boolean isUndoAvailable();

  /** Returns the current filter text. */
  String getFilterText();
}
