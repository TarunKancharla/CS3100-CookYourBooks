package app.cookyourbooks.gui.view;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;

import app.cookyourbooks.gui.viewmodel.RecipeEditorViewModelImpl;
import app.cookyourbooks.gui.viewmodel.RecipeEditorViewModelImpl.IngredientEntry;

@SuppressWarnings("NullAway.Init")
public class RecipeEditorViewController {
  @FXML private TextField titleField;
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

  public RecipeEditorViewController(RecipeEditorViewModelImpl viewModel) {
    this.viewModel = viewModel;
  }

  @SuppressWarnings("UnusedMethod")
  @FXML
  private void initialize() {
    titleField.textProperty().bindBidirectional(viewModel.titleProperty());
    titleField.editableProperty().bind(viewModel.editingProperty());
    ingredientsList.setItems(viewModel.ingredientsProperty());
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
  }
}
