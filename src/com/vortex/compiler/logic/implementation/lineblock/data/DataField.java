package com.vortex.compiler.logic.implementation.lineblock.data;

import com.vortex.compiler.content.Token;
import com.vortex.compiler.logic.header.variable.Field;
import com.vortex.compiler.logic.implementation.lineblock.LineCall;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 24/11/2016
 */
public class DataField extends Data {
    public Token nameToken;
    public Field fieldCall;
    public boolean superCall;

    public DataField(LineCall lineCall) {
        nameToken = lineCall.getToken();
        Field field = lineCall.findField(nameToken);
        if (field != null) {
            fieldCall = field;
            superCall = field.getName().equals("super");
            lineCall.returnType = fieldCall.getType();
            if (field.isAbstract()) {
                if (lineCall.isFromSuperCall()) {
                    lineCall.addCleanErro("super abstract properties should not be called", nameToken);
                }
            }
        } else {
            lineCall.addErro("cannot resolve", nameToken);
        }
    }
}
