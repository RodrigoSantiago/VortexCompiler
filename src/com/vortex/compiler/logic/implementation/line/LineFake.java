package com.vortex.compiler.logic.implementation.line;

import com.vortex.compiler.content.Token;
import com.vortex.compiler.logic.build.CppBuilder;
import com.vortex.compiler.logic.implementation.block.Block;

/**
 * @author Rodrigo Neiva Santiago
 *         Data : 12/04/2017.
 */
public class LineFake extends Line {

    //Escrita
    private String fakeContent;

    public LineFake(Block container, Token token, String fakeContent) {
        super(container, token);
        this.fakeContent = fakeContent;
    }

    @Override
    public void load() {

    }

    @Override
    public void build(CppBuilder cBuilder, int indent) {
        cBuilder.add(fakeContent);
    }
}
