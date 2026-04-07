package app.cookyourbooks.gui.viewmodel;

import java.time.Duration;
import java.util.List;

import javafx.animation.PauseTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import org.jspecify.annotations.Nullable;

import app.cookyourbooks.gui.BackgroundTaskRunner;
import app.cookyourbooks.gui.NavigationService;
import app.cookyourbooks.model.Recipe;
import app.cookyourbooks.services.LibrarianService;

/** Implementation of {@link SearchViewModel} for the Search &amp; Filter feature. */
public class SearchViewModelImpl implements SearchViewModel {

  // ── Observable properties ─────────────────────────────────────────────────

  private final StringProperty query = new SimpleStringProperty("");
  private final ObservableList<RecipeResult> results = FXCollections.observableArrayList();
  private final ObservableList<String> ingredientFilters = FXCollections.observableArrayList();
  private final BooleanProperty searching = new SimpleBooleanProperty(false);
  private final StringProperty statusMessage = new SimpleStringProperty("");

  // ── Non-observable state ──────────────────────────────────────────────────

  /** ID of the currently selected result, or null if nothing is selected. */
  private @Nullable String selectedResultId;

  /**
   * Monotonically increasing counter. Each new search increments this. A background task discards
   * its results if the generation it captured at start no longer matches this field.
   */
  private int searchGeneration = 0;

  // ── Dependencies ──────────────────────────────────────────────────────────

  private final LibrarianService librarianService;
  private final NavigationService navigationService;

  /** Timer that fires the actual search 300ms after the user stops typing. */
  private final PauseTransition debounceTimer;

  // ── Result entry type ─────────────────────────────────────────────────────

  /**
   * A lightweight record that holds the recipe ID and title for display in the results list. Using
   * a record here means the View can call {@code result.id()} and {@code result.title()} without
   * depending on the full {@link Recipe} domain object.
   */
  public record RecipeResult(String id, String title) {}

  // ── Constructor ───────────────────────────────────────────────────────────

  /**
   * Creates a new SearchViewModelImpl.
   *
   * @param librarianService provides search and filter operations
   * @param navigationService used to navigate to a selected recipe
   * @param debounceDelay how long to wait after the last keystroke before firing a search; pass
   *     {@code Duration.ofMillis(300)} in production and a shorter value in tests
   */
  public SearchViewModelImpl(
      LibrarianService librarianService,
      NavigationService navigationService,
      Duration debounceDelay) {
    this.librarianService = librarianService;
    this.navigationService = navigationService;

    double delaySeconds = debounceDelay.toMillis() / 1000.0;
    this.debounceTimer = new PauseTransition(javafx.util.Duration.seconds(delaySeconds));
    this.debounceTimer.setOnFinished(event -> executeSearch());
  }

  // ── SearchViewModel interface ─────────────────────────────────────────────

  @Override
  public StringProperty queryProperty() {
    return query;
  }

  @Override
  public ObservableList<RecipeResult> resultsProperty() {
    return results;
  }

  @Override
  public ObservableList<String> ingredientFiltersProperty() {
    return ingredientFilters;
  }

  @Override
  public BooleanProperty searchingProperty() {
    return searching;
  }

  @Override
  public StringProperty statusMessageProperty() {
    return statusMessage;
  }

  // Commands and accessors will be added next.

  @Override
  public void setQuery(String q) {
    query.set(q);
    debounceTimer.playFromStart();
  }

  @Override
  public void addIngredientFilter(String ingredient) {
    if (!ingredientFilters.contains(ingredient)) {
      ingredientFilters.add(ingredient);
    }
    executeSearch();
  }

  @Override
  public void removeIngredientFilter(String ingredient) {
    ingredientFilters.remove(ingredient);
    executeSearch();
  }

  @Override
  public void clearFilters() {
    debounceTimer.stop();
    query.set("");
    ingredientFilters.clear();
    results.clear();
    selectedResultId = null;
    statusMessage.set("");
  }

  @Override
  public void selectNextResult() {
    if (results.isEmpty()) {
      return;
    }
    int currentIndex = indexOfSelected();
    int nextIndex = (currentIndex + 1) % results.size();
    selectedResultId = results.get(nextIndex).id();
  }

  @Override
  public void selectPreviousResult() {
    if (results.isEmpty()) {
      return;
    }
    int currentIndex = indexOfSelected();
    int prevIndex = (currentIndex - 1 + results.size()) % results.size();
    selectedResultId = results.get(prevIndex).id();
  }

  @Override
  public void navigateToSelectedResult() {
    if (selectedResultId == null) {
      return;
    }
    navigationService.navigateToRecipe(selectedResultId);
  }

  @Override
  public String getQuery() {
    return query.get();
  }

  @Override
  public List<String> getResultIds() {
    return results.stream().map(RecipeResult::id).toList();
  }

  @Override
  public List<String> getIngredientFilters() {
    return List.copyOf(ingredientFilters);
  }

  @Override
  public boolean isSearching() {
    return searching.get();
  }

  @Override
  public String getStatusMessage() {
    return statusMessage.get();
  }

  @Override
  public @Nullable String getSelectedResultId() {
    return selectedResultId;
  }

  // ── Private helpers ───────────────────────────────────────────────────────

  /** Called when the debounce timer fires. Runs the actual search on a background thread. */
  @SuppressWarnings("FutureReturnValueIgnored")
  private void executeSearch() {
    searching.set(true);
    statusMessage.set("Searching...");

    // Capture the current generation. If a newer search starts before this one
    // finishes, the generation will no longer match and we discard the stale result.
    final int myGeneration = ++searchGeneration;

    // Snapshot query and filters now (on the FX thread) so the background thread
    // reads stable values even if the user keeps typing.
    final String currentQuery = query.get().trim();
    final List<String> currentFilters = List.copyOf(ingredientFilters);

    BackgroundTaskRunner.run(
        () -> {
          // --- background thread ---
          // S11: empty query → all recipes; non-empty query → title search
          List<Recipe> titleResults =
              currentQuery.isEmpty()
                  ? librarianService.listAllRecipes()
                  : librarianService.resolveRecipes(currentQuery);

          // Apply each ingredient filter (AND logic: keep only recipes in every filter set)
          List<Recipe> filtered = titleResults;
          for (String ingredient : currentFilters) {
            List<Recipe> byIngredient = librarianService.searchByIngredient(ingredient);
            filtered = filtered.stream().filter(byIngredient::contains).toList();
          }
          return filtered;
        },
        recipes -> {
          // --- FX thread (success) ---
          if (myGeneration != searchGeneration) {
            // A newer search already started; discard these stale results.
            return;
          }
          results.setAll(
              recipes.stream().map(r -> new RecipeResult(r.getId(), r.getTitle())).toList());
          selectedResultId = results.isEmpty() ? null : results.get(0).id();
          int count = results.size();
          statusMessage.set(
              count == 0 ? "No results found" : count + " result" + (count == 1 ? "" : "s"));
          searching.set(false);
        },
        error -> {
          // --- FX thread (failure) ---
          if (myGeneration != searchGeneration) {
            return;
          }
          statusMessage.set("Search failed: " + error.getMessage());
          searching.set(false);
        });
  }

  /**
   * Returns the index of the currently selected result in the list, or -1 if nothing is selected.
   */
  private int indexOfSelected() {
    if (selectedResultId == null) {
      return -1;
    }
    for (int i = 0; i < results.size(); i++) {
      if (results.get(i).id().equals(selectedResultId)) {
        return i;
      }
    }
    return -1;
  }
}
