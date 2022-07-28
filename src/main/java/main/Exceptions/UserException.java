
package main.Exceptions;

public class UserException extends Exception {

    public String message;

    public UserException(String message) {
        super("UserException");
        this.message = message;
    }

}
