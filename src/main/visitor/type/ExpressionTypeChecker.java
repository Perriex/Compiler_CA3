package main.visitor.type;

import main.ast.nodes.expression.*;
import main.ast.nodes.expression.operators.BinaryOperator;
import main.ast.nodes.expression.operators.UnaryOperator;
import main.ast.nodes.expression.values.primitive.BoolValue;
import main.ast.nodes.expression.values.primitive.IntValue;
import main.ast.types.*;
import main.ast.types.primitives.BoolType;
import main.ast.types.primitives.IntType;
import main.ast.types.primitives.VoidType;
import main.compileError.typeError.*;
import main.symbolTable.SymbolTable;
import main.symbolTable.exceptions.ItemNotFoundException;
import main.symbolTable.items.StructSymbolTableItem;
import main.symbolTable.items.SymbolTableItem;
import main.symbolTable.items.VariableSymbolTableItem;
import main.visitor.Visitor;

import java.rmi.NoSuchObjectException;

public class ExpressionTypeChecker extends Visitor<Type> {

    private Type checkIntTypeEx(BinaryExpression binaryExpression,Type type, BinaryOperator operator)
    {
        if(type instanceof IntType)
        {
            if ( operator != BinaryOperator.or && operator != BinaryOperator.and)
            {
                if(operator == BinaryOperator.assign)
                    return new VoidType();
                if(operator == BinaryOperator.sub || operator == BinaryOperator.div ||
                    operator == BinaryOperator.mult || operator == BinaryOperator.add)
                    return new IntType();
                return new BoolType();
            }
        }
        if(!(type instanceof NoType))
        {
            binaryExpression.addError(new UnsupportedOperandType(binaryExpression.getLine(),operator.name()));
        }
        return new NoType();
    }
    private Type checkBoolTypeEx(BinaryExpression binaryExpression,Type type, BinaryOperator operator)
    {
        if(type instanceof BoolType)
        {
            if ( operator == BinaryOperator.or || operator == BinaryOperator.and || operator == BinaryOperator.eq)
            {
                return new BoolType();
            }
            if (operator == BinaryOperator.assign)
            {
                return new VoidType();
            }
        }
        if(!(type instanceof NoType))
        {
            binaryExpression.addError(new UnsupportedOperandType(binaryExpression.getLine(),operator.name()));
        }
        return new NoType();
    }

    @Override
    public Type visit(BinaryExpression binaryExpression) {
        Type lType = binaryExpression.getFirstOperand().accept(this);
        Type rType = binaryExpression.getSecondOperand().accept(this);
        BinaryOperator operator = binaryExpression.getBinaryOperator();
        if (lType instanceof IntType)
        {
            return checkIntTypeEx(binaryExpression,rType, operator);
        }
        else if (rType instanceof IntType)
        {
            return checkIntTypeEx(binaryExpression,lType, operator);
        }
        else if (lType instanceof BoolType)
        {
            return checkBoolTypeEx(binaryExpression,rType, operator);
        }
        else if (rType instanceof BoolType)
        {
            return checkBoolTypeEx(binaryExpression, lType, operator);
        }
        else
        {
            if(!(rType instanceof NoType && lType instanceof NoType)) {
                if (operator == BinaryOperator.eq) {
                    if ((rType instanceof FptrType && lType instanceof FptrType) ||
                        (rType instanceof StructType && lType instanceof StructType)) {
                        return new BoolType();
                    }
                    else
                    {
                        binaryExpression.addError(new UnsupportedOperandType(binaryExpression.getLine(),operator.name()));
                    }
                } else if (operator == BinaryOperator.assign) {
                    if ((rType instanceof FptrType && lType instanceof FptrType) ||
                       (rType instanceof StructType && lType instanceof StructType) ||
                       (rType instanceof ListType && lType instanceof ListType)) {
                        return new VoidType();
                    }
                    else
                    {
                        binaryExpression.addError(new UnsupportedOperandType(binaryExpression.getLine(),operator.name()));
                    }
                }
            }
        }
        return new NoType();
    }

