package com.vortex.compiler.content;

import java.util.HashMap;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 02/10/2016
 */
public class Keywords {

    //##-Modificador de acesso
    //public, protected, internal, private

    //##-Modificadores
    //static, abstract, final, volatile

    //##-Typedefs
    //namespace, using, class, enum, interface, struct

    //##-Operadores
    //operator, cast, autocast, is, isnot

    //##-Blocos
    //if, else, switch, default, for, while, do, break, continue, return, lock, native

    //##-Tipo de dados
    //auto, void, this, super, new, delete, stack

    //##-Valores
    //true, false, null

    //##-Debug (Modo release 1 'assert' é removido, Modo release 2 'try', 'catch', 'throw')
    //assert, try, catch, throw

    //##-Pseudo keywords (São keywords em lugares unicos, não entram na lista)
    //get, set, value

    public static final String[] KEYWORDS = new String[]{
            "abstract",
            "assert",
            "auto",
            "autocast",
            "break",
            "case",
            "cast",
            "catch",
            "class",
            "continue",
            "default",
            "delete",
            "do",
            "else",
            "enum",
            "false",
            "final",
            "for",
            "if",
            "interface",
            "internal",
            "is",
            "isnot",
            "lock",
            "namespace",
            "native",
            "new",
            "null",
            "operator",
            "private",
            "protected",
            "public",
            "return",
            "stack",
            "static",
            "struct",
            "super",
            "switch",
            "this",
            "throw",
            "true",
            "try",
            "using",
            "void",
            "volatile",
            "while"
    };

    //Mapa de tradução de keywords
    public static final HashMap<String, String> KEYMAP = new HashMap<>();
    static {
        for (String keyword : KEYWORDS) {
            KEYMAP.put(keyword, keyword);
        }
    }
}
