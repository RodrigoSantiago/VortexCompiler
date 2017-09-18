package com.vortex.compiler.logic.implementation.lineblock;

import com.vortex.compiler.content.Token;
import com.vortex.compiler.logic.Operator;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 18/10/2016
 */
public interface InnerLine {
    boolean isCall();
    default boolean isBlock(){
        return !isCall();
    }

    Operator getOperator();
    LineCall getCall();
    LineBlock getBlock();
    Token getToken();

    void load();
}
