package com.vortex.compiler.logic.implementation.lineblock.data;

import com.vortex.compiler.logic.implementation.lineblock.LineBlock;
import com.vortex.compiler.logic.implementation.lineblock.LineCall;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 25/11/2016
 */
public class DataInner extends Data {

    public LineBlock innerCall;

    public DataInner(LineCall lineCall) {
        innerCall = new LineBlock(lineCall.getCommandContainer(), lineCall.getToken().byNested(), lineCall.instance, false);
        innerCall.load();
        innerCall.requestGetAcess();
        lineCall.returnType = innerCall.getReturnType();
        if (innerCall.isWrong()) lineCall.setWrong();
        if (innerCall.getReturnType().isStatic) {
            lineCall.addErro("invalid line", innerCall.getToken());
        }
    }
}
