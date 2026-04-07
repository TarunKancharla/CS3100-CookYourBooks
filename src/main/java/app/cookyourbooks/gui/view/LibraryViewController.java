package app.cookyourbooks.gui.view;

import java.util.ArrayList;
import java.util.List;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

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

  @Nullable @FXML private VBox collectionsCardsContainer;
  @Nullable @FXML private Button newButton;
  @Nullable @FXML private Button saveNewCollectionButton;
  @Nullable @FXML private TextField collectionNameEntry;
  @Nullable @FXML private Button undoDeleteButton;
  @Nullable @FXML private Button deleteButton;
  @Nullable @FXML private Label loadingLabel;
  @Nullable @FXML private ScrollPane collectionsPane;
  @Nullable @FXML private TextField filterTextField;
  @Nullable @FXML private VBox recipesCardsContainer;

  private final List<Button> selectButtons;
  private boolean deleteMode = false;
  @Nullable private Button currentRecipeSelectButton = null;

  /**
   * Adds a collection card to the VBox containing the collection cards
   *
   * @param summary summary of the collection
   */
  private void addCollectionCard(RecipeCollectionSummary summary) {
    if (summary == null || this.collectionsCardsContainer == null) {
      return;
    }

    Label titleLabel =
        new Label("%s (%d recipes)".formatted(summary.title(), summary.recipeCount()));

    TextField titleField = new TextField(summary.title());
    titleField.setVisible(false);
    titleField.setManaged(false);

    StackPane titleContainer = new StackPane(titleLabel, titleField);
    titleContainer.setAlignment(Pos.CENTER_LEFT);

    Button selectButton = new Button(!deleteMode ? "Select" : "Delete");
    selectButton.setMinWidth(64);
    selectButton.setPrefWidth(64);
    selectButton.setMaxWidth(64);

    HBox content = new HBox();
    content.setAlignment(Pos.CENTER_LEFT);
    content.setSpacing(8);
    HBox.setHgrow(titleContainer, Priority.ALWAYS);
    content.getChildren().addAll(titleContainer, selectButton);
    content.setPadding(new Insets(0, 4, 0, 4));

    selectButtons.add(selectButton);
    selectButton.setOnAction(
        actionEvent -> {
          if (deleteMode) {
            if (DialogHandler.showConfirmation(
                "Confirmation",
                "Are you sure you want to delete the collection?\n%s".formatted(summary.title()))) {
              libraryViewModel.deleteCollection(summary.id());
            }
            return;
          }

          selectButtons.forEach(
              btn -> {
                btn.setDisable(false);
                btn.setText("Select");
              });
          selectButton.setDisable(true);
          selectButton.setText("");
          currentRecipeSelectButton = selectButton;
          libraryViewModel.selectCollection(summary.id());
        });

    this.collectionsCardsContainer.getChildren().add(content);
  }

  private void createRecipeCard(Recipe recipe) {
    if (recipe == null || this.recipesCardsContainer == null) {
      return;
    }

    Label nameLabel = new Label(recipe.getTitle());
    nameLabel.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(nameLabel, Priority.ALWAYS);

    Button openButton = new Button("Open");
    openButton.setMinWidth(64);
    openButton.setPrefWidth(64);
    openButton.setMaxWidth(64);
    openButton.setOnAction(actionEvent -> libraryViewModel.selectRecipe(recipe.getId()));

    HBox card = new HBox(8, nameLabel, openButton);
    card.setAlignment(Pos.CENTER_LEFT);
    card.setPadding(new Insets(0, 4, 0, 4));
    card.setMaxWidth(Double.MAX_VALUE);
    card.getStyleClass().add("recipe-card");

    this.recipesCardsContainer.getChildren().add(card);
  }

  /** Adds bindings on change for each property. */
  private void addBindings() {
    // TODO: fix repeated code
    this.collectionsProperty.addListener(
        (ListChangeListener<? super RecipeCollectionSummary>)
            change -> {
              while (change.next()) {
                if (change.wasAdded() && this.collectionsCardsContainer != null) {
                  this.collectionsCardsContainer.getChildren().clear();
                  change.getAddedSubList().forEach(this::addCollectionCard);
                }

                if (change.wasRemoved() && this.collectionsCardsContainer != null) {
                  this.collectionsCardsContainer.getChildren().clear();
                  change.getAddedSubList().forEach(this::addCollectionCard);
                }
              }
            });

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

    recipesProperty.addListener(
        (ListChangeListener<? super Recipe>)
            change -> {
              System.out.println(change);
              if (recipesCardsContainer != null) {
                recipesCardsContainer.getChildren().clear();
              }
              System.out.println(change.getList());
              change.getList().forEach(this::createRecipeCard);
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

          selectButtons.forEach(
              btn -> {
                btn.setText(!deleteMode ? "Select" : "Delete");
                btn.setDisable(false);
              });
          if (!deleteMode && currentRecipeSelectButton != null) {
            currentRecipeSelectButton.setText("");
            currentRecipeSelectButton.setDisable(true);
          }
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

    this.selectButtons = new ArrayList<>();

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
