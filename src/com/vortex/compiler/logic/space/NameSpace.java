package com.vortex.compiler.logic.space;

import com.vortex.compiler.content.SmartRegex;
import com.vortex.compiler.content.Token;
import com.vortex.compiler.data.Library;
import com.vortex.compiler.logic.LogicToken;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 01/10/2016
 */
public class NameSpace extends LogicToken {
    public static final String keyword = "namespace";

    public final String fullName;
    public final Library library;

    public Token keywordToken, nameToken;

    public NameSpace(Token token, Token[] tokens) {
        this.token = token;
        this.strFile = token.getStringFile();
        this.library = strFile.library;

        if (token.isEmpty()) {
            this.fullName = library.name;
        } else {
            //[0-namesapce][1-full::name][2-;]
            boolean lastHasErro = false;
            int stage = 0;
            for (Token sToken : tokens) {
                lastHasErro = false;
                if (stage == 0 && sToken.compare(keyword)) {
                    keywordToken = sToken;
                    stage = 1;
                } else if (stage == 1 && SmartRegex.spaceName(sToken)) {
                    if (!sToken.startsWith("::")) {
                        nameToken = sToken;
                    } else {
                        addCleanErro("invalid namespace name", sToken);
                    }
                    stage = 2;
                } else if (stage == 2 && sToken.compare(";")) {
                    stage = -1;
                } else {
                    lastHasErro = true;
                    addCleanErro("unexpected token", sToken);
                }
            }
            if (stage != -1) {
                if (!lastHasErro) addCleanErro("unexpected end of token", token.byLastChar());
            }
            if (nameToken != null) {
                this.fullName = library.name + "::" + nameToken.toString();
            } else {
                this.fullName = library.name;
            }
        }
    }

    public boolean isSameProject(NameSpace other) {
        return library == other.library;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (obj == this) return true;
        if (obj instanceof NameSpace) {
            return this.fullName.equals(((NameSpace) obj).fullName);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "namespace : [name][" + fullName + "] ";
    }
}
