package app.cookyourbooks.gui.view;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

import app.cookyourbooks.gui.viewmodel.RecipeEditorViewModelImpl;
import app.cookyourbooks.gui.viewmodel.RecipeEditorViewModelImpl.IngredientEntry;

@SuppressWarnings("NullAway.Init")
public class RecipeEditorViewController {
  @FXML private TextField titleField;
  @FXML private ListView<IngredientEntry> ingredientsList;
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
  }
}
