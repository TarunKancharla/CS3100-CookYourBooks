package app.cookyourbooks.gui.viewmodel;

import java.util.List;
import java.util.Objects;

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
import app.cookyourbooks.model.Instruction;
import app.cookyourbooks.model.Recipe;
import app.cookyourbooks.model.VagueIngredient;
import app.cookyourbooks.repository.RecipeRepository;

public class RecipeEditorViewModelImpl implements RecipeEditorViewModel {
  public static final class IngredientEntry {
    private final StringProperty name;
    private final StringProperty description;

    public IngredientEntry(String name, String description) {
      this.name = new SimpleStringProperty(name);
      this.description = new SimpleStringProperty(description);
    }

    public StringProperty nameProperty() {
      return name;
    }

    public StringProperty descriptionProperty() {
      return description;
    }

    public String name() {
      return name.get();
    }

    public String description() {
      return description.get();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof IngredientEntry other)) {
        return false;
      }
      return Objects.equals(name(), other.name())
          && Objects.equals(description(), other.description());
    }

    @Override
    public int hashCode() {
      return Objects.hash(name(), description());
    }
  }

  private final RecipeRepository recipeRepository;
  private final StringProperty title = new SimpleStringProperty("");
  private final StringProperty description = new SimpleStringProperty("");
  private final StringProperty instructions = new SimpleStringProperty("");
  private final ObservableList<IngredientEntry> ingredients = FXCollections.observableArrayList();
  private final BooleanProperty editing = new SimpleBooleanProperty(false);
  private final BooleanProperty isDirty = new SimpleBooleanProperty(false);
  private final BooleanProperty isValid = new SimpleBooleanProperty(false);
  private final BooleanProperty isSaving = new SimpleBooleanProperty(false);
  private final StringProperty statusMessage = new SimpleStringProperty("No recipe selected.");
  private @Nullable String recipeId;
  private String loadedTitle = "";
  private String loadedDescription = "";
  private String loadedInstructions = "";
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
    description.addListener((obs, oldValue, newValue) -> updateDirtyState());
    instructions.addListener((obs, oldValue, newValue) -> updateDirtyState());
    ingredients.addListener(
        (javafx.collections.ListChangeListener<? super IngredientEntry>)
            change -> {
              while (change.next()) {
                if (change.wasAdded()) {
                  for (IngredientEntry entry : change.getAddedSubList()) {
                    bindIngredientEntry(entry);
                  }
                }
              }
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

  public StringProperty descriptionProperty() {
    return description;
  }

  public StringProperty instructionsProperty() {
    return instructions;
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
    loadInstructionText(recipe);
    ingredients.setAll(recipe.getIngredients().stream().map(this::toIngredientEntry).toList());
    editing.set(false);
    isSaving.set(false);
    isValid.set(!title.get().trim().isEmpty());
    loadedTitle = title.get().trim();
    loadedDescription = description.get().trim();
    loadedInstructions = instructions.get().trim();
    loadedIngredients = snapshotIngredients(ingredients);
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
            buildUpdatedInstructions(),
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
          loadedDescription = description.get().trim();
          loadedInstructions = instructions.get().trim();
          loadedIngredients = snapshotIngredients(ingredients);
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
    description.set(loadedDescription);
    instructions.set(loadedInstructions);
    ingredients.setAll(snapshotIngredients(loadedIngredients));
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

  private void bindIngredientEntry(IngredientEntry entry) {
    entry.nameProperty().addListener((obs, oldValue, newValue) -> updateDirtyState());
    entry.descriptionProperty().addListener((obs, oldValue, newValue) -> updateDirtyState());
  }

  private List<IngredientEntry> snapshotIngredients(List<IngredientEntry> source) {
    return source.stream()
        .map(entry -> new IngredientEntry(entry.name(), entry.description()))
        .toList();
  }

  private void loadInstructionText(Recipe recipe) {
    List<Instruction> all = recipe.getInstructions();
    if (all.isEmpty()) {
      description.set("");
      instructions.set("");
      return;
    }
    description.set(all.getFirst().getText());
    if (all.size() == 1) {
      instructions.set("");
      return;
    }
    instructions.set(
        all.subList(1, all.size()).stream()
            .map(Instruction::getText)
            .collect(java.util.stream.Collectors.joining("\n")));
  }

  private List<Instruction> buildUpdatedInstructions() {
    List<String> lines = new java.util.ArrayList<>();
    String desc = description.get().trim();
    if (!desc.isEmpty()) {
      lines.add(desc);
    }
    String steps = instructions.get().trim();
    if (!steps.isEmpty()) {
      for (String line : java.util.regex.Pattern.compile("\\R").splitAsStream(steps).toList()) {
        String trimmed = line.trim();
        if (!trimmed.isEmpty()) {
          lines.add(trimmed);
        }
      }
    }
    if (lines.isEmpty()) {
      return List.of();
    }
    return java.util.stream.IntStream.range(0, lines.size())
        .mapToObj(i -> new Instruction(i + 1, lines.get(i), List.of()))
        .toList();
  }

  private void updateDirtyState() {
    if (suppressDirtyTracking || recipeId == null) {
      return;
    }
    boolean titleChanged = !title.get().trim().equals(loadedTitle);
    boolean descriptionChanged = !description.get().trim().equals(loadedDescription);
    boolean instructionsChanged = !instructions.get().trim().equals(loadedInstructions);
    boolean ingredientsChanged = !List.copyOf(ingredients).equals(loadedIngredients);
    isDirty.set(titleChanged || descriptionChanged || instructionsChanged || ingredientsChanged);
  }
}
