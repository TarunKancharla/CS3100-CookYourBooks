package app.cookyourbooks.gui;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

/**
 * Shared utility for displaying error dialogs in the CookYourBooks GUI.
 *
 * <p>Each feature's {@code onFailure} callback (from {@link BackgroundTaskRunner}) can delegate
 * here rather than constructing {@link Alert} dialogs inline.
 */
public final class ErrorHandler {

  private ErrorHandler() {}

  /**
   * Shows a modal error dialog with the given title and message.
   *
   * <p>Must be called on the FX Application Thread.
   *
   * @param title the dialog title
   * @param message the error message to display
   */
  public static void showError(String title, String message) {
    Alert alert = new Alert(AlertType.ERROR);
    alert.setTitle(title);
    alert.setHeaderText(null);
    alert.setContentText(message);
    alert.showAndWait();
  }

  /**
   * Convenience overload that extracts the message from a {@link Throwable}.
   *
   * @param title the dialog title
   * @param cause the exception whose message is displayed
   */
  public static void showError(String title, Throwable cause) {
    String message = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    showError(title, message);
  }
}