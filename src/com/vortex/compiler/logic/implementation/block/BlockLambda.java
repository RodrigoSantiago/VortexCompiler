package com.vortex.compiler.logic.implementation.block;

import com.vortex.compiler.content.Parser;
import com.vortex.compiler.content.Token;
import com.vortex.compiler.logic.Acess;
import com.vortex.compiler.logic.Type;
import com.vortex.compiler.logic.build.CppBuilder;
import com.vortex.compiler.logic.header.variable.Field;
import com.vortex.compiler.logic.header.variable.Params;
import com.vortex.compiler.logic.implementation.line.Line;
import com.vortex.compiler.logic.typedef.Pointer;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 17/01/2017
 */
public class BlockLambda  extends Block {

    public Pointer requestReturn, lambdaPointer;
    public Params params;

    public BlockLambda(Block container, Token token, Pointer requestReturn, Params params, Pointer lambdaPointer) {
        super(container, token);
        this.contentToken = token;
        this.requestReturn = requestReturn;
        this.params = params;
        this.lambdaPointer = lambdaPointer;
    }

    @Override
    public void load() {
        if (params != null) {
            for (int i = 0; i < params.pointers.size(); i++) {
                if (!addField(new Field(Type.LOCALVAR,
                        params.nameTokens.get(i), typedef, params.nameTokens.get(i).toString(),
                        params.pointers.get(i),
                        false, false, false,
                        params.finalTokens.get(i) == null, true, Acess.PUBLIC, Acess.PUBLIC))) {
                    addWarning("repeated variable name", params.nameTokens.get(i));
                }
            }
        }
        if (contentToken != null) {
            Parser.parseCommands(this, contentToken.byNested(), false);
        }
    }

    @Override
    public void build(CppBuilder cBuilder, int indent) {
        //cBuilder.idt(indent);
        cBuilder.path(lambdaPointer).add("([=](").add(params).add(") -> ").add(requestReturn).add(" ").begin(indent + 1);

        for (Line line : lines) {
            line.build(cBuilder, indent + 1);
        }

        cBuilder.idt(indent).end("").add(")");

    }

    public Pointer getRequestReturn() {
        return requestReturn;
    }
}
