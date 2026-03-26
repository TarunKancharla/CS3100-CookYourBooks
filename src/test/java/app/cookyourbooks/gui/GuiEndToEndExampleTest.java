package app.cookyourbooks.gui;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.control.Button;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * Example end-to-end test for the CookYourBooks GUI.
 *
 * <p>This test demonstrates how to use TestFX with JUnit 5 and accessibility locators (node IDs and
 * {@code .lookup()}) to interact with and verify JavaFX UI elements.
 *
 * <p><b>Your graded E2E tests should follow this same pattern.</b> Use {@link
 * org.testfx.api.FxRobot} methods to interact with the UI:
 *
 * <ul>
 *   <li>{@code robot.lookup("#nodeId")} — find a node by its {@code fx:id} (set via {@code
 *       node.setId("nodeId")})
 *   <li>{@code robot.clickOn("#button")} — click a button or other interactive element
 *   <li>{@code robot.write("text")} — type text into the focused text field
 *   <li>{@code robot.lookup(".style-class")} — find nodes by CSS style class
 * </ul>
 */
@ExtendWith(ApplicationExtension.class)
class GuiEndToEndExampleTest {

  /**
   * Called before each test to start the JavaFX application. This replaces the normal {@code
   * Application.launch()} call.
   */
  @Start
  private void start(Stage stage) {
    // Launch the app on the provided stage
    CookYourBooksGuiApp app = new CookYourBooksGuiApp();
    app.start(stage);
  }

  @Test
  void appLaunchesWithExpectedTitle(FxRobot robot) {
    // Verify the window title
    Stage stage = (Stage) robot.listWindows().get(0);
    assertThat(stage.getTitle()).isEqualTo("CookYourBooks");
  }

  @Test
  void sidebarNavigationButtonsArePresent(FxRobot robot) {
    // Use accessibility locators (fx:id) to find sidebar buttons.
    // In your feature views, set IDs on key elements: node.setId("recipe-list") etc.
    Button libraryBtn = robot.lookup("#libraryButton").queryAs(Button.class);
    assertThat(libraryBtn.getText()).isEqualTo("Library");

    Button editorBtn = robot.lookup("#editorButton").queryAs(Button.class);
    assertThat(editorBtn.getText()).isEqualTo("Recipe Editor");

    Button importBtn = robot.lookup("#importButton").queryAs(Button.class);
    assertThat(importBtn.getText()).isEqualTo("Import");

    Button searchBtn = robot.lookup("#searchButton").queryAs(Button.class);
    assertThat(searchBtn.getText()).isEqualTo("Search");
  }
}
