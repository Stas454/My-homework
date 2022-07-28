
package main.Exceptions;

public class HttpConnEx extends Exception {

    public String message;

    public HttpConnEx(String message) {
        super("HttpConnEx");
        this.message = message;
    }
}