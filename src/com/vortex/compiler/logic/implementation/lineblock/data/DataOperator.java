package com.vortex.compiler.logic.implementation.lineblock.data;

import com.vortex.compiler.content.SmartRegex;
import com.vortex.compiler.content.Token;
import com.vortex.compiler.logic.Operator;
import com.vortex.compiler.logic.header.OpOverload;
import com.vortex.compiler.logic.implementation.lineblock.LineCall;
import com.vortex.compiler.logic.typedef.Pointer;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 24/11/2016
 */
public class DataOperator extends Data {
    public Token operatorToken, castingToken;
    public Operator operator;
    public OpOverload operatorCall;

    public boolean betweenObjects;
    public Pointer castingPointer;

    public DataOperator(LineCall lineCall) {
        operatorToken = lineCall.getToken();

        if (operatorToken.isClosedBy("()")) {
            operator = Operator.cast;
            castingToken = lineCall.getToken().byNested();

            if (SmartRegex.pointer(castingToken)) {
                castingPointer = lineCall.getStack().findPointer(castingToken);
            }
            if (castingPointer == null) {
                lineCall.addErro("cannot resolve", lineCall.getToken());        //improvavel
            } else {
                lineCall.returnType = castingPointer;
            }
        } else {
            operator = Operator.fromToken(lineCall.getToken());
        }
    }
}
