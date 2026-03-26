package app.cookyourbooks.cli.commands;

import java.util.List;

import org.jspecify.annotations.NonNull;

import app.cookyourbooks.cli.CliContext;
import app.cookyourbooks.model.Recipe;

/** Searches recipes by ingredient. */
public final class SearchCommand extends AbstractCommand {

  public SearchCommand() {
    super(
        "search",
        "Find recipes by ingredient",
        "search <ingredient> - Find recipes containing the specified ingredient",
        "Recipe");
  }

  @Override
  public void execute(@NonNull List<String> args, @NonNull CliContext context) {
    if (args.isEmpty() || args.get(0).isBlank()) {
      context.println("Usage: search <ingredient>");
      return;
    }
    String ingredient = String.join(" ", args).trim();
    var matches = context.librarianService().searchByIngredient(ingredient);
    if (matches.isEmpty()) {
      context.println("No recipes found containing '" + ingredient + "'.");
      return;
    }
    context.println("Recipes containing '" + ingredient + "':");
    for (int i = 0; i < matches.size(); i++) {
      Recipe r = matches.get(i);
      String collName = findCollectionForRecipe(context, r);
      context.println("  " + (i + 1) + ". " + r.getTitle() + "         (" + collName + ")");
    }
    context.println("\nFound " + matches.size() + " recipes.");
  }

  private String findCollectionForRecipe(CliContext context, Recipe r) {
    for (var c : context.librarianService().listCollections()) {
      if (c.containsRecipe(r.getId())) {
        return c.getTitle();
      }
    }
    return "Unknown";
  }
}
