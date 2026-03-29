package app.cookyourbooks.model;

/**
 * Represents a mutable MeasuredIngredient.
 */
public class EditableMeasuredIngredient extends EditableIngredient {

    /* Fields */
    private Quantity quantity;

    /* Constructor */

    /**
     * Constructs an editable measured ingredient with the given name, quantity, preparation, and notes.
     * @param name the name of the ingredient (must not be null or blank)
     * @param quantity the quantity of the ingredient (must not be null)
     * @param preparation optional preparation instructions
     * @param notes optional notes about the ingerdient
     * @throws IllegalArgumentException if the name is null or blank, or if quantity is null
     */
    public EditableMeasuredIngredient(String name, Quantity quantity, String preparation, String notes) {
        super(name, preparation, notes);
        this.quantity = quantity;
    }

    /* Getters */

    /**
     * Returns the quantity of the ingredient
     * @return the quantity of the ingredient
     */
    public Quantity getQuantity() { return quantity; }

    @Override
    public Ingredient compileAsIngredient() {
        return new MeasuredIngredient(this.getName(), this.quantity, this.getPreparation(), this.getNotes());
    }

    @Override
    public boolean equals(Object o) {
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
     * Sets the quantity of the ingredient.
     * @param quantity the new quantity (must not be null)
     * @throws IllegalArgumentException if the quantity is null
     */
    public void setQuantity(Quantity quantity) { this.quantity = quantity; }

}
