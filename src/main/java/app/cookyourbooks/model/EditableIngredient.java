package app.cookyourbooks.model;

import org.jspecify.annotations.Nullable;

/**
 * Wrapper for the {@code app.cookyourbooks.model.Ingredient} class.
 * Allows for mutability.
 */
public abstract class EditableIngredient {

    /* Fields */
    private String name;
    private @Nullable String preparation;
    private @Nullable String notes;

    /* Constructor */

    /**
     * Constructs an editable ingredient with the given name, preparation, and notes.
     * Editable ingredients are mutable.
     * @param name the name of the ingredient (must not be null or blank(
     * @param preparation optional preparation instructions
     * @param notes optional notes about the ingredient
     * @throws IllegalArgumentException if name is blank
     */
    protected EditableIngredient(String name, @Nullable String preparation, @Nullable String notes) {
        if (name.isBlank()) { throw new IllegalArgumentException("name must not be blank"); }

        this.name = name;
        this.preparation = preparation;
        this.notes = notes;
    }

    /* Getters */

    /**
     * Returns the name of the ingredient
     * @return the ingredient name
     */
    public String getName() { return name; }

    /**
     * Returns the preparation instructions for this ingredient.
     * @return the preparation instructions, or null if none
     */
    public @Nullable String getPreparation() { return preparation; }

    /**
     * Returns any notes about this ingredient
     * @return the notes, or null if none
     */
    public @Nullable String getNotes() { return notes; }

    /**
     * Returns a human-readable string representation of this ingredient.
     * @return a formatted string representation
     */
    public abstract String toString();

    /**
     * Compares this ingredient with the specified object for equality.
     * @param o   the reference object with which to compare.
     * @return true if the objects are equal, false otherwise
     */
    public abstract boolean equals(@Nullable Object o);

    /**
     * Returns a hash code value for this ingredient.
     * @return a hash code value
     */
    public abstract int hashCode();

    /**
     * Returns a new Ingredient object with the specified/set values.
     * Ingredients are immutable and may be used for database persistence.
     * @return the immutable ingredient
     */
    public abstract Ingredient compileAsIngredient();

    /* Setters */

    /**
     * Sets the name of the ingredient.
     * @param name the new name for the ingredient (must not be blank or null)
     * @throws IllegalArgumentException if the name is blank
     */
    public void setName(String name) {
        if (name.isBlank()) { throw new IllegalArgumentException("name must not be blank"); }
        this.name = name;
    }

    /**
     * Sets the preparation instructions for the ingredient.
     * @param preparation the new preparation instructions
     */
    public void setPreparation(@Nullable String preparation) {
        this.preparation = preparation;
    }

    /**
     * Sets the preparation notes for the ingredient
     * @param notes the new notes
     */
    public void setNotes(@Nullable String notes) {
        this.notes = notes;
    }

}
