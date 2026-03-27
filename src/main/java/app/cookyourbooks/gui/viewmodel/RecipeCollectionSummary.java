package app.cookyourbooks.gui.viewmodel;

import app.cookyourbooks.model.SourceType;

/**
 * A record that represents various details for a recipe collection.
 * @param id the id of the collection
 * @param title the title of the collection
 * @param sourceType the type of source the recipe collection is
 * @param recipeCount the amount of recipes in the collection
 */
public record RecipeCollectionSummary(String id, String title, SourceType sourceType, int recipeCount) {
}
