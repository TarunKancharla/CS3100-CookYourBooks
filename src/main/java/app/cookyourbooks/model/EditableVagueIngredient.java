package app.cookyourbooks.model;

import org.jspecify.annotations.Nullable;

/**
 * Represents a mutable VagueIngredient.
 */
public class EditableVagueIngredient extends EditableIngredient {

    /* Fields */
    private @Nullable String description;

    /* Constructor */

    /**
     * Constructs an editable vague ingredient with the given properties.
     * @param name the name of the ingredient (must not be null or blank)
     * @param description optional description
     * @param notes optional preparation notes
     * @param preparation optional notes about the ingredient
     * @throws IllegalArgumentException if the name is null or blank
     */
    public EditableVagueIngredient(String name, @Nullable String description, @Nullable String notes, @Nullable String preparation) {
        super(name, preparation, notes);
        this.description = (description != null) ? description.trim() : null;
    }

    /* Getters */

    /**
     * Returns the description for this vague ingredient.
     * @return the description, or null if none
     */
    public @Nullable String getDescription() { return description; }

    @Override
    public Ingredient compileAsIngredient() {
        return new VagueIngredient(this.getName(), this.description, this.getPreparation(), this.getNotes());
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return this.compileAsIngredient().equals(o);
    }

    @Override
    public int hashCode() {
        return this.compileAsIngredient().hashCode();
    }

    @Override
    public String toString() {
        return this.compileAsIngredient().toString();
    }

    /* Setters */

    /**
     * Sets the description for this vague ingredient. May be null
     * @param description the new description
     */
    public void setDescription(@Nullable String description) {
        this.description = (description != null) ? description.trim() : null;
    }

}
