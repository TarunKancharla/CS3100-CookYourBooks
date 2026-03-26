package app.cookyourbooks.gui;

import java.io.IOException;
import java.nio.file.Path;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.cookyourbooks.CybLibrary;
import app.cookyourbooks.gui.view.MainViewController;
import app.cookyourbooks.services.LibrarianServiceImpl;

/**
 * JavaFX entry point for CookYourBooks.
 *
 * <p>This class wires up the service layer, creates ViewModels, and launches the main window. It
 * demonstrates the dependency injection pattern you'll use to connect your feature ViewModels to
 * the service layer and navigation.
 *
 * <h2>Wiring pattern</h2>
 *
 * <ol>
 *   <li>Load the library (repositories + conversion registry)
 *   <li>Create service-layer objects
 *   <li>Create the shared {@link NavigationService}
 *   <li>Create your feature ViewModels, injecting services via constructors
 *   <li>Load each feature's FXML, injecting the ViewModel into the controller
 *   <li>Register each feature's view with the {@link MainViewController}
 * </ol>
 *
 * <h2>Adding your feature</h2>
 *
 * <p>Find the TODO comments below and follow the pattern to wire your ViewModel and View.
 */
public class CookYourBooksGuiApp extends Application {

  private static final Logger LOG = LoggerFactory.getLogger(CookYourBooksGuiApp.class);

  @Override
  public void start(Stage primaryStage) {
    // ── 1. Load the recipe library ──
    CybLibrary library = CybLibrary.load(Path.of("cyb-library.json"));

    // ── 2. Create services ──
    var librarianService =
        new LibrarianServiceImpl(
            library.getRecipeRepository(), library.getCollectionRepository(), library);
    // var recipeService = new RecipeServiceImpl(
    //     library.getRecipeRepository(), library.getCollectionRepository(),
    //     library.getConversionRegistry());
    // Also available: TransformerServiceImpl, CookingServiceImpl, PlannerServiceImpl

    LOG.info("Loaded {} collections", librarianService.listCollections().size());

    // ── 3. Create shared navigation ──
    var navigationService = new NavigationService();

    // ── 4. Create the main layout ──
    var mainController = new MainViewController(navigationService);

    // ── 5. Wire your feature ViewModels and Views ──
    //
    // For each feature you implement, follow this pattern:
    //
    //   // Create your ViewModel (inject services via constructor)
    //   var libraryVm = new LibraryViewModelImpl(librarianService, navigationService);
    //
    //   // Load your FXML view (inject the ViewModel into the controller)
    //   FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LibraryView.fxml"));
    //   loader.setControllerFactory(clazz -> new LibraryViewController(libraryVm));
    //   Parent libraryView = loader.load();
    //
    //   // Register the view with the main controller
    //   mainController.setViewNode(NavigationService.View.LIBRARY, libraryView);

    // TODO: Wire Library View (use librarianService)
    // TODO: Wire Recipe Editor
    // TODO: Wire Import Interface
    // TODO: Wire Search & Filter (teams of 4 only)

    // ── 6. Load the main layout and show the window ──
    try {
      FXMLLoader mainLoader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
      mainLoader.setControllerFactory(clazz -> mainController);
      Parent root = mainLoader.load();

      Scene scene = new Scene(root, 960, 640);
      primaryStage.setTitle("CookYourBooks");
      primaryStage.setScene(scene);
      primaryStage.show();
    } catch (IOException e) {
      throw new RuntimeException("Failed to load MainView.fxml", e);
    }
  }

  public static void main(String[] args) {
    launch(args);
  }
}
