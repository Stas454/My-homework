
package main.Exceptions;

public class AdminException extends Exception {

    public String message;

    public AdminException(String message) {
        super("AdminException");
        this.message = message;
    }
}
