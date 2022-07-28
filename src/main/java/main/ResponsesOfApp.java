
package main;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class ResponsesOfApp {

    static ObjectNode errorResponse(String description) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode errorResponse = mapper.createObjectNode();
        errorResponse.put("result", false);
        errorResponse.put("error", description);
        return errorResponse;
    }

    static ObjectNode successResponse() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode successResponse = mapper.createObjectNode();
        successResponse.put("result", true);
        return successResponse;
    }

    static ResponseEntity<ObjectNode> getSuccessResponse() {
        return new ResponseEntity<>(successResponse(), HttpStatus.OK);
    }

    static ResponseEntity<ObjectNode> getErrorResponse(String description, HttpStatus httpStatus) {
        return new ResponseEntity<>(errorResponse(description), httpStatus);
    }

}
