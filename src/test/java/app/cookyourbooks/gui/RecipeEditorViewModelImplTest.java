package app.cookyourbooks.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import app.cookyourbooks.cli.fixtures.RecipeFixtures;
import app.cookyourbooks.gui.viewmodel.RecipeEditorViewModelImpl;
import app.cookyourbooks.gui.viewmodel.RecipeEditorViewModelImpl.IngredientEntry;
import app.cookyourbooks.model.Recipe;
import app.cookyourbooks.repository.RecipeRepository;

/** Tests for RecipeEditorViewModelImpl requirements E1-E10. */
class RecipeEditorViewModelImplTest extends ViewModelTestBase {
  private InMemoryRecipeRepository repo;
  private NavigationService navigation;
  private RecipeEditorViewModelImpl vm;
  private Recipe recipe;

  /** Sets up a fresh view model and repository for each test. */
  @BeforeEach
  void setUp() {
    recipe = RecipeFixtures.pancakes();
    repo = new InMemoryRecipeRepository(recipe);
    navigation = new NavigationService();
    vm = new RecipeEditorViewModelImpl(repo, navigation);
  }

  /** loadRecipe populates recipe id, title, and ingredients. */
  @Test
  void e1_loadRecipe_populatesState() {
    vm.loadRecipe(recipe.getId());

    assertThat(vm.getRecipeId()).isEqualTo(recipe.getId());
    assertThat(vm.getTitle()).isEqualTo(recipe.getTitle());
    assertThat(vm.getIngredientCount()).isEqualTo(recipe.getIngredients().size());
  }

  /** toggleEditMode flips editing on and off. */
  @Test
  void e2_toggleEditMode_switchesOnOff() {
    vm.loadRecipe(recipe.getId());

    assertThat(vm.isEditing()).isFalse();
    vm.toggleEditMode();
    assertThat(vm.isEditing()).isTrue();
    vm.toggleEditMode();
    assertThat(vm.isEditing()).isFalse();
  }

  /** changing title or ingredients in edit mode sets dirty true. */
  @Test
  void e3_changesInEditMode_setDirtyTrue() {
    vm.loadRecipe(recipe.getId());
    vm.toggleEditMode();

    vm.titleProperty().set("Updated title");
    assertThat(vm.isDirty()).isTrue();

    vm.discardChanges();
    assertThat(vm.isDirty()).isFalse();

    List<IngredientEntry> entries = (List<IngredientEntry>) vm.ingredientsProperty();
    entries.getFirst().nameProperty().set("Updated ingredient");
    assertThat(vm.isDirty()).isTrue();
  }

  /** discardChanges restores original data and clears dirty. */
  @Test
  void e4_discardChanges_restoresAndClearsDirty() {
    vm.loadRecipe(recipe.getId());
    vm.toggleEditMode();
    vm.titleProperty().set("Changed");
    List<IngredientEntry> entries = (List<IngredientEntry>) vm.ingredientsProperty();
    entries.getFirst().nameProperty().set("Changed ingredient");

    vm.discardChanges();

    assertThat(vm.getTitle()).isEqualTo(recipe.getTitle());
    assertThat(vm.getIngredientNames())
        .containsExactlyElementsOf(recipe.getIngredients().stream().map(i -> i.getName()).toList());
    assertThat(vm.isDirty()).isFalse();
  }

  /** validity is false for blank title and true for non-blank. */
  @Test
  void e5_validation_blankVsNonBlankTitle() {
    vm.loadRecipe(recipe.getId());
    vm.toggleEditMode();

    vm.titleProperty().set("   ");
    assertThat(vm.isValid()).isFalse();

    vm.titleProperty().set("Valid Title");
    assertThat(vm.isValid()).isTrue();
  }

  /** addIngredient and removeIngredient update list size. */
  @Test
  void e6_addAndRemoveIngredient_modifyList() {
    vm.loadRecipe(recipe.getId());
    vm.toggleEditMode();
    int before = vm.getIngredientCount();

    vm.addIngredient();
    assertThat(vm.getIngredientCount()).isEqualTo(before + 1);

    vm.removeIngredient(before);
    assertThat(vm.getIngredientCount()).isEqualTo(before);
  }

  /** save persists edited recipe to repository. */
  @Test
  void e7_save_persistsRecipe() throws InterruptedException {
    vm.loadRecipe(recipe.getId());
    vm.toggleEditMode();
    vm.titleProperty().set("Saved Title");

    vm.save();
    repo.awaitSaveCompletion();
    waitForFxEvents();

    Recipe persisted = repo.findById(recipe.getId()).orElseThrow();
    assertThat(persisted.getTitle()).isEqualTo("Saved Title");
    assertThat(repo.saveCalls.get()).isEqualTo(1);
  }

