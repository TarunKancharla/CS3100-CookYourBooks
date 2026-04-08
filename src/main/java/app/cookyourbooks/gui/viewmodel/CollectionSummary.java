package app.cookyourbooks.gui.viewmodel;

import app.cookyourbooks.model.RecipeCollection;

/** Lightweight collection DTO for selection lists. */
public record CollectionSummary(String id, String title) {
  public static CollectionSummary from(RecipeCollection collection) {
    return new CollectionSummary(collection.getId(), collection.getTitle());
  }

  @Override
  public String toString() {
    return title;
  }
}
