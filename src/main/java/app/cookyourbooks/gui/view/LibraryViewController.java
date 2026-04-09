package app.cookyourbooks.gui.view;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import org.jspecify.annotations.Nullable;

import app.cookyourbooks.gui.DialogHandler;
import app.cookyourbooks.gui.viewmodel.LibraryViewModel;
import app.cookyourbooks.gui.viewmodel.RecipeCollectionSummary;
import app.cookyourbooks.model.Recipe;

/** Controller for the Library View Model. */
public class LibraryViewController {

  private final LibraryViewModel libraryViewModel;

  private final ObservableList<RecipeCollectionSummary> collectionsProperty;
  private final StringProperty filterTextProperty;
  private final ObservableList<Recipe> recipesProperty;
  private final BooleanProperty loadingProperty;
  private final BooleanProperty undoAvailableProperty;
  private final StringProperty undoMessageProperty;

  @Nullable @FXML private ListView<RecipeCollectionSummary> collectionsCardsContainer;
  @Nullable @FXML private Button newButton;
  @Nullable @FXML private Button saveNewCollectionButton;
  @Nullable @FXML private TextField collectionNameEntry;
  @Nullable @FXML private Button undoDeleteButton;
  @Nullable @FXML private Button deleteButton;
  @Nullable @FXML private Label loadingLabel;
  @Nullable @FXML private ScrollPane collectionsPane;
  @Nullable @FXML private TextField filterTextField;
  @Nullable @FXML private ListView<Recipe> recipesCardsContainer;

  private boolean deleteMode = false;

  /** Adds bindings on change for each property. */
  private void addBindings() {
    loadingProperty.addListener(
        change -> {
          if (loadingLabel == null || collectionsPane == null) {
            return;
          }

          loadingLabel.setVisible(loadingProperty.get());
          collectionsPane.setVisible(!loadingProperty.get());
        });

    undoAvailableProperty.addListener(
        change -> {
          if (undoDeleteButton == null) {
            return;
          }
          undoDeleteButton.setVisible(undoAvailableProperty.get());
        });

    System.out.println(undoMessageProperty.toString());
  }

  /** Adds bindings that work when a button is clicked. */
  private void addButtonBindings() {
    if (newButton == null) {
      throw new IllegalStateException("New collection button missing.");
    }
    if (deleteButton == null) {
      throw new IllegalStateException("Delete collection button missing.");
    }
    if (undoDeleteButton == null) {
      throw new IllegalStateException("Undo delete collection button missing.");
    }
    if (saveNewCollectionButton == null) {
      throw new IllegalStateException("Edit collection button missing.");
    }
    if (collectionNameEntry == null) {
      throw new IllegalStateException("Collection name text field missing.");
    }
    if (filterTextField == null) {
      throw new IllegalStateException("Filter text field missing.");
    }
    if (collectionsCardsContainer == null) {
      throw new IllegalStateException("Collections cards container missing.");
    }
    if (recipesCardsContainer == null) {
      throw new IllegalStateException("Recipes cards container missing.");
    }

    // bindings for collection cards container
    collectionsCardsContainer.setCellFactory(
        lv ->
            new ListCell<RecipeCollectionSummary>() {
              @Override
              protected void updateItem(RecipeCollectionSummary item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                  setText(null);
                  return;
                }

                setText(
                    "%s [%s] %d recipe%s"
                        .formatted(
                            item.title(),
                            switch (item.sourceType()) {
                              case PUBLISHED_BOOK -> "COOKBOOK";
                              case PERSONAL -> "PERSONAL";
                              case WEBSITE -> "WEBSITE";
                            },
                            item.recipeCount(),
                            item.recipeCount() == 1 ? "" : "s"));
              }
            });

    collectionsCardsContainer
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            ((observableValue, oldValue, newValue) -> {
              if (newValue == null) {
                return;
              }
              if (deleteMode
                  && DialogHandler.showConfirmation(
                      "Confirmation",
                      "Are you sure you want to delete this collection?\n%s"
                          .formatted(newValue.title()))) {
                libraryViewModel.deleteCollection(newValue.id());
                return;
              }

              libraryViewModel.selectCollection(newValue.id());
            }));

    collectionsCardsContainer.setItems(collectionsProperty);

    // bindings for recipe cards container
    recipesCardsContainer.setCellFactory(
        lv ->
            new ListCell<Recipe>() {
              @Override
              protected void updateItem(Recipe item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                  setText(null);
                  return;
                }

                setText(item.getTitle());
              }
            });

    recipesCardsContainer
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            ((observableValue, oldRecipe, newRecipe) -> {
              if (newRecipe == null) {
                return;
              }

              libraryViewModel.selectRecipe(newRecipe.getId());
            }));

    recipesCardsContainer.setItems(recipesProperty);

    newButton.setOnAction(
        actionEvent -> {
          if (saveNewCollectionButton == null || collectionNameEntry == null) {
            return;
          }

          saveNewCollectionButton.setVisible(true);
          collectionNameEntry.setVisible(true);
        });

    saveNewCollectionButton.setOnAction(
        actionEvent -> {
          if (saveNewCollectionButton == null || collectionNameEntry == null || newButton == null) {
            return;
          }

          newButton.setVisible(true);
          saveNewCollectionButton.setVisible(false);
          collectionNameEntry.setVisible(false);

          // try to create the collection. expect blank name
          try {
            libraryViewModel.createCollection(collectionNameEntry.getText());
            libraryViewModel.refresh();
          } catch (IllegalArgumentException ignored) {
            DialogHandler.showError(
                "Error", "Failed to create collection. Collection names must not be blank.");
          }
          collectionNameEntry.clear();
        });

    deleteButton.setOnAction(
        actionEvent -> {
          if (deleteButton == null) {
            return;
          }
          deleteMode = !deleteMode;
          deleteButton.setText(!deleteMode ? "Delete" : "Done");
        });

    undoDeleteButton.setOnAction(actionEvent -> libraryViewModel.undoDelete());

    filterTextField
        .textProperty()
        .addListener(
            (observable, oldValue, newValue) -> {
              if (filterTextField == null) {
                return;
              }
              filterTextProperty.set(newValue);
            });
  }

  /**
   * Constructor for the View Model for the Library View feature. Dependencies must be injected.
   *
   * @param libraryViewModel implementation for the LibraryViewModel
   */
  @SuppressWarnings("unchecked")
  public LibraryViewController(LibraryViewModel libraryViewModel) {
    this.libraryViewModel = libraryViewModel;

    this.collectionsProperty =
        (ObservableList<RecipeCollectionSummary>) libraryViewModel.collectionsProperty();
    this.recipesProperty = (ObservableList<Recipe>) libraryViewModel.recipesProperty();

    this.filterTextProperty = libraryViewModel.filterTextProperty();
    this.loadingProperty = libraryViewModel.loadingProperty();
    this.undoAvailableProperty = libraryViewModel.undoAvailableProperty();
    this.undoMessageProperty = libraryViewModel.undoMessageProperty();

    this.addBindings();
  }

  /** Runs after the FXML has initialized. */
  @FXML
  private void initialize() {
    this.addButtonBindings();
    this.libraryViewModel.refresh();
  }
}
