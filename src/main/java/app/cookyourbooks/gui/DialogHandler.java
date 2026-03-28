package app.cookyourbooks.gui;

import java.util.Optional;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;

/**
 * Shared utility for displaying modal dialogs in the CookYourBooks GUI.
 *
 * <p>Covers three dialog types: error, information, and confirmation. All methods must be called on
 * the FX Application Thread.
 */
public final class DialogHandler {

  private DialogHandler() {}

  /**
   * Shows a modal error dialog.
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
    String message =
        cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    showError(title, message);
  }

  /**
   * Shows a modal information dialog.
   *
   * @param title the dialog title
   * @param message the message to display
   */
  public static void showInformation(String title, String message) {
    Alert alert = new Alert(AlertType.INFORMATION);
    alert.setTitle(title);
    alert.setHeaderText(null);
    alert.setContentText(message);
    alert.showAndWait();
  }

  /**
   * Shows a modal Yes/No confirmation dialog.
   *
   * @param title the dialog title
   * @param message the question to present to the user
   * @return {@code true} if the user clicked Yes, {@code false} if they clicked No or dismissed
   */
  public static boolean showConfirmation(String title, String message) {
    Alert alert = new Alert(AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
    alert.setTitle(title);
    alert.setHeaderText(null);
    Optional<ButtonType> result = alert.showAndWait();
    return result.isPresent() && result.get() == ButtonType.YES;
  }
}