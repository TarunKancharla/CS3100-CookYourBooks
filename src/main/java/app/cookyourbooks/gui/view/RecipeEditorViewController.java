package app.cookyourbooks.gui.view;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import org.jspecify.annotations.Nullable;

import app.cookyourbooks.gui.viewmodel.RecipeEditorViewModelImpl;
import app.cookyourbooks.gui.viewmodel.RecipeEditorViewModelImpl.IngredientEntry;

/** JavaFX controller for the Recipe Editor view. */
@SuppressWarnings("NullAway.Init")
public class RecipeEditorViewController {
  @FXML private TextField titleField;
  @FXML private TextArea descriptionArea;
  @FXML private TextArea instructionsArea;
  @FXML private ListView<IngredientEntry> ingredientsList;
  @FXML private Button editButton;
  @FXML private Button discardButton;
  @FXML private Button saveButton;
  @FXML private Button addIngredientButton;
  @FXML private Button removeIngredientButton;
  @FXML private Button moveUpButton;
  @FXML private Button moveDownButton;
  @FXML private Label statusLabel;
  private final RecipeEditorViewModelImpl viewModel;

  /**
   * Creates the controller with its view model dependency.
   *
   * @param viewModel recipe editor view model
   */
  public RecipeEditorViewController(RecipeEditorViewModelImpl viewModel) {
    this.viewModel = viewModel;
  }

  /** Initializes control bindings and event handlers after FXML injection. */
  @SuppressWarnings("UnusedMethod")
  @FXML
  private void initialize() {
    titleField.textProperty().bindBidirectional(viewModel.titleProperty());
    descriptionArea.textProperty().bindBidirectional(viewModel.descriptionProperty());
    instructionsArea.textProperty().bindBidirectional(viewModel.instructionsProperty());
    titleField.editableProperty().bind(viewModel.editingProperty());
    descriptionArea
        .editableProperty()
        .bind(viewModel.editingProperty().and(viewModel.isSavingProperty().not()));
    instructionsArea
        .editableProperty()
        .bind(viewModel.editingProperty().and(viewModel.isSavingProperty().not()));
    ingredientsList.setItems(viewModel.ingredientsProperty());
    ingredientsList.setCellFactory(list -> new IngredientCell(viewModel));
    statusLabel.textProperty().bind(viewModel.statusMessageProperty());
    editButton
        .textProperty()
        .bind(Bindings.when(viewModel.editingProperty()).then("View").otherwise("Edit"));
    saveButton
        .textProperty()
        .bind(Bindings.when(viewModel.isSavingProperty()).then("Saving...").otherwise("Save"));

    addIngredientButton
        .disableProperty()
        .bind(viewModel.editingProperty().not().or(viewModel.isSavingProperty()));
    saveButton
        .disableProperty()
        .bind(
            viewModel
                .isSavingProperty()
                .or(viewModel.editingProperty().not())
                .or(viewModel.isDirtyProperty().not())
                .or(viewModel.isValidProperty().not()));
    discardButton.disableProperty().bind(viewModel.isDirtyProperty().not());
    editButton.disableProperty().bind(viewModel.isSavingProperty());
    removeIngredientButton
        .disableProperty()
        .bind(
            viewModel
                .editingProperty()
                .not()
                .or(viewModel.isSavingProperty())
                .or(ingredientsList.getSelectionModel().selectedIndexProperty().lessThan(0)));
    moveUpButton
        .disableProperty()
        .bind(
            viewModel
                .editingProperty()
                .not()
                .or(viewModel.isSavingProperty())
                .or(
                    ingredientsList
                        .getSelectionModel()
                        .selectedIndexProperty()
                        .lessThanOrEqualTo(0)));
    moveDownButton
        .disableProperty()
        .bind(
            viewModel
                .editingProperty()
                .not()
                .or(viewModel.isSavingProperty())
                .or(
                    ingredientsList
                        .getSelectionModel()
                        .selectedIndexProperty()
                        .greaterThanOrEqualTo(
                            Bindings.size(ingredientsList.getItems()).subtract(1))));

    editButton.setOnAction(e -> viewModel.toggleEditMode());
    discardButton.setOnAction(e -> viewModel.discardChanges());
    saveButton.setOnAction(e -> viewModel.save());
    addIngredientButton.setOnAction(
        e -> {
          viewModel.addIngredient();
          ingredientsList.getSelectionModel().selectLast();
        });
    removeIngredientButton.setOnAction(
        e -> viewModel.removeIngredient(ingredientsList.getSelectionModel().getSelectedIndex()));
    moveUpButton.setOnAction(
        e -> {
          int index = ingredientsList.getSelectionModel().getSelectedIndex();
          viewModel.moveIngredientUp(index);
          ingredientsList.getSelectionModel().select(Math.max(0, index - 1));
        });
    moveDownButton.setOnAction(
        e -> {
          int index = ingredientsList.getSelectionModel().getSelectedIndex();
          viewModel.moveIngredientDown(index);
          ingredientsList
              .getSelectionModel()
              .select(Math.min(ingredientsList.getItems().size() - 1, index + 1));
        });

    ingredientsList.setOnKeyPressed(
        event -> {
          int index = ingredientsList.getSelectionModel().getSelectedIndex();
          if (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) {
            viewModel.removeIngredient(index);
            event.consume();
          } else if (event.isAltDown() && event.getCode() == KeyCode.UP) {
            viewModel.moveIngredientUp(index);
            ingredientsList.getSelectionModel().select(Math.max(0, index - 1));
            event.consume();
          } else if (event.isAltDown() && event.getCode() == KeyCode.DOWN) {
            viewModel.moveIngredientDown(index);
            ingredientsList
                .getSelectionModel()
                .select(Math.min(ingredientsList.getItems().size() - 1, index + 1));
            event.consume();
          }
        });

    instructionsArea.setOnKeyPressed(
        event -> {
          if (event.isShortcutDown() && event.getCode() == KeyCode.S) {
            viewModel.save();
            event.consume();
          }
        });
    titleField.setOnKeyPressed(
        event -> {
          if (event.isShortcutDown() && event.getCode() == KeyCode.S) {
            viewModel.save();
            event.consume();
          }
        });
    descriptionArea.setOnKeyPressed(
        event -> {
          if (event.isShortcutDown() && event.getCode() == KeyCode.S) {
            viewModel.save();
            event.consume();
          }
        });
  }

