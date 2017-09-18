package com.vortex.compiler.logic;

/**
 * @author Rodrigo Neiva Santiago
 *         Data: 17/10/2016
 */
public enum Operator {
    //Invalido
    invalid(-1,""),
    //Assignment
    set(0, "="), setAdd(0, "+="), setSub(0, "-="), setMul(0, "*="), setDiv(0, "/="), setMod(0, "%="),
    setBAnd(0, "&="), setBXor(0, "^="), setBOr(0, "|="), setRShift(0, ">>="), setLShift(0, "<<="),
    //Ternary
    tIF(1, "?"), tElse(1, ":"),
    //Logical
    Or(2, "||"), And(3, "&&"),
    //Binary Logical
    bOr(4, "|"), bXor(5, "^"), bAnd(5, "&"),
    //Equality
    equals(6, "=="), different(6, "!="),
    //Relational
    smaller(7, "<"), bigger(7, ">"), smallerEquals(7, "<="), biggerEquals(7, ">="), is(7, "is"), isnot(7, "isnot"),
    //Shift
    lShift(8, "<<"), rShift(8, ">>"),
    //Additive
    add(9, "+"), sub(9, "-"),
    //Multiplicative
    mul(10, "*"), div(10, "/"), mod(10, "%"),
    //Casting
    cast(11, "", true),
    //Unary
    increment(12, "++", true), decrement(12, "--", true),  Not(12, "!", true), bNot(12, "~", true),
    //Special Unaries (nomes dos operadores nunca sao usados, apenas add e sub (com 1 parametro))
    negative(12, "-", true), positive(12, "+", true);

    public final int priority;
    public final String value;
    public final boolean unary;

    Operator(int priority, String value) {
        this.priority = priority;
        this.value = value;
        unary = false;
    }

    Operator(int priority, String value, boolean unary) {
        this.priority = priority;
        this.value = value;
        this.unary = unary;
    }

    public boolean isIncrement(){
        return this == increment || this == decrement;
    }

    public boolean isLogical(){
        return this == Or || this == And;
    }

    public boolean isInstanceLogical(){
        return this == is || this == isnot;
    }

    public boolean isSet(){
        return this == set || this == setAdd || this == setSub || this == setMul || this == setDiv ||
                this == setMod || this == setBAnd || this == setBXor || this == setBOr || this == setRShift ||
                this == setLShift;
    }

    public Operator getInnerOperator(){
        if(this == set) return set;
        if(this == setAdd) return add;
        if(this == setSub) return sub;
        if(this == setMul) return mul;
        if(this == setDiv) return div;
        if(this == setMod) return mod;
        if(this == setBAnd) return bAnd;
        if(this == setBXor) return bXor;
        if(this == setBOr) return bOr;
        if(this == setRShift) return rShift;
        if(this == setLShift) return lShift;
        return this;
    }

    public static Operator fromToken(CharSequence name){
        if(name == null) return invalid;
        switch (name.toString()){
            case "=" : return set;
            case "+=" : return setAdd;
            case "-=" : return setSub;
            case "*=" : return setMul;
            case "/=" : return setDiv;
            case "%=" : return setMod;
            case "&=" : return setBAnd;
            case "^=" : return setBXor;
            case "|=" : return setBOr;
            case "<<=" : return setLShift;
            case ">>=" : return setRShift;
            case "?" : return tIF;
            case ":" : return tElse;
            case "||" : return Or;
            case "&&" : return And;
            case "|" : return bOr;
            case "^" : return bXor;
            case "&" : return bAnd;
            case "==" : return equals;
            case "!=" : return different;
            case "<" : return smaller;
            case ">" : return bigger;
            case "<=" : return smallerEquals;
            case ">=" : return biggerEquals;
            case "is" : return is;
            case "isnot" : return isnot;
            case "<<" : return lShift;
            case ">>" : return rShift;
            case "+" : return add;
            case "-" : return sub;
            case "*" : return mul;
            case "/" : return div;
            case "%" : return mod;
            case "++" : return increment;
            case "--" : return decrement;
            case "!" : return Not;
            case "~" : return bNot;
        }
        return invalid;
    }

    public boolean isUnary() {
        return unary;
    }

    public boolean isTernary() {
        return this == tIF || this == tElse;
    }
}
