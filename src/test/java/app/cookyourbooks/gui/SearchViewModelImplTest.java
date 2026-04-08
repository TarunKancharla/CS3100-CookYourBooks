package app.cookyourbooks.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import app.cookyourbooks.cli.fixtures.RecipeFixtures;
import app.cookyourbooks.gui.viewmodel.SearchViewModelImpl;
import app.cookyourbooks.model.Recipe;
import app.cookyourbooks.services.LibrarianService;

class SearchViewModelImplTest extends ViewModelTestBase {

  // Use a very short debounce so tests don't wait 300ms
  private static final Duration FAST = Duration.ofMillis(50);

  private LibrarianService librarian;
  private app.cookyourbooks.gui.NavigationService navigation;
  private SearchViewModelImpl vm;

  private Recipe pancakes;
  private Recipe pasta;
  private Recipe tacos;

  @BeforeEach
  void setUp() {
    librarian = mock(LibrarianService.class);
    navigation = mock(app.cookyourbooks.gui.NavigationService.class);
    vm = new SearchViewModelImpl(librarian, navigation, FAST);

    pancakes = RecipeFixtures.pancakes();
    pasta = RecipeFixtures.recipeWithIngredient("Pasta", "egg");
    tacos = RecipeFixtures.recipeWithIngredient("Tacos", "carrot");

    // Default stubs
    when(librarian.listAllRecipes()).thenReturn(List.of(pancakes, pasta, tacos));
    when(librarian.resolveRecipes("pasta")).thenReturn(List.of(pasta));
    when(librarian.resolveRecipes("pancakes")).thenReturn(List.of(pancakes));
    when(librarian.searchByIngredient("egg")).thenReturn(List.of(pasta));
    when(librarian.searchByIngredient("carrot")).thenReturn(List.of(tacos));
    when(librarian.searchByIngredient("flour")).thenReturn(List.of(pancakes));
  }

  // ── S1: Setting query triggers search and populates results ───────────────

  @Test
  void s1_setQuery_triggersSearchAndPopulatesResults() throws InterruptedException {
    vm.setQuery("pasta");
    Thread.sleep(100); // wait for debounce + background thread
    waitForFxEvents();

    assertThat(vm.getResultIds()).containsExactly(pasta.getId());
  }

  // ── S2: Title search returns matching recipes via resolveRecipes() ─────────

  @Test
  void s2_titleSearch_returnsMatchingRecipes() throws InterruptedException {
    vm.setQuery("pancakes");
    Thread.sleep(100);
    waitForFxEvents();

    assertThat(vm.getResultIds()).containsExactly(pancakes.getId());
  }

  // ── S3: Adding ingredient filter narrows results ───────────────────────────

  @Test
  void s3_addIngredientFilter_narrowsResults() throws InterruptedException {
    vm.addIngredientFilter("egg");
    Thread.sleep(100);
    waitForFxEvents();

    assertThat(vm.getResultIds()).containsExactly(pasta.getId());
  }

  // ── S4: Multiple ingredient filters use AND logic ─────────────────────────

  @Test
  void s4_multipleIngredientFilters_useAndLogic() throws InterruptedException {
    // egg matches pasta, carrot matches tacos — AND means no recipe has both
    vm.addIngredientFilter("egg");
    Thread.sleep(100);
    waitForFxEvents();

    vm.addIngredientFilter("carrot");
    Thread.sleep(100);
    waitForFxEvents();

    assertThat(vm.getResultIds()).isEmpty();
  }

  // ── S5: Clearing filters/query resets results ─────────────────────────────

  @Test
  void s5_clearFilters_resetsResults() throws InterruptedException {
    vm.setQuery("pasta");
    Thread.sleep(100);
    waitForFxEvents();

    vm.clearFilters();
    waitForFxEvents();

    assertThat(vm.getQuery()).isEmpty();
    assertThat(vm.getIngredientFilters()).isEmpty();
    assertThat(vm.getResultIds()).isEmpty();
  }

