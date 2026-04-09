package app.cookyourbooks.gui.viewmodel;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;

import org.jspecify.annotations.Nullable;

import app.cookyourbooks.gui.BackgroundTaskRunner;
import app.cookyourbooks.model.Ingredient;
import app.cookyourbooks.model.Recipe;
import app.cookyourbooks.services.LibrarianService;
import app.cookyourbooks.services.ocr.OcrException;
import app.cookyourbooks.services.ocr.RecipeOcrService;

/** ViewModel implementation for the Import Interface feature. */
public final class ImportViewModelImpl implements ImportViewModel {

  private enum ImportState {
    IDLE,
    PROCESSING,
    REVIEW,
    ERROR
  }

  private final RecipeOcrService ocrService;
  private final LibrarianService librarianService;

  private final StringProperty statusMessage = new SimpleStringProperty("Ready to import.");
  private final StringProperty errorMessage = new SimpleStringProperty();
  private final StringProperty importedTitle = new SimpleStringProperty();
  private final ObservableList<EditableIngredient> importedIngredients =
      FXCollections.observableArrayList();
  private final ObservableList<CollectionSummary> availableCollections =
      FXCollections.observableArrayList();
  private final ObjectProperty<CollectionSummary> selectedCollection = new SimpleObjectProperty<>();
  private final StringProperty stateString = new SimpleStringProperty("idle");
  private final DoubleProperty progress = new SimpleDoubleProperty(0.0);
  private final ObjectProperty<ImportState> state = new SimpleObjectProperty<>(ImportState.IDLE);

  private @Nullable Recipe importedRecipe;
  private @Nullable Task<Recipe> activeTask;

  public ImportViewModelImpl(RecipeOcrService ocrService, LibrarianService librarianService) {
    this.ocrService = Objects.requireNonNull(ocrService);
    this.librarianService = Objects.requireNonNull(librarianService);
    state.addListener((obs, oldState, newState) -> updateStateString(newState));
  }

  public StringProperty stateProperty() {
    return stateString;
  }

  public DoubleProperty progressProperty() {
    return progress;
  }

  public ObjectProperty<CollectionSummary> selectedCollectionProperty() {
    return selectedCollection;
  }

  @Override
  public StringProperty statusMessageProperty() {
    return statusMessage;
  }

  @Override
  public StringProperty errorMessageProperty() {
    return errorMessage;
  }

  @Override
  public StringProperty importedTitleProperty() {
    return importedTitle;
  }

  @Override
  public ObservableList<EditableIngredient> importedIngredientsProperty() {
    return importedIngredients;
  }

  @Override
  public ObservableList<CollectionSummary> availableCollectionsProperty() {
    return availableCollections;
  }

  @Override
  public void startImport(Path imagePath) {
    if (imagePath == null || state.get() == ImportState.PROCESSING) {
      return;
    }
    clearImportedData();
    errorMessage.set(null);
    statusMessage.set("Extracting recipe...");
    progress.set(-1.0);
    setState(ImportState.PROCESSING);

    activeTask =
        BackgroundTaskRunner.run(
            () -> ocrService.extractRecipe(imagePath),
            recipe -> {
              importedRecipe = recipe;
              importedTitle.set(recipe.getTitle());
              importedIngredients.setAll(toEditableIngredients(recipe.getIngredients()));
              statusMessage.set("Review the imported recipe.");
              progress.set(1.0);
              setState(ImportState.REVIEW);
            },
            error -> {
              errorMessage.set(resolveErrorMessage(error));
              statusMessage.set("Import failed.");
              progress.set(0.0);
              setState(ImportState.ERROR);
            });
  }

  @Override
  public void cancelImport() {
    if (state.get() != ImportState.PROCESSING) {
      return;
    }
    if (activeTask != null) {
      activeTask.cancel();
      activeTask = null;
    }
    clearImportedData();
    errorMessage.set(null);
    statusMessage.set("Ready to import.");
    progress.set(0.0);
    setState(ImportState.IDLE);
  }

