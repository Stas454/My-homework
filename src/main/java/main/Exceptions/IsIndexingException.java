
package main.Exceptions;

public class IsIndexingException extends Exception {

    public String message;

    public IsIndexingException(String message) {
        super("IsIndexingException");
        this.message = message;
    }

}
