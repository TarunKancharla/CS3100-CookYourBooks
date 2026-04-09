package app.cookyourbooks.gui.viewmodel;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import org.jspecify.annotations.Nullable;

import app.cookyourbooks.model.VagueIngredient;

/** Mutable, UI-friendly ingredient wrapper for text field binding. */
public final class EditableIngredient {

  private final StringProperty name = new SimpleStringProperty("");

  public EditableIngredient() {}

  public EditableIngredient(@Nullable String name) {
    setName(name != null ? name : "");
  }

  public StringProperty nameProperty() {
    return name;
  }

  public String getName() {
    return name.get();
  }

  public void setName(String name) {
    this.name.set(name);
  }

  public VagueIngredient toVagueIngredient() {
    return new VagueIngredient(getName().trim(), null, null, null);
  }

  @Override
  public String toString() {
    return getName();
  }
}