  @Override
  public void acceptImport() {
    if (state.get() != ImportState.REVIEW || importedRecipe == null) {
      return;
    }
    String collectionId = getSelectedCollectionId();
    if (collectionId == null) {
      return;
    }
    String title = importedTitle.get() != null ? importedTitle.get().trim() : "";
    if (title.isBlank()) {
      title = importedRecipe.getTitle();
    }
    List<Ingredient> ingredients =
        importedIngredients.stream()
            .map(EditableIngredient::getName)
            .map(name -> name == null ? "" : name.trim())
            .filter(name -> !name.isBlank())
            .map(name -> new app.cookyourbooks.model.VagueIngredient(name, null, null, null))
            .map(Ingredient.class::cast)
            .toList();
    Recipe updated =
        new Recipe(
            null,
            title,
            importedRecipe.getServings(),
            ingredients,
            importedRecipe.getInstructions(),
            importedRecipe.getConversionRules());
    try {
      librarianService.saveRecipe(updated, collectionId);
      resetToIdle();
    } catch (RuntimeException e) {
      errorMessage.set(resolveErrorMessage(e));
      statusMessage.set("Import failed.");
      setState(ImportState.ERROR);
    }
  }

  @Override
  public void rejectImport() {
    if (state.get() != ImportState.REVIEW && state.get() != ImportState.ERROR) {
      return;
    }
    resetToIdle();
  }

  @Override
  public void selectTargetCollection(String collectionId) {
    if (collectionId == null) {
      selectedCollection.set(null);
      return;
    }
    availableCollections.stream()
        .filter(summary -> summary.id().equals(collectionId))
        .findFirst()
        .ifPresentOrElse(selectedCollection::set, () -> selectedCollection.set(null));
  }

  @Override
  public void loadCollections() {
    BackgroundTaskRunner.run(
        librarianService::listCollections,
        collections -> {
          CollectionSummary previous = selectedCollection.get();
          availableCollections.setAll(collections.stream().map(CollectionSummary::from).toList());
          if (previous != null) {
            selectTargetCollection(previous.id());
          }
        },
        error -> {
          statusMessage.set("Failed to load collections.");
          errorMessage.set(resolveErrorMessage(error));
        });
  }

  @Override
  public String getState() {
    return stateString.get();
  }

  @Override
  public String getStatusMessage() {
    return statusMessage.get();
  }

  @Override
  public @Nullable String getErrorMessage() {
    return state.get() == ImportState.ERROR ? errorMessage.get() : null;
  }

  @Override
  public @Nullable String getImportedRecipeTitle() {
    return state.get() == ImportState.REVIEW ? importedTitle.get() : null;
  }

  @Override
  public List<String> getImportedIngredientNames() {
    if (state.get() != ImportState.REVIEW) {
      return List.of();
    }
    return importedIngredients.stream().map(EditableIngredient::getName).toList();
  }

  @Override
  public List<String> getAvailableCollectionIds() {
    return availableCollections.stream().map(CollectionSummary::id).toList();
  }

  @Override
  public @Nullable String getSelectedCollectionId() {
    CollectionSummary summary = selectedCollection.get();
    return summary != null ? summary.id() : null;
  }

  private List<EditableIngredient> toEditableIngredients(List<Ingredient> ingredients) {
    return ingredients.stream().map(Ingredient::getName).map(EditableIngredient::new).toList();
  }

  private void resetToIdle() {
    clearImportedData();
    errorMessage.set(null);
    statusMessage.set("Ready to import.");
    progress.set(0.0);
    setState(ImportState.IDLE);
  }

  private void clearImportedData() {
    importedRecipe = null;
    importedTitle.set("");
    importedIngredients.clear();
  }

  private void updateStateString(ImportState newState) {
    stateString.set(newState.name().toLowerCase(Locale.ROOT));
  }

  private void setState(ImportState newState) {
    state.set(newState);
    updateStateString(newState);
  }

  private static String resolveErrorMessage(Throwable error) {
    if (error instanceof OcrException && error.getMessage() != null) {
      return error.getMessage();
    }
    return error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
  }
}
