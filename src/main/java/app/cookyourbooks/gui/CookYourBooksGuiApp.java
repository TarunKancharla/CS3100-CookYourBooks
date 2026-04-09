package app.cookyourbooks.gui;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.cookyourbooks.CybLibrary;
import app.cookyourbooks.adapters.GeminiOcrAdapter;
import app.cookyourbooks.adapters.gemini.RealGeminiClient;
import app.cookyourbooks.gui.view.ImportViewController;
import app.cookyourbooks.gui.view.LibraryViewController;
import app.cookyourbooks.gui.view.MainViewController;
import app.cookyourbooks.gui.view.RecipeEditorViewController;
import app.cookyourbooks.gui.view.SearchViewController;
import app.cookyourbooks.gui.viewmodel.ImportViewModelImpl;
import app.cookyourbooks.gui.viewmodel.LibraryViewModelImpl;
import app.cookyourbooks.gui.viewmodel.RecipeEditorViewModelImpl;
import app.cookyourbooks.gui.viewmodel.SearchViewModelImpl;
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
    var libraryVm =
        new LibraryViewModelImpl(
            librarianService, navigationService, javafx.util.Duration.seconds(5));
    FXMLLoader libraryLoader = new FXMLLoader(getClass().getResource("/fxml/LibraryView.fxml"));
    libraryLoader.setControllerFactory(c -> new LibraryViewController(libraryVm));
    try {
      Parent libraryView = libraryLoader.load();
      mainController.setViewNode(NavigationService.View.LIBRARY, libraryView);
    } catch (IOException e) {
      System.err.printf("Was unable to load library exception.\n%s%n", e.getMessage());
    }

    var recipeEditorVm =
        new RecipeEditorViewModelImpl(library.getRecipeRepository(), navigationService);
    try {
      FXMLLoader editorLoader =
          new FXMLLoader(getClass().getResource("/fxml/RecipeEditorView.fxml"));
      editorLoader.setControllerFactory(clazz -> new RecipeEditorViewController(recipeEditorVm));
      Parent recipeEditorView = editorLoader.load();
      mainController.setViewNode(NavigationService.View.RECIPE_EDITOR, recipeEditorView);
    } catch (IOException e) {
      throw new RuntimeException("Failed to load RecipeEditorView.fxml", e);
    }
    // TODO: Wire Import Interface
    var ocrService = new GeminiOcrAdapter(new RealGeminiClient(System.getenv("GOOGLE_API_KEY")));
    var importVm = new ImportViewModelImpl(ocrService, librarianService);
    try {
      FXMLLoader importLoader = new FXMLLoader(getClass().getResource("/fxml/ImportView.fxml"));
      importLoader.setControllerFactory(clazz -> new ImportViewController(importVm));
      Parent importView = importLoader.load();
      mainController.setViewNode(NavigationService.View.IMPORT, importView);
    } catch (IOException e) {
      throw new RuntimeException("Failed to load ImportView.fxml", e);
    }

    // ── Wire Search & Filter ──
    var searchVm =
        new SearchViewModelImpl(librarianService, navigationService, Duration.ofMillis(300));
    try {
      FXMLLoader searchLoader = new FXMLLoader(getClass().getResource("/fxml/SearchView.fxml"));
      searchLoader.setControllerFactory(clazz -> new SearchViewController(searchVm));
      Parent searchView = searchLoader.load();
      mainController.setViewNode(NavigationService.View.SEARCH, searchView);
    } catch (IOException e) {
      throw new RuntimeException("Failed to load SearchView.fxml", e);
    }

    // ── 6. Load the main layout and show the window ──
    try {
      FXMLLoader mainLoader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
      mainLoader.setController(mainController);
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
