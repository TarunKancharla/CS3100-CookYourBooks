package app.cookyourbooks.gui.viewmodel;

import java.util.List;
import java.util.Objects;

import javafx.beans.Observable;
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

/** ViewModel implementation for the Recipe Editor. */
public class RecipeEditorViewModelImpl implements RecipeEditorViewModel {
  /** Ingredient row used by the editor view. */
  public static final class IngredientEntry {
    private final StringProperty name;
    private final StringProperty description;

    /**
     * Creates an ingredient entry row.
     *
     * @param name ingredient name
     * @param description ingredient description text
     */
    public IngredientEntry(String name, String description) {
      this.name = new SimpleStringProperty(name);
      this.description = new SimpleStringProperty(description);
    }

    /** Returns the observable ingredient name property. */
    public StringProperty nameProperty() {
      return name;
    }

    /** Returns the observable ingredient description property. */
    public StringProperty descriptionProperty() {
      return description;
    }

    /** Returns the current ingredient name. */
    public String name() {
      return name.get();
    }

    /** Returns the current ingredient description. */
    public String description() {
      return description.get();
    }

    /** Compares by name and description values. */
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

    /** Returns hash code for the value comparison. */
    @Override
    public int hashCode() {
      return Objects.hash(name(), description());
    }
  }

  private final RecipeRepository recipeRepository;
  private final StringProperty title = new SimpleStringProperty("");
  private final StringProperty description = new SimpleStringProperty("");
  private final StringProperty instructions = new SimpleStringProperty("");
  private final ObservableList<IngredientEntry> ingredients =
      FXCollections.observableArrayList(
          entry -> new Observable[] {entry.nameProperty(), entry.descriptionProperty()});
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

  /**
   * Creates the editor view model and links it to navigation events.
   *
   * @param recipeRepository repository used for recipe reads and saves
   * @param navigation shared navigation service
   */
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
              while (change.next()) {}
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

  /** Returns the editable title property. */
  @Override
  public StringProperty titleProperty() {
    return title;
  }

  /** Returns the editable description property. */
  public StringProperty descriptionProperty() {
    return description;
  }

  /** Returns the editable instructions property. */
  public StringProperty instructionsProperty() {
    return instructions;
  }

  /** Returns the editable ingredient list property. */
  @Override
  public ObservableList<IngredientEntry> ingredientsProperty() {
    return ingredients;
  }

  /** Returns whether edit mode is enabled. */
  @Override
  public BooleanProperty editingProperty() {
    return editing;
  }

  /** Returns whether unsaved changes are present. */
  @Override
  public BooleanProperty isDirtyProperty() {
    return isDirty;
  }

  /** Returns whether current editor state is valid. */
  @Override
  public BooleanProperty isValidProperty() {
    return isValid;
  }

  /** Returns whether a save operation is in progress. */
  @Override
  public BooleanProperty isSavingProperty() {
    return isSaving;
  }

  /** Returns the status and error message property. */
  @Override
  public StringProperty statusMessageProperty() {
    return statusMessage;
  }

  /**
   * Loads a recipe into editor state.
   *
   * @param recipeId recipe identifier to load
   */
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

  /** Toggles between view and edit mode. */
  @Override
  public void toggleEditMode() {
    editing.set(!editing.get());
    statusMessage.set(editing.get() ? "Edit mode enabled." : "View mode.");
  }

  /** Saves current edits asynchronously to the repository. */
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

  /** Restores editor fields to the last loaded or saved state. */
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

  /** Adds a new ingredient row while in edit mode. */
  @Override
  public void addIngredient() {
    if (!editing.get()) {
      return;
    }
    ingredients.add(new IngredientEntry("New ingredient", ""));
    updateDirtyState();
    statusMessage.set("Ingredient added.");
  }

  /**
   * Removes an ingredient row while in edit mode.
   *
   * @param index ingredient index to remove
   */
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

  /**
   * Moves an ingredient one position up.
   *
   * @param index current ingredient index
   */
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

  /**
   * Moves an ingredient one position down.
   *
   * @param index current ingredient index
   */
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

  /** Returns currently loaded recipe ID, or null if none. */
  @Override
  public @Nullable String getRecipeId() {
    return recipeId;
  }

  /** Returns current title text. */
  @Override
  public String getTitle() {
    return title.get();
  }

  /** Returns current ingredient count. */
  @Override
  public int getIngredientCount() {
    return ingredients.size();
  }

  /** Returns ingredient names in display order. */
  @Override
  public List<String> getIngredientNames() {
    return ingredients.stream().map(IngredientEntry::name).toList();
  }

  /** Returns true if editor is in edit mode. */
  @Override
  public boolean isEditing() {
    return editing.get();
  }

  /** Returns true if there are unsaved edits. */
  @Override
  public boolean isDirty() {
    return isDirty.get();
  }

  /** Returns true if current editor state is valid. */
  @Override
  public boolean isValid() {
    return isValid.get();
  }

  /** Returns true while save is in progress. */
  @Override
  public boolean isSaving() {
    return isSaving.get();
  }

  /** Returns current status or error message text. */
  @Override
  public String getStatusMessage() {
    return statusMessage.get();
  }

  /**
   * Converts a domain ingredient into an editable ingredient row.
   *
   * @param ingredient domain ingredient
   * @return editable ingredient row
   */
  private IngredientEntry toIngredientEntry(Ingredient ingredient) {
    return new IngredientEntry(ingredient.getName(), ingredient.toString());
  }

  /**
   * Creates a copy of ingredient rows for baseline snapshots.
   *
   * @param source ingredient rows to copy
   * @return copied ingredient rows
   */
  private List<IngredientEntry> snapshotIngredients(List<IngredientEntry> source) {
    return source.stream()
        .map(entry -> new IngredientEntry(entry.name(), entry.description()))
        .toList();
  }

  /**
   * Splits loaded instruction steps into description and instructions fields.
   *
   * @param recipe source recipe
   */
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

  /**
   * Builds domain instruction steps from editor description and instructions fields.
   *
   * @return instruction list in step order
   */
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

  /** Updates dirty state by comparing editor fields to the loaded snapshot. */
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
