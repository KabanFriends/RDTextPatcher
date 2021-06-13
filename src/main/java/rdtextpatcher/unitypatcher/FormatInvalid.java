package rdtextpatcher.unitypatcher;

public class FormatInvalid extends Exception {

    public FormatInvalid(long position, Integer value) {
        super("Unexpected value " + value + " at position " + position);
    }
}