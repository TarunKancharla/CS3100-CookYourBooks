package app.cookyourbooks.gui.viewmodel;

import java.util.List;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import org.jspecify.annotations.Nullable;

import app.cookyourbooks.gui.BackgroundTaskRunner;
import app.cookyourbooks.gui.NavigationService;
import app.cookyourbooks.model.Ingredient;
import app.cookyourbooks.model.Recipe;
import app.cookyourbooks.model.VagueIngredient;
import app.cookyourbooks.repository.RecipeRepository;

public class RecipeEditorViewModelImpl implements RecipeEditorViewModel {
  public record IngredientEntry(String name, String description) {}

  private final RecipeRepository recipeRepository;
  private final StringProperty title = new SimpleStringProperty("");
  private final ObservableList<IngredientEntry> ingredients = FXCollections.observableArrayList();
  private final BooleanProperty editing = new SimpleBooleanProperty(false);
  private final BooleanProperty isDirty = new SimpleBooleanProperty(false);
  private final BooleanProperty isValid = new SimpleBooleanProperty(false);
  private final BooleanProperty isSaving = new SimpleBooleanProperty(false);
  private final StringProperty statusMessage = new SimpleStringProperty("No recipe selected.");
  private @Nullable String recipeId;
  private String loadedTitle = "";
  private List<IngredientEntry> loadedIngredients = List.of();
  private boolean suppressDirtyTracking;

  public RecipeEditorViewModelImpl(
      RecipeRepository recipeRepository, NavigationService navigation) {
    this.recipeRepository = recipeRepository;
    title.addListener(
        (obs, oldValue, newValue) -> {
          isValid.set(!newValue.trim().isEmpty());
          updateDirtyState();
        });
    navigation
        .selectedRecipeIdProperty()
        .addListener(
            (obs, oldId, newId) -> {
              if (newId != null && !newId.isBlank()) {
                loadRecipe(newId);
              }
            });
  }

  @Override
  public StringProperty titleProperty() {
    return title;
  }

  @Override
  public ObservableList<IngredientEntry> ingredientsProperty() {
    return ingredients;
  }

  @Override
  public BooleanProperty editingProperty() {
    return editing;
  }

  @Override
  public BooleanProperty isDirtyProperty() {
    return isDirty;
  }

  @Override
  public BooleanProperty isValidProperty() {
    return isValid;
  }

  @Override
  public BooleanProperty isSavingProperty() {
    return isSaving;
  }

  @Override
  public StringProperty statusMessageProperty() {
    return statusMessage;
  }

  @Override
  public void loadRecipe(String recipeId) {
    Recipe recipe =
        recipeRepository
            .findById(recipeId)
            .orElseThrow(() -> new IllegalArgumentException("Recipe not found: " + recipeId));
    this.recipeId = recipe.getId();
    title.set(recipe.getTitle());
    ingredients.setAll(recipe.getIngredients().stream().map(this::toIngredientEntry).toList());
    editing.set(false);
    isSaving.set(false);
    isValid.set(!title.get().trim().isEmpty());
    loadedTitle = title.get().trim();
    loadedIngredients = List.copyOf(ingredients);
    isDirty.set(false);
    statusMessage.set("Loaded recipe.");
  }

  @Override
  public void toggleEditMode() {
    editing.set(!editing.get());
    statusMessage.set(editing.get() ? "Edit mode enabled." : "View mode.");
  }

  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  public void save() {
    if (recipeId == null || !editing.get() || !isDirty.get() || !isValid.get() || isSaving.get()) {
      return;
    }

    Recipe original =
        recipeRepository
            .findById(recipeId)
            .orElseThrow(() -> new IllegalArgumentException("Recipe not found: " + recipeId));

    List<Ingredient> updatedIngredients =
        ingredients.stream()
            .map(
                entry ->
                    (Ingredient)
                        new VagueIngredient(
                            entry.name().trim(),
                            entry.description().isBlank() ? null : entry.description().trim(),
                            null,
                            null))
            .toList();

    Recipe updated =
        new Recipe(
            original.getId(),
            title.get().trim(),
            original.getServings(),
            updatedIngredients,
            original.getInstructions(),
            original.getConversionRules());

    isSaving.set(true);
    statusMessage.set("Saving...");

    BackgroundTaskRunner.run(
        () -> {
          recipeRepository.save(updated);
          return updated;
        },
        saved -> {
          loadedTitle = saved.getTitle().trim();
          loadedIngredients = List.copyOf(ingredients);
          isDirty.set(false);
          isSaving.set(false);
          editing.set(false);
          statusMessage.set("Saved successfully.");
        },
        error -> {
          isSaving.set(false);
          editing.set(true);
          isDirty.set(true);
          statusMessage.set("Save failed: " + error.getMessage());
        });
  }

  @Override
  public void discardChanges() {
    if (recipeId == null) {
      return;
    }
    suppressDirtyTracking = true;
    title.set(loadedTitle);
    ingredients.setAll(loadedIngredients);
    suppressDirtyTracking = false;
    isDirty.set(false);
    statusMessage.set("Changes discarded.");
  }

  @Override
  public void addIngredient() {
    if (!editing.get()) {
      return;
    }
    ingredients.add(new IngredientEntry("New ingredient", ""));
    updateDirtyState();
    statusMessage.set("Ingredient added.");
  }

  @Override
  public void removeIngredient(int index) {
    if (!editing.get()) {
      return;
    }
    if (index < 0 || index >= ingredients.size()) {
      return;
    }
    ingredients.remove(index);
    updateDirtyState();
    statusMessage.set("Ingredient removed.");
  }

  public void moveIngredientUp(int index) {
    if (!editing.get()) {
      return;
    }
    if (index <= 0 || index >= ingredients.size()) {
      return;
    }
    IngredientEntry entry = ingredients.remove(index);
    ingredients.add(index - 1, entry);
    updateDirtyState();
    statusMessage.set("Ingredient moved.");
  }

  public void moveIngredientDown(int index) {
    if (!editing.get()) {
      return;
    }
    if (index < 0 || index >= ingredients.size() - 1) {
      return;
    }
    IngredientEntry entry = ingredients.remove(index);
    ingredients.add(index + 1, entry);
    updateDirtyState();
    statusMessage.set("Ingredient moved.");
  }

  @Override
  public @Nullable String getRecipeId() {
    return recipeId;
  }

  @Override
  public String getTitle() {
    return title.get();
  }

  @Override
  public int getIngredientCount() {
    return ingredients.size();
  }

  @Override
  public List<String> getIngredientNames() {
    return ingredients.stream().map(IngredientEntry::name).toList();
  }

  @Override
  public boolean isEditing() {
    return editing.get();
  }

  @Override
  public boolean isDirty() {
    return isDirty.get();
  }

  @Override
  public boolean isValid() {
    return isValid.get();
  }

  @Override
  public boolean isSaving() {
    return isSaving.get();
  }

  @Override
  public String getStatusMessage() {
    return statusMessage.get();
  }

  private IngredientEntry toIngredientEntry(Ingredient ingredient) {
    return new IngredientEntry(ingredient.getName(), ingredient.toString());
  }

  private void updateDirtyState() {
    if (suppressDirtyTracking || recipeId == null) {
      return;
    }
    boolean titleChanged = !title.get().trim().equals(loadedTitle);
    boolean ingredientsChanged = !List.copyOf(ingredients).equals(loadedIngredients);
    isDirty.set(titleChanged || ingredientsChanged);
  }
}
