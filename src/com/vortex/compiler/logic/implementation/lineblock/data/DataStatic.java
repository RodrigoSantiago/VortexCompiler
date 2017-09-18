package com.vortex.compiler.logic.implementation.lineblock.data;

import com.vortex.compiler.content.Token;
import com.vortex.compiler.logic.implementation.lineblock.LineCall;
import com.vortex.compiler.logic.typedef.Pointer;
import com.vortex.compiler.logic.typedef.Typedef;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 24/11/2016
 */
public class DataStatic extends Data {
    public Token typedefToken;
    public Pointer staticCall;

    public DataStatic(LineCall lineCall) {
        typedefToken = lineCall.getToken();

        Typedef staticTypedef = lineCall.getStack().getWorkspace().getTypedef(lineCall.getToken());
        if (staticTypedef != null) {
            staticCall = staticTypedef.getPointer().byStatic();
            lineCall.returnType = staticCall;
        } else {
            lineCall.addErro("cannot resolve", lineCall.getToken());
        }
    }
}
