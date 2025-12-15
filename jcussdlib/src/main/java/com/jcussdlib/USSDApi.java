package com.jcussdlib;

import java.util.HashMap;

/**
 * Interface for USSD callback events
 */
public interface USSDApi {

    /**
     * Called when USSD response is received
     * @param message The USSD response message
     */
    void responseInvoke(String message);

    /**
     * Called when USSD session ends
     */
    void over(String message);
}