    @Override
    public Type visit(UnaryExpression unaryExpression) {
        Type exType =  unaryExpression.getOperand().accept(this);
        if (unaryExpression.getOperator() == UnaryOperator.not)
        {
            if (exType instanceof BoolType)
            {
                return new BoolType();
            }
            else if(exType instanceof IntType)
            {
                unaryExpression.addError(new UnsupportedOperandType(unaryExpression.getLine(), "not"));
            }
        }
        else
        {
            if(exType instanceof IntType)
            {
                return new IntType();
            }
            else if(exType instanceof BoolType)
            {
                unaryExpression.addError(new UnsupportedOperandType(unaryExpression.getLine(), "minus"));
            }
        }
        return new NoType();
    }

    @Override
    public Type visit(FunctionCall funcCall) {
        //Todo
        return null;
    }

    @Override
    public Type visit(Identifier identifier) {

        return null;
    }

    @Override
    public Type visit(ListAccessByIndex listAccessByIndex) {
        Type instType = listAccessByIndex.getInstance().accept(this);
        Type indexType = listAccessByIndex.getIndex().accept(this);
        if (instType instanceof ListType && indexType instanceof IntType)
        {
            return ((ListType) instType).getType();
        }
        else if (instType instanceof ListType && !(indexType instanceof NoType))
        {
            listAccessByIndex.addError(new ListIndexNotInt(listAccessByIndex.getLine()));
        }
        else if (!(instType instanceof NoType) && indexType instanceof IntType)
        {
           listAccessByIndex.addError(new AccessByIndexOnNonList(listAccessByIndex.getLine()));
        }
        return new NoType();
    }

    @Override
    public Type visit(StructAccess structAccess) {
        Type instType = structAccess.getInstance().accept(this);
        if (!(instType instanceof StructType))
        {
            structAccess.addError(new AccessOnNonStruct(structAccess.getLine()));
            return new NoType();
        }
        String varName = structAccess.getElement().getName();
        String structName = ((StructType) instType).getStructName().getName();
        try
        {
            StructSymbolTableItem struct = (StructSymbolTableItem) SymbolTable.top.getItem(structName);
            SymbolTable structTable = struct.getStructSymbolTable();
            try
            {
                VariableSymbolTableItem element = (VariableSymbolTableItem) structTable.getItem(varName);
                return element.getType();
            }
            catch (ItemNotFoundException ex)
            {
                structAccess.addError(new StructMemberNotFound(structAccess.getLine(),structName,varName));
                return new NoType();
            }
        }
        catch (ItemNotFoundException ex)
        {
            return new NoType();
        }
    }

    @Override
    public Type visit(ListSize listSize) {
        //Todo
        return null;
    }

    @Override
    public Type visit(ListAppend listAppend) {
        Type listType = listAppend.getListArg().accept(this);
        if(!(listType instanceof ListType))
        {
            listAppend.addError(new AppendToNonList(listAppend.getLine()));
            return new NoType();
        }
        Type listEl = listAppend.getElementArg().accept(this);
        Type listEls = ((ListType) listType).getType();
        if((listEl instanceof BoolType && listEls instanceof BoolType ) ||
         (listEl instanceof IntType && listEls instanceof IntType ) ||
         (listEl instanceof StructType && listEls instanceof StructType ) ||
         (listEl instanceof ListType && listEls instanceof ListType ) )
        {
            return new VoidType();
        }
        if(!(listEls instanceof NoType))
        {
            listAppend.addError(new NewElementTypeNotMatchListType(listAppend.getLine()));
        }
        return new NoType();
    }

    @Override
    public Type visit(ExprInPar exprInPar) {
        return exprInPar.getInputs().get(0).accept(this);
    }

    @Override
    public Type visit(IntValue intValue) {
        return new IntType();
    }

    @Override
    public Type visit(BoolValue boolValue) {
        return new BoolType();
    }
}