  /** Custom list cell that shows editable ingredient fields. */
  private static final class IngredientCell extends ListCell<IngredientEntry> {
    private final TextField nameField = new TextField();
    private final TextField descriptionField = new TextField();
    private final HBox container = new HBox(8, nameField, descriptionField);
    private @Nullable IngredientEntry boundItem;

    /**
     * Creates an editable ingredient cell.
     *
     * @param viewModel recipe editor view model
     */
    private IngredientCell(RecipeEditorViewModelImpl viewModel) {
      HBox.setHgrow(nameField, Priority.ALWAYS);
      HBox.setHgrow(descriptionField, Priority.ALWAYS);
      nameField.setPromptText("Ingredient");
      descriptionField.setPromptText("Description");
      nameField
          .disableProperty()
          .bind(viewModel.editingProperty().not().or(viewModel.isSavingProperty()));
      descriptionField
          .disableProperty()
          .bind(viewModel.editingProperty().not().or(viewModel.isSavingProperty()));
    }

    /**
     * Updates text fields to the current row item.
     *
     * @param item ingredient row item
     * @param empty whether the cell is empty
     */
    @Override
    protected void updateItem(IngredientEntry item, boolean empty) {
      super.updateItem(item, empty);
      if (boundItem != null) {
        nameField.textProperty().unbindBidirectional(boundItem.nameProperty());
        descriptionField.textProperty().unbindBidirectional(boundItem.descriptionProperty());
        boundItem = null;
      }
      if (empty || item == null) {
        setGraphic(null);
        return;
      }
      boundItem = item;
      nameField.textProperty().bindBidirectional(item.nameProperty());
      descriptionField.textProperty().bindBidirectional(item.descriptionProperty());
      setGraphic(container);
    }
  }
}
