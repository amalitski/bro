package ru.timebook.bro.flow.exceptions;

public class FlowRuntimeException extends RuntimeException {
    public FlowRuntimeException(String msg) {
        super(msg);
    }

    public FlowRuntimeException(Throwable cause) {
        super(cause);
    }
}
