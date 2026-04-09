package app.cookyourbooks.gui.view;

import java.io.File;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import org.jspecify.annotations.Nullable;

import app.cookyourbooks.gui.viewmodel.CollectionSummary;
import app.cookyourbooks.gui.viewmodel.EditableIngredient;
import app.cookyourbooks.gui.viewmodel.ImportViewModelImpl;

@SuppressWarnings("NullAway.Init")
public class ImportViewController {

  @FXML private Label statusLabel;
  @FXML private Label errorLabel;
  @FXML private Button chooseImageButton;
  @FXML private Button cancelButton;
  @FXML private Button acceptButton;
  @FXML private Button rejectButton;
  @FXML private Button addIngredientButton;
  @FXML private Button removeIngredientButton;
  @FXML private Button errorResetButton;
  @FXML private ProgressBar progressBar;
  @FXML private TextField titleField;
  @FXML private ListView<EditableIngredient> ingredientsList;
  @FXML private ComboBox<CollectionSummary> collectionComboBox;

  @FXML private VBox idlePane;
  @FXML private VBox processingPane;
  @FXML private VBox reviewPane;
  @FXML private VBox errorPane;

  private final ImportViewModelImpl viewModel;

  public ImportViewController(ImportViewModelImpl viewModel) {
    this.viewModel = viewModel;
  }

  @SuppressWarnings("UnusedMethod")
  @FXML
  private void initialize() {
    statusLabel.textProperty().bind(viewModel.statusMessageProperty());
    errorLabel.textProperty().bind(viewModel.errorMessageProperty());
    progressBar.progressProperty().bind(viewModel.progressProperty());

    titleField.textProperty().bindBidirectional(viewModel.importedTitleProperty());
    ingredientsList.setItems(viewModel.importedIngredientsProperty());
    ingredientsList.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
    ingredientsList.setCellFactory(list -> new EditableIngredientCell());

    collectionComboBox.setItems(viewModel.availableCollectionsProperty());
    collectionComboBox.valueProperty().bindBidirectional(viewModel.selectedCollectionProperty());
    collectionComboBox.setConverter(
        new StringConverter<>() {
          @Override
          public String toString(CollectionSummary summary) {
            return summary != null ? summary.title() : "";
          }

          @Override
          @Nullable
          public CollectionSummary fromString(String string) {
            return null;
          }
        });

    chooseImageButton.setOnAction(e -> chooseImage());
    cancelButton.setOnAction(e -> viewModel.cancelImport());
    acceptButton.setOnAction(e -> viewModel.acceptImport());
    rejectButton.setOnAction(e -> viewModel.rejectImport());
    errorResetButton.setOnAction(e -> viewModel.rejectImport());

    addIngredientButton.setOnAction(
        e -> {
          EditableIngredient ingredient = new EditableIngredient("");
          viewModel.importedIngredientsProperty().add(ingredient);
          ingredientsList.getSelectionModel().select(ingredient);
        });
    removeIngredientButton.setOnAction(
        e -> {
          EditableIngredient selected = ingredientsList.getSelectionModel().getSelectedItem();
          if (selected != null) {
            viewModel.importedIngredientsProperty().remove(selected);
          }
        });

    viewModel.stateProperty().addListener((obs, oldState, newState) -> updateState(newState));
    updateState(viewModel.getState());
    viewModel.loadCollections();
  }

  private void chooseImage() {
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Import Recipe Image");
    chooser
        .getExtensionFilters()
        .add(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.webp"));
    File file = chooser.showOpenDialog(chooseImageButton.getScene().getWindow());
    if (file != null) {
      viewModel.startImport(file.toPath());
    }
  }

  private void updateState(String state) {
    boolean idle = "idle".equals(state);
    boolean processing = "processing".equals(state);
    boolean review = "review".equals(state);
    boolean error = "error".equals(state);

    setPaneVisible(idlePane, idle);
    setPaneVisible(processingPane, processing);
    setPaneVisible(reviewPane, review);
    setPaneVisible(errorPane, error);

    chooseImageButton.setDisable(!idle);
    cancelButton.setDisable(!processing);
    acceptButton.setDisable(!review);
    rejectButton.setDisable(!review);
    addIngredientButton.setDisable(!review);
    removeIngredientButton.setDisable(!review);
    titleField.setDisable(!review);
    ingredientsList.setDisable(!review);
    collectionComboBox.setDisable(!review);
  }

  private void setPaneVisible(VBox pane, boolean visible) {
    pane.setVisible(visible);
    pane.setManaged(visible);
  }

  private static final class EditableIngredientCell extends ListCell<EditableIngredient> {
    private final TextField textField = new TextField();
    private @Nullable EditableIngredient current;

    EditableIngredientCell() {
      setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    @Override
    protected void updateItem(EditableIngredient item, boolean empty) {
      super.updateItem(item, empty);
      if (current != null) {
        textField.textProperty().unbindBidirectional(current.nameProperty());
        current = null;
      }
      if (empty || item == null) {
        setGraphic(null);
      } else {
        current = item;
        textField.textProperty().bindBidirectional(item.nameProperty());
        setGraphic(textField);
      }
    }
  }
}
