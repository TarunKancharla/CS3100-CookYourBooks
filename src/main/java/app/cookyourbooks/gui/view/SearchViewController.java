package app.cookyourbooks.gui.view;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;

import app.cookyourbooks.gui.viewmodel.SearchViewModel;
import app.cookyourbooks.gui.viewmodel.SearchViewModelImpl.RecipeResult;

/**
 * FXML controller for {@code SearchView.fxml}.
 *
 * <p>Binds the View's controls to the {@link SearchViewModel}. All user interactions are delegated
 * to the ViewModel — this controller contains no business logic.
 */
@SuppressWarnings("NullAway.Init") // FXML fields are injected by FXMLLoader, not the constructor
public class SearchViewController {

  // ── FXML-injected controls ────────────────────────────────────────────────

  @FXML private TextField searchField;
  @FXML private ProgressIndicator loadingSpinner;
  @FXML private TextField ingredientField;
  @FXML private Button addFilterButton;
  @FXML private ListView<String> filterList;
  @FXML private Button removeFilterButton;
  @FXML private Button clearFiltersButton;
  @FXML private ListView<RecipeResult> resultsList;
  @FXML private Label statusLabel;

  // ── ViewModel ─────────────────────────────────────────────────────────────

  private final SearchViewModel viewModel;

  /**
   * Constructs the controller with its ViewModel.
   *
   * @param viewModel the ViewModel for this view
   */
  public SearchViewController(SearchViewModel viewModel) {
    this.viewModel = viewModel;
  }

  // ── Initialization ────────────────────────────────────────────────────────

  /** Called by FXMLLoader after all @FXML fields are injected. Wires controls to the ViewModel. */
  @SuppressWarnings("UnusedMethod") // Called reflectively by FXMLLoader
  @FXML
  private void initialize() {
    // ── Search field ──────────────────────────────────────────────────────
    // Call setQuery on every keystroke so the debounce timer restarts each time.
    searchField.textProperty().addListener((obs, oldVal, newVal) -> viewModel.setQuery(newVal));

    // Keyboard navigation: up/down arrows move selection in results list.
    searchField.setOnKeyPressed(
        event -> {
          if (event.getCode() == KeyCode.DOWN) {
            viewModel.selectNextResult();
            syncListSelection();
            event.consume();
          } else if (event.getCode() == KeyCode.UP) {
            viewModel.selectPreviousResult();
            syncListSelection();
            event.consume();
          } else if (event.getCode() == KeyCode.ENTER) {
            viewModel.navigateToSelectedResult();
            event.consume();
          }
        });

    // ── Loading spinner ───────────────────────────────────────────────────
    // visible AND managed are both bound so the spinner doesn't take up space when hidden.
    loadingSpinner.visibleProperty().bind(viewModel.searchingProperty());
    loadingSpinner.managedProperty().bind(viewModel.searchingProperty());

    // ── Results list ──────────────────────────────────────────────────────
    // The interface declares ObservableList<?> but our impl always returns
    // ObservableList<RecipeResult>.
    @SuppressWarnings("unchecked")
    var typedResults =
        (javafx.collections.ObservableList<RecipeResult>) viewModel.resultsProperty();
    resultsList.setItems(typedResults);
    resultsList.setCellFactory(
        list ->
            new javafx.scene.control.ListCell<>() {
              @Override
              protected void updateItem(RecipeResult item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                  setText(null);
                } else {
                  setText(
                      item.title()
                          + "  ("
                          + item.ingredientCount()
                          + " ingredients, "
                          + item.instructionCount()
                          + " steps)");
                }
              }
            });

    // When the user clicks a result, update the ViewModel's selected ID.
    resultsList
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (obs, oldVal, newVal) -> {
              if (newVal != null) {
                // Sync keyboard selection state in ViewModel by navigating to this index
                int index = resultsList.getSelectionModel().getSelectedIndex();
                // Bring ViewModel selection in sync with ListView selection
                syncViewModelSelection(index);
              }
            });

    // Double-click on a result navigates to it.
    resultsList.setOnMouseClicked(
        event -> {
          if (event.getClickCount() == 2) {
            viewModel.navigateToSelectedResult();
          }
        });

    // ── Ingredient filter list ────────────────────────────────────────────
    filterList.setItems(viewModel.ingredientFiltersProperty());

    // Add filter on button click or Enter key in the ingredient field.
    addFilterButton.setOnAction(e -> addIngredientFilter());
    ingredientField.setOnKeyPressed(
        event -> {
          if (event.getCode() == KeyCode.ENTER) {
            addIngredientFilter();
          }
        });

    // Remove selected filter.
    removeFilterButton.setOnAction(
        e -> {
          String selected = filterList.getSelectionModel().getSelectedItem();
          if (selected != null) {
            viewModel.removeIngredientFilter(selected);
          }
        });

    // Clear all filters and query.
    clearFiltersButton.setOnAction(e -> viewModel.clearFilters());

    // ── Status label ──────────────────────────────────────────────────────
    statusLabel.textProperty().bind(viewModel.statusMessageProperty());
  }

  // ── Private helpers ───────────────────────────────────────────────────────

  /** Reads the ingredient field, calls addIngredientFilter, and clears the field. */
  private void addIngredientFilter() {
    String ingredient = ingredientField.getText().trim();
    if (!ingredient.isEmpty()) {
      viewModel.addIngredientFilter(ingredient);
      ingredientField.clear();
    }
  }

  /**
   * After arrow-key navigation updates the ViewModel's selected ID, this syncs the ListView's
   * visual selection to match.
   */
  private void syncListSelection() {
    String selectedId = viewModel.getSelectedResultId();
    if (selectedId == null) {
      return;
    }
    for (int i = 0; i < resultsList.getItems().size(); i++) {
      if (resultsList.getItems().get(i).id().equals(selectedId)) {
        resultsList.getSelectionModel().select(i);
        resultsList.scrollTo(i);
        return;
      }
    }
  }

  /**
   * Syncs the ViewModel's selected result ID to match the ListView's selection at the given index.
   * Called when the user clicks a result.
   */
  private void syncViewModelSelection(int index) {
    if (index >= 0 && index < resultsList.getItems().size()) {
      // Drive selection through the ViewModel's keyboard navigation to keep state consistent.
      // We do this by calling selectNextResult/selectPreviousResult until we reach the index.
      // Simpler approach: directly update via a package-visible method isn't available,
      // so we navigate to match the clicked index relative to current selection.
      String clickedId = resultsList.getItems().get(index).id();
      // Walk ViewModel selection to match — find current index and step toward target
      String currentId = viewModel.getSelectedResultId();
      if (clickedId.equals(currentId)) {
        return;
      }
      // Step through results until ViewModel selection matches the clicked item
      for (int steps = 0; steps < resultsList.getItems().size(); steps++) {
        viewModel.selectNextResult();
        if (clickedId.equals(viewModel.getSelectedResultId())) {
          return;
        }
      }
    }
  }
}
