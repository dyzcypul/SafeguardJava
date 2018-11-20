package com.oneidentity.safeguard.safeguardjava.exceptions;

public class ArgumentException extends Exception {

    public ArgumentException(String msg) {
        super(msg);
    }
    
    public ArgumentException(String msg, Exception cause) {
        super(msg, cause);
    }

    @Override
    public String getLocalizedMessage() {
        return getMessage();
    }

    @Override
    public String getMessage() {
        String retVal = "";
        if (null != super.getMessage()) {
            retVal = super.getMessage();
        }
        return retVal;
    }
    private static final long serialVersionUID = 1L;
    private int status = 500;

    public ArgumentException() {
        super();
    }

    public ArgumentException(Exception cause, int status) {
        super(cause);
        this.status = status;
    }

    public ArgumentException(String msg, int status) {
        super(msg);
        this.status = status;
    }
    
    public ArgumentException(String msg, Exception cause, int status) {
        super(msg, cause);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

}