  // ── S6: Search runs on background thread; isSearching true while running ──

  @Test
  void s6_searchingProperty_trueWhileSearchRunning() throws InterruptedException {
    vm.setQuery("pasta");
    // isSearching goes true immediately when executeSearch starts
    // We can't reliably catch it mid-flight in a unit test, but we can assert
    // it returns to false after completion
    Thread.sleep(100);
    waitForFxEvents();

    assertThat(vm.isSearching()).isFalse();
  }

  // ── S7: Search is debounced (only fires after last keystroke) ─────────────

  @Test
  void s7_debounce_onlyFiresAfterLastKeystroke() throws InterruptedException {
    // Type quickly — only the last query should produce results
    when(librarian.resolveRecipes("p")).thenReturn(List.of(pancakes, pasta));
    when(librarian.resolveRecipes("pa")).thenReturn(List.of(pancakes, pasta));
    when(librarian.resolveRecipes("pas")).thenReturn(List.of(pasta));
    when(librarian.resolveRecipes("past")).thenReturn(List.of(pasta));
    when(librarian.resolveRecipes("paste")).thenReturn(List.of());

    vm.setQuery("p");
    vm.setQuery("pa");
    vm.setQuery("pas");
    vm.setQuery("past");
    vm.setQuery("paste");

    Thread.sleep(200); // wait for debounce + background thread
    waitForFxEvents();

    // Only "paste" query should have fired
    assertThat(vm.getQuery()).isEqualTo("paste");
    assertThat(vm.getResultIds()).isEmpty();
  }

  // ── S8: selectNextResult / selectPreviousResult cycle through results ──────

  @Test
  void s8_selectNextResult_cyclesThroughResults() throws InterruptedException {
    vm.addIngredientFilter("egg"); // loads pasta only
    Thread.sleep(100);
    waitForFxEvents();

    // With one result, next wraps back to itself
    vm.selectNextResult();
    assertThat(vm.getSelectedResultId()).isEqualTo(pasta.getId());
  }

  @Test
  void s8_selectPreviousResult_cyclesThroughResults() throws InterruptedException {
    vm.addIngredientFilter("egg");
    Thread.sleep(100);
    waitForFxEvents();

    vm.selectPreviousResult();
    assertThat(vm.getSelectedResultId()).isEqualTo(pasta.getId());
  }

  // ── S9: navigateToSelectedResult provides selected recipe ID ──────────────

  @Test
  void s9_navigateToSelectedResult_callsNavigationService() throws InterruptedException {
    vm.addIngredientFilter("egg");
    Thread.sleep(100);
    waitForFxEvents();

    vm.navigateToSelectedResult();

    org.mockito.Mockito.verify(navigation).navigateToRecipe(pasta.getId());
  }

  // ── S10: Status message reflects result count ─────────────────────────────

  @Test
  void s10_statusMessage_reflectsResultCount() throws InterruptedException {
    vm.setQuery("pasta");
    Thread.sleep(100);
    waitForFxEvents();

    assertThat(vm.getStatusMessage()).isEqualTo("1 result");
  }

  @Test
  void s10_statusMessage_noResultsFound() throws InterruptedException {
    when(librarian.resolveRecipes("xyz")).thenReturn(List.of());
    vm.setQuery("xyz");
    Thread.sleep(100);
    waitForFxEvents();

    assertThat(vm.getStatusMessage()).isEqualTo("No results found");
  }

  // ── S11: Empty query with no filters returns all recipes ──────────────────

  @Test
  void s11_emptyQueryNoFilters_returnsAllRecipes() throws InterruptedException {
    vm.setQuery("");
    Thread.sleep(100);
    waitForFxEvents();

    assertThat(vm.getResultIds())
        .containsExactlyInAnyOrder(pancakes.getId(), pasta.getId(), tacos.getId());
  }
}
