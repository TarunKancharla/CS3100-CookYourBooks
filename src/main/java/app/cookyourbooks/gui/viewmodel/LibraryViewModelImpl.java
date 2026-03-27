package app.cookyourbooks.gui.viewmodel;

import app.cookyourbooks.gui.BackgroundTaskRunner;
import app.cookyourbooks.model.RecipeCollection;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;
import org.jspecify.annotations.NullMarked;

import app.cookyourbooks.gui.NavigationService;
import app.cookyourbooks.services.LibrarianService;

import java.util.ArrayList;
import java.util.List;

/** Implementation for the Library View Model. */
@NullMarked
public class LibraryViewModelImpl implements LibraryViewModel {

  /* Services */
  private final LibrarianService librarianService;
  private final NavigationService navigationService;

  /* ViewModel Values */
  BooleanProperty loadingProperty;

  /* General Values */
  List<RecipeCollection> recipeCollections;

  /* Constructor */
  /**
   * Instantiates the ViewModel values.
   */
  private void instantiateVMValues() {
    loadingProperty = new SimpleBooleanProperty();
  }

  private void instantiateGeneralValues() {
    recipeCollections = new ArrayList<>();
  }

  /**
   * Constructor for the Library View Model implementation. Dependencies should be injected.
   *
   * @param librarianService implementation for the LibrarianService
   * @param navigationService implementation for the NavigationService
   */
  public LibraryViewModelImpl(
      LibrarianService librarianService, NavigationService navigationService) {
    this.librarianService = librarianService;
    this.navigationService = navigationService;

    this.instantiateGeneralValues();
    this.instantiateVMValues();
  }

  /* Observable Properties */

  /* Commands */
  void refresh() {
    loadingProperty.set(true);

  }

}
