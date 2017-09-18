package com.vortex.compiler.logic.header;

import com.vortex.compiler.content.SmartRegex;
import com.vortex.compiler.content.Token;
import com.vortex.compiler.logic.Acess;
import com.vortex.compiler.logic.Document;
import com.vortex.compiler.logic.LogicToken;
import com.vortex.compiler.logic.Type;
import com.vortex.compiler.logic.build.CppBuilder;
import com.vortex.compiler.logic.space.Workspace;
import com.vortex.compiler.logic.typedef.Typedef;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 02/10/2016
 */
public abstract class Header extends LogicToken implements Document {
    public Type type;

    //Leitura
    public Token acessToken, modifierToken, staticToken, volatileToken;

    //Conteudo Interno
    protected Acess acessValue;
    protected boolean staticValue;
    protected boolean finalValue;
    protected boolean abstractValue;
    protected boolean volatileValue;

    protected Header() {
    }

    public Header(Typedef container, Token token, Token tokens[], Type type, boolean accAcess, boolean accStatic,
                  boolean accAbstract, boolean accFinal, boolean accVolatile) {
        this.token = token;
        this.strFile = token.getStringFile();
        this.type = type;
        this.typedef = container;

        for (Token sToken : tokens) {
            if (SmartRegex.isAcess(sToken)) {
                if (!accAcess) {
                    addCleanErro("invalid modifier", sToken);
                } else if (acessToken == null) {
                    acessToken = sToken;
                } else {
                    addCleanErro("repeated acess modifier", sToken);
                }
            } else if (sToken.compare("static")) {
                if (!accStatic) {
                    addCleanErro("invalid modifier", sToken);
                } else if (staticToken == null) {
                    staticToken = sToken;
                } else {
                    addCleanErro("repeated modifier", sToken);
                }
            } else if (sToken.compare("abstract")) {
                if (!accAbstract) {
                    addCleanErro("invalid modifier", sToken);
                } else if (modifierToken == null) {
                    modifierToken = sToken;
                } else {
                    addCleanErro("repeated modifier", sToken);
                }
            } else if (sToken.compare("final")) {
                if (!accFinal) {
                    addCleanErro("invalid modifier", sToken);
                } else if (modifierToken == null) {
                    modifierToken = sToken;
                } else {
                    addCleanErro("repeated modifier", sToken);
                }
            } else if (sToken.compare("volatile")) {
                if (!accVolatile) {
                    addCleanErro("invalid modifier", sToken);
                } else if (volatileToken == null) {
                    volatileToken = sToken;
                } else {
                    addCleanErro("repeated modifier", sToken);
                }
            } else {
                break;
            }
        }
        acessValue = Acess.fromToken(acessToken);
        staticValue = staticToken != null;
        abstractValue = SmartRegex.compare(modifierToken, "abstract");
        finalValue = SmartRegex.compare(modifierToken, "final");
        volatileValue = volatileToken != null;
    }

    public abstract void load();

    public abstract void make();

    public abstract void build(CppBuilder cBuilder);

    public Workspace getWorkspace() {
        return getContainer().workspace;
    }

    public Acess getAcess() {
        return acessValue;
    }

    public boolean isStatic() {
        return staticValue;
    }

    public boolean isFinal() {
        return finalValue;
    }

    public boolean isAbstract() {
        return abstractValue;
    }

    public boolean isVolatile() {
        return abstractValue;
    }

    public void fixAcess(Acess acessValue) {
        this.acessValue = acessValue;
    }

    public void fixStatic(boolean staticValue) {
        this.staticValue = staticValue;
    }

    public void fixFinal(boolean finalValue) {
        this.finalValue = finalValue;
    }

    public void fixAbstract(boolean abstractValue) {
        this.abstractValue = abstractValue;
    }

    public void fixVolatile(boolean volatileValue) {
        this.volatileValue = volatileValue;
    }

    @Override
    public String getDocument() {
        return toString();
    }
}
