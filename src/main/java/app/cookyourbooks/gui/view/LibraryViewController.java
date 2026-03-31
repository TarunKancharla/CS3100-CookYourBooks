package app.cookyourbooks.gui.view;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;

import app.cookyourbooks.gui.viewmodel.LibraryViewModel;
import app.cookyourbooks.gui.viewmodel.RecipeCollectionSummary;
import app.cookyourbooks.model.Recipe;

/** */
public class LibraryViewController {

  private final LibraryViewModel libraryViewModel;

  private final ObservableList<RecipeCollectionSummary> collectionsProperty;
  private final StringProperty filterTextProperty;
  private final ObservableList<Recipe> recipesProperty;
  private final BooleanProperty loadingProperty;
  private final BooleanProperty undoAvailableProperty;
  private final StringProperty undoMessageProperty;

  /**
   * Constructor for the View Model for the Library View feature. Dependencies must be injected.
   *
   * @param libraryViewModel implementation for the LibraryViewModel
   */
  @SuppressWarnings("unchecked") // TODO: address this
  public LibraryViewController(LibraryViewModel libraryViewModel) {
    this.libraryViewModel = libraryViewModel;

    this.collectionsProperty =
        (ObservableList<RecipeCollectionSummary>) libraryViewModel.collectionsProperty();
    this.recipesProperty = (ObservableList<Recipe>) libraryViewModel.recipesProperty();

    this.filterTextProperty = libraryViewModel.filterTextProperty();
    this.loadingProperty = libraryViewModel.loadingProperty();
    this.undoAvailableProperty = libraryViewModel.undoAvailableProperty();
    this.undoMessageProperty = libraryViewModel.undoMessageProperty();
  }
}
