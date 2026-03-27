package app.cookyourbooks.gui.view;

import app.cookyourbooks.gui.viewmodel.LibraryViewModel;

/** */
public class LibraryViewController {

  private final LibraryViewModel libraryViewModel;

  /**
   * Constructor for the View Model for the Library View feature. Dependencies must be injected.
   *
   * @param libraryViewModel implementation for the LibraryViewModel
   */
  public LibraryViewController(LibraryViewModel libraryViewModel) {
    this.libraryViewModel = libraryViewModel;
  }
}