  /** save runs asynchronously and toggles isSaving during operation. */
  @Test
  void e8_save_asyncAndSavingFlag() throws InterruptedException {
    repo.blockOnSave = true;
    vm.loadRecipe(recipe.getId());
    vm.toggleEditMode();
    vm.titleProperty().set("Async Title");
    Thread testThread = Thread.currentThread();

    vm.save();
    assertThat(repo.saveStarted.await(1, TimeUnit.SECONDS)).isTrue();
    assertThat(vm.isSaving()).isTrue();
    assertThat(repo.saveThread.get()).isNotSameAs(testThread);

    repo.releaseSave.countDown();
    repo.awaitSaveCompletion();
    waitForFxEvents();
    assertThat(vm.isSaving()).isFalse();
  }

  /** failed save keeps edit mode, preserves dirty state, and sets error message. */
  @Test
  void e9_saveFailure_preservesStateAndShowsError() throws InterruptedException {
    repo.failOnSave = true;
    vm.loadRecipe(recipe.getId());
    vm.toggleEditMode();
    vm.titleProperty().set("Will Fail");

    vm.save();
    repo.awaitSaveCompletion();
    waitUntil(() -> vm.getStatusMessage().startsWith("Save failed:"), 1000);

    assertThat(vm.isEditing()).isTrue();
    assertThat(vm.isDirty()).isTrue();
    assertThat(vm.getStatusMessage()).startsWith("Save failed:");
  }

  /** save is doesn't work when not dirty or when invalid. */
  @Test
  void e10_saveNoOp_whenNotDirtyOrInvalid() throws InterruptedException {
    vm.loadRecipe(recipe.getId());
    vm.toggleEditMode();

    vm.save();
    Thread.sleep(100);
    assertThat(repo.saveCalls.get()).isZero();

    vm.titleProperty().set("   ");
    assertThat(vm.isValid()).isFalse();
    vm.save();
    Thread.sleep(100);
    assertThat(repo.saveCalls.get()).isZero();
  }

  /** Inmemory repository with controls for async save testing. */
  private static final class InMemoryRecipeRepository implements RecipeRepository {
    private final AtomicReference<Recipe> stored = new AtomicReference<>();
    private final AtomicInteger saveCalls = new AtomicInteger();
    private final CountDownLatch saveCompleted = new CountDownLatch(1);
    private final CountDownLatch saveStarted = new CountDownLatch(1);
    private final CountDownLatch releaseSave = new CountDownLatch(1);
    private final AtomicReference<Thread> saveThread = new AtomicReference<>();
    private volatile boolean blockOnSave = false;
    private volatile boolean failOnSave = false;

    private InMemoryRecipeRepository(Recipe initial) {
      stored.set(initial);
    }

    @Override
    public void save(Recipe recipe) {
      saveCalls.incrementAndGet();
      saveThread.set(Thread.currentThread());
      saveStarted.countDown();
      try {
        if (blockOnSave) {
          releaseSave.await(2, TimeUnit.SECONDS);
        }
        if (failOnSave) {
          throw new RuntimeException("repository failure");
        }
        stored.set(recipe);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      } finally {
        saveCompleted.countDown();
      }
    }

    @Override
    public Optional<Recipe> findById(String id) {
      Recipe current = stored.get();
      if (current == null) {
        return Optional.empty();
      }
      return current.getId().equals(id) ? Optional.of(current) : Optional.empty();
    }

    @Override
    public Optional<Recipe> findByTitle(String title) {
      Recipe current = stored.get();
      if (current == null) {
        return Optional.empty();
      }
      return current.getTitle().equalsIgnoreCase(title) ? Optional.of(current) : Optional.empty();
    }

    @Override
    public List<Recipe> findAllByTitle(String title) {
      return findByTitle(title).stream().toList();
    }

    @Override
    public List<Recipe> findAll() {
      Recipe current = stored.get();
      return current == null ? List.of() : List.of(current);
    }

    @Override
    public void delete(String id) {
      Recipe current = stored.get();
      if (current != null && current.getId().equals(id)) {
        stored.set(null);
      }
    }

    private void awaitSaveCompletion() throws InterruptedException {
      saveCompleted.await(2, TimeUnit.SECONDS);
    }
  }

  private void waitUntil(BooleanSupplier condition, long timeoutMs) throws InterruptedException {
    long start = System.currentTimeMillis();
    while (System.currentTimeMillis() - start < timeoutMs) {
      waitForFxEvents();
      if (condition.getAsBoolean()) {
        return;
      }
      Thread.sleep(10);
    }
  }
}
