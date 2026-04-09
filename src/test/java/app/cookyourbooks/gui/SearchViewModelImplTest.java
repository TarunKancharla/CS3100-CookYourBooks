package app.cookyourbooks.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

  // Short debounce so tests don't wait 300 ms
  private static final Duration FAST = Duration.ofMillis(50);

  private LibrarianService librarian;
  private NavigationService navigation;
  private SearchViewModelImpl vm;

  private Recipe pancakes;
  private Recipe pasta;
  private Recipe tacos;

  @BeforeEach
  void setUp() {
    librarian = mock(LibrarianService.class);
    navigation = mock(NavigationService.class);
    vm = new SearchViewModelImpl(librarian, navigation, FAST);

    // pancakes() → 3 ingredients (flour, milk, salt), 2 instructions
    pancakes = RecipeFixtures.pancakes();
    pasta = RecipeFixtures.recipeWithIngredient("Pasta", "egg");
    tacos = RecipeFixtures.recipeWithIngredient("Tacos", "carrot");

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
    Thread.sleep(100);
    waitForFxEvents();

    assertThat(vm.getResultIds()).containsExactly(pasta.getId());
  }

  // ── S2: Title search uses resolveRecipes() ────────────────────────────────

  @Test
  void s2_titleSearch_returnsMatchingRecipes() throws InterruptedException {
    vm.setQuery("pancakes");
    Thread.sleep(100);
    waitForFxEvents();

    assertThat(vm.getResultIds()).containsExactly(pancakes.getId());
  }

  @Test
  void s2_queryProperty_updatesWhenQueryIsSet() throws InterruptedException {
    vm.setQuery("pasta");
    Thread.sleep(100);
    waitForFxEvents();

    assertThat(vm.getQuery()).isEqualTo("pasta");
    assertThat(vm.queryProperty().get()).isEqualTo("pasta");
  }

  // ── S3: Ingredient filter narrows and broadens results ────────────────────

  @Test
  void s3_addIngredientFilter_narrowsResults() throws InterruptedException {
    vm.addIngredientFilter("egg");
    Thread.sleep(100);
    waitForFxEvents();

    assertThat(vm.getResultIds()).containsExactly(pasta.getId());
    assertThat(vm.getIngredientFilters()).containsExactly("egg");
  }

  @Test
  void s3_removeIngredientFilter_broadensResults() throws InterruptedException {
    // Narrow to pancakes only via the flour filter
    vm.addIngredientFilter("flour");
    Thread.sleep(100);
    waitForFxEvents();
    assertThat(vm.getResultIds()).containsExactly(pancakes.getId());

    // Remove filter — all recipes should come back
    vm.removeIngredientFilter("flour");
    Thread.sleep(100);
    waitForFxEvents();

    assertThat(vm.getResultIds())
        .containsExactlyInAnyOrder(pancakes.getId(), pasta.getId(), tacos.getId());
    assertThat(vm.getIngredientFilters()).isEmpty();
  }

  @Test
  void s3_removeNonExistentIngredientFilter_leavesStateUnchanged() throws InterruptedException {
    // Removing a filter that was never added should not throw
    vm.removeIngredientFilter("noodles");
    Thread.sleep(100);
    waitForFxEvents();

    assertThat(vm.getIngredientFilters()).isEmpty();
    // Search still ran with empty query + no filters → all recipes
    assertThat(vm.getResultIds())
        .containsExactlyInAnyOrder(pancakes.getId(), pasta.getId(), tacos.getId());
  }

  @Test
  void s3_addIngredientFilter_duplicate_isIgnored() throws InterruptedException {
    vm.addIngredientFilter("egg");
    vm.addIngredientFilter("egg");
    Thread.sleep(100);
    waitForFxEvents();

    // Filter list should contain "egg" exactly once
    assertThat(vm.getIngredientFilters()).containsExactly("egg");
  }

  // ── S4: Multiple ingredient filters use AND logic ─────────────────────────

  @Test
  void s4_multipleIngredientFilters_useAndLogic() throws InterruptedException {
    // egg matches pasta, carrot matches tacos — no recipe has both
    vm.addIngredientFilter("egg");
    Thread.sleep(100);
    waitForFxEvents();

    vm.addIngredientFilter("carrot");
    Thread.sleep(100);
    waitForFxEvents();

    assertThat(vm.getResultIds()).isEmpty();
  }

  @Test
  void s4_combinedTitleQueryAndIngredientFilter_intersectsCorrectly() throws InterruptedException {
    // "pancakes" resolves to [pancakes]; filter "egg" matches only [pasta]
    // intersection = empty
    vm.setQuery("pancakes");
    vm.addIngredientFilter("egg");
    Thread.sleep(200);
    waitForFxEvents();

    assertThat(vm.getResultIds()).isEmpty();
  }

  // ── S5: Clearing filters/query resets all state ───────────────────────────

  @Test
  void s5_clearFilters_resetsAllState() throws InterruptedException {
    vm.setQuery("pasta");
    vm.addIngredientFilter("egg");
    Thread.sleep(100);
    waitForFxEvents();

    vm.clearFilters();
    waitForFxEvents();

    assertThat(vm.getQuery()).isEmpty();
    assertThat(vm.getIngredientFilters()).isEmpty();
    assertThat(vm.getResultIds()).isEmpty();
    assertThat(vm.getSelectedResultId()).isNull();
    assertThat(vm.getStatusMessage()).isEmpty();
  }

  @Test
  void s5_clearFilters_isIdempotent() {
    // Calling clearFilters on an already-cleared VM should not crash
    vm.clearFilters();
    vm.clearFilters();

    assertThat(vm.getQuery()).isEmpty();
    assertThat(vm.getIngredientFilters()).isEmpty();
    assertThat(vm.getResultIds()).isEmpty();
    assertThat(vm.getSelectedResultId()).isNull();
    assertThat(vm.getStatusMessage()).isEmpty();
  }

  // ── S6: isSearching is false after the background task settles ───────────

  @Test
  void s6_searchingProperty_falseAfterSearchCompletes() throws InterruptedException {
    vm.setQuery("pasta");
    Thread.sleep(100);
    waitForFxEvents();

    assertThat(vm.isSearching()).isFalse();
  }

  @Test
  void s6_searchingProperty_falseAfterSearchFails() throws InterruptedException {
    when(librarian.resolveRecipes("boom")).thenThrow(new RuntimeException("DB error"));

    vm.setQuery("boom");
    Thread.sleep(100);
    waitForFxEvents();

    assertThat(vm.isSearching()).isFalse();
  }

  // ── S7: Debounce — only the final keystroke fires a search ────────────────

  @Test
  void s7_debounce_onlyFiresAfterLastKeystroke() throws InterruptedException {
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

    Thread.sleep(200);
    waitForFxEvents();

    assertThat(vm.getQuery()).isEqualTo("paste");
    assertThat(vm.getResultIds()).isEmpty();
    // Confirm only one service call was made (for the final query)
    verify(librarian, times(1)).resolveRecipes("paste");
  }

  // ── S8: selectNextResult / selectPreviousResult ───────────────────────────

  @Test
  void s8_selectNextResult_onEmptyResults_isNoOp() {
    // No search has run; results is empty
    vm.selectNextResult();

    assertThat(vm.getSelectedResultId()).isNull();
  }

  @Test
  void s8_selectPreviousResult_onEmptyResults_isNoOp() {
    vm.selectPreviousResult();

    assertThat(vm.getSelectedResultId()).isNull();
  }

  @Test
  void s8_selectNextResult_withSingleResult_staysOnSameItem() throws InterruptedException {
    vm.addIngredientFilter("egg"); // only pasta
    Thread.sleep(100);
    waitForFxEvents();

    vm.selectNextResult(); // wraps back to itself
    assertThat(vm.getSelectedResultId()).isEqualTo(pasta.getId());
  }

  @Test
  void s8_selectPreviousResult_withSingleResult_staysOnSameItem() throws InterruptedException {
    vm.addIngredientFilter("egg");
    Thread.sleep(100);
    waitForFxEvents();

    vm.selectPreviousResult();
    assertThat(vm.getSelectedResultId()).isEqualTo(pasta.getId());
  }

  @Test
  void s8_selectNextResult_advancesThroughMultipleResults() throws InterruptedException {
    // All 3 results: pancakes (0), pasta (1), tacos (2)
    vm.setQuery("");
    Thread.sleep(100);
    waitForFxEvents();
    // First result is auto-selected after search
    assertThat(vm.getSelectedResultId()).isEqualTo(pancakes.getId());

    vm.selectNextResult();
    assertThat(vm.getSelectedResultId()).isEqualTo(pasta.getId());

    vm.selectNextResult();
    assertThat(vm.getSelectedResultId()).isEqualTo(tacos.getId());
  }

  @Test
  void s8_selectNextResult_wrapsFromLastToFirst() throws InterruptedException {
    // All 3 results: pancakes (0), pasta (1), tacos (2)
    vm.setQuery("");
    Thread.sleep(100);
    waitForFxEvents();

    vm.selectNextResult(); // pancakes → pasta
    vm.selectNextResult(); // pasta → tacos
    vm.selectNextResult(); // tacos → wraps to pancakes

    assertThat(vm.getSelectedResultId()).isEqualTo(pancakes.getId());
  }

  @Test
  void s8_selectPreviousResult_wrapsFromFirstToLast() throws InterruptedException {
    // All 3 results: pancakes (0), pasta (1), tacos (2)
    vm.setQuery("");
    Thread.sleep(100);
    waitForFxEvents();
    // pancakes is auto-selected (index 0)

    vm.selectPreviousResult(); // 0 → wraps to tacos (index 2)

    assertThat(vm.getSelectedResultId()).isEqualTo(tacos.getId());
  }

  // ── S9: navigateToSelectedResult ─────────────────────────────────────────

  @Test
  void s9_navigateToSelectedResult_callsNavigationService() throws InterruptedException {
    vm.addIngredientFilter("egg");
    Thread.sleep(100);
    waitForFxEvents();

    vm.navigateToSelectedResult();

    verify(navigation).navigateToRecipe(pasta.getId());
  }

  @Test
  void s9_navigateWithNoSelection_doesNotCallNavigationService() {
    // No search run; selectedResultId is null
    vm.navigateToSelectedResult();

    verify(navigation, never()).navigateToRecipe(anyString());
  }

  // ── S10: Status message reflects result count ─────────────────────────────

  @Test
  void s10_statusMessage_singularResult() throws InterruptedException {
    vm.setQuery("pasta");
    Thread.sleep(100);
    waitForFxEvents();

    assertThat(vm.getStatusMessage()).isEqualTo("1 result");
  }

  @Test
  void s10_statusMessage_pluralResults() throws InterruptedException {
    // Empty query returns all 3 recipes
    vm.setQuery("");
    Thread.sleep(100);
    waitForFxEvents();

    assertThat(vm.getStatusMessage()).isEqualTo("3 results");
  }

  @Test
  void s10_statusMessage_noResultsFound() throws InterruptedException {
    when(librarian.resolveRecipes("xyz")).thenReturn(List.of());
    vm.setQuery("xyz");
    Thread.sleep(100);
    waitForFxEvents();

    assertThat(vm.getStatusMessage()).isEqualTo("No results found");
  }

  @Test
  void s10_statusMessage_searchFailure() throws InterruptedException {
    when(librarian.resolveRecipes("boom")).thenThrow(new RuntimeException("DB error"));

    vm.setQuery("boom");
    Thread.sleep(100);
    waitForFxEvents();

    assertThat(vm.getStatusMessage()).isEqualTo("Search failed: DB error");
  }

  // ── S11: Empty / whitespace query returns all recipes ────────────────────

  @Test
  void s11_emptyQueryNoFilters_returnsAllRecipes() throws InterruptedException {
    vm.setQuery("");
    Thread.sleep(100);
    waitForFxEvents();

    assertThat(vm.getResultIds())
        .containsExactlyInAnyOrder(pancakes.getId(), pasta.getId(), tacos.getId());
  }

  @Test
  void s11_whitespaceOnlyQuery_treatedAsEmpty() throws InterruptedException {
    // Whitespace is trimmed inside executeSearch; should behave like empty query
    vm.setQuery("   ");
    Thread.sleep(100);
    waitForFxEvents();

    assertThat(vm.getResultIds())
        .containsExactlyInAnyOrder(pancakes.getId(), pasta.getId(), tacos.getId());
    verify(librarian).listAllRecipes();
  }

  // ── Auto-selection after search ───────────────────────────────────────────

  @Test
  void firstResult_isAutoSelectedAfterSearch() throws InterruptedException {
    vm.setQuery("");
    Thread.sleep(100);
    waitForFxEvents();

    assertThat(vm.getSelectedResultId()).isEqualTo(pancakes.getId());
  }

  @Test
  void noResults_selectedResultIdIsNull() throws InterruptedException {
    when(librarian.resolveRecipes("zzzz")).thenReturn(List.of());
    vm.setQuery("zzzz");
    Thread.sleep(100);
    waitForFxEvents();

    assertThat(vm.getSelectedResultId()).isNull();
  }

  // ── addIngredientFilter: negative — filter with no matches ────────────────

  @Test
  void addIngredientFilter_ingredientMatchesNoRecipes_resultsEmpty() throws InterruptedException {
    when(librarian.searchByIngredient("onion")).thenReturn(List.of());
    vm.addIngredientFilter("onion");
    Thread.sleep(100);
    waitForFxEvents();

    // Filter is recorded even though nothing matched
    assertThat(vm.getIngredientFilters()).containsExactly("onion");
    assertThat(vm.getResultIds()).isEmpty();
    assertThat(vm.getStatusMessage()).isEqualTo("No results found");
  }

  // ── removeIngredientFilter: edge — remove one of two active filters ───────

  @Test
  void removeIngredientFilter_oneOfTwoActive_remainingFilterStillApplied()
      throws InterruptedException {
    // egg→pasta, carrot→tacos; AND = empty
    vm.addIngredientFilter("egg");
    Thread.sleep(100);
    waitForFxEvents();
    vm.addIngredientFilter("carrot");
    Thread.sleep(100);
    waitForFxEvents();
    assertThat(vm.getResultIds()).isEmpty();

    // Remove carrot — only egg remains, pasta should come back
    vm.removeIngredientFilter("carrot");
    Thread.sleep(100);
    waitForFxEvents();

    assertThat(vm.getIngredientFilters()).containsExactly("egg");
    assertThat(vm.getResultIds()).containsExactly(pasta.getId());
  }

  // ── clearFilters: negative — cancels pending debounce ─────────────────────

  @Test
  void clearFilters_cancelsPendingDebounce_searchNeverFires() throws InterruptedException {
    vm.setQuery("pasta"); // starts the debounce timer
    vm.clearFilters(); // stops the timer before it fires
    Thread.sleep(200); // wait longer than the debounce delay
    waitForFxEvents();

    verify(librarian, never()).resolveRecipes(anyString());
    assertThat(vm.getResultIds()).isEmpty();
  }

  // ── selectPreviousResult: positive — advances backward without wrapping ───

  @Test
  void selectPreviousResult_advancesBackwardInList() throws InterruptedException {
    // Load all 3 results: pancakes (0), pasta (1), tacos (2)
    vm.setQuery("");
    Thread.sleep(100);
    waitForFxEvents();

    vm.selectNextResult(); // 0 → 1 (pasta)
    vm.selectNextResult(); // 1 → 2 (tacos)
    vm.selectPreviousResult(); // 2 → 1 (pasta) — backward advance, no wrapping

    assertThat(vm.getSelectedResultId()).isEqualTo(pasta.getId());
  }

  // ── navigateToSelectedResult: edge — selection cleared by clearFilters ────

  @Test
  void navigateToSelectedResult_afterClearFilters_doesNotNavigate() throws InterruptedException {
    vm.addIngredientFilter("egg");
    Thread.sleep(100);
    waitForFxEvents();
    assertThat(vm.getSelectedResultId()).isEqualTo(pasta.getId());

    vm.clearFilters(); // resets selectedResultId to null
    vm.navigateToSelectedResult(); // should be a no-op

    verify(navigation, never()).navigateToRecipe(anyString());
  }

  // ── RecipeResult: correct field mapping from Recipe ───────────────────────

  @Test
  void recipeResult_fieldsAreMappedCorrectly() throws InterruptedException {
    // pancakes() has 3 ingredients (flour, milk, salt) and 2 instructions
    vm.setQuery("pancakes");
    Thread.sleep(100);
    waitForFxEvents();

    assertThat(vm.resultsProperty()).hasSize(1);
    var result = vm.resultsProperty().get(0);
    assertThat(result.id()).isEqualTo(pancakes.getId());
    assertThat(result.title()).isEqualTo("Pancakes");
    assertThat(result.ingredientCount()).isEqualTo(3);
    assertThat(result.instructionCount()).isEqualTo(2);
  }
}
