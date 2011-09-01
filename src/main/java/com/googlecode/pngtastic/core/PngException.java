/*
 * $Id: PngException.java 5 2010-07-17 22:50:19Z voidstar $
 * $URL: http://pngtastic.googlecode.com/svn/trunk/pngtastic/src/com/googlecode/pngtastic/core/PngException.java $
 */
package com.googlecode.pngtastic.core;

/**
 * Exception type for pngtastic code
 *
 * @author rayvanderborght
 */
@SuppressWarnings("serial")
public class PngException extends Exception
{
    /** */
    public PngException() {  }

    /** */
    public PngException(String message)
    {
        super(message);
    }

    /** */
    public PngException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
