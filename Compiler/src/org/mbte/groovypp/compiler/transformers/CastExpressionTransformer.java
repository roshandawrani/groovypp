/*
 * Copyright 2009-2011 MBTE Sweden AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mbte.groovypp.compiler.transformers;

import groovy.lang.TypePolicy;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.mbte.groovypp.compiler.*;
import org.mbte.groovypp.compiler.bytecode.BytecodeExpr;
import org.mbte.groovypp.compiler.bytecode.InnerThisBytecodeExpr;
import org.mbte.groovypp.compiler.bytecode.PropertyUtil;
import org.mbte.groovypp.compiler.bytecode.ResolvedMethodBytecodeExpr;
import org.mbte.groovypp.compiler.flow.MapWithListExpression;
import org.mbte.groovypp.compiler.flow.MultiPropertySetExpression;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.*;

/**
 * Cast processing rules:
 * a) as operator always go via asType method
 * b) both numerical types always goes via primitive types
 * c) if we cast statically to lower type we keep upper (except if upper NullType)
 * d) otherwise we do direct cast
 * e) primitive types goes via boxing and Number
 */
public class CastExpressionTransformer extends ExprTransformer<CastExpression> {
    public BytecodeExpr transform(CastExpression cast, CompilerTransformer compiler) {

        if (cast.getExpression() instanceof ConstantExpression) {
            ConstantExpression constantExpression = (ConstantExpression) cast.getExpression();
            if(constantExpression.getValue() instanceof String) {
                String s = (String) constantExpression.getValue();
                if(s.length() == 1) {
                    if(cast.getType().equals(ClassHelper.int_TYPE) || cast.getType().equals(ClassHelper.Integer_TYPE)) {
                        return new ConstantExpressionTransformer.Constant(constantExpression, ClassHelper.int_TYPE, (int)s.charAt(0));
                    }
                    if(cast.getType().equals(ClassHelper.char_TYPE)) {
                        return new ConstantExpressionTransformer.Constant(constantExpression, ClassHelper.char_TYPE, (int)s.charAt(0));
                    }
                }
            }
        }

        if(cast.getExpression() instanceof ConstantExpressionTransformer.Constant) {
            ConstantExpressionTransformer.Constant constant = (ConstantExpressionTransformer.Constant) cast.getExpression();
            if(cast.getType().equals(constant.getType())) {
                return constant;
            }
        }

        if(cast.getExpression() instanceof MultiPropertySetExpression) {
            final MultiPropertySetExpression multiPropertySetExpression = (MultiPropertySetExpression) cast.getExpression();
            final Expression tobj = compiler.transform(multiPropertySetExpression.getObject());
            if(TypeUtil.isAssignableFrom(cast.getType(), tobj.getType())) {
                final MultiPropertySetExpression exp = new MultiPropertySetExpression( tobj, multiPropertySetExpression.getProperties());
                exp.setSourcePosition(multiPropertySetExpression);
                return (BytecodeExpr) compiler.transform(exp);
            }
            else {
                final MultiPropertySetExpression exp = new MultiPropertySetExpression( compiler.cast(multiPropertySetExpression.getObject(), cast.getType()), multiPropertySetExpression.getProperties());
                exp.setSourcePosition(multiPropertySetExpression);
                return (BytecodeExpr) compiler.transform(exp);
            }
        }

        if (cast.getExpression() instanceof TernaryExpression) {
            return compiler.cast(cast.getExpression(), cast.getType());
        }

        if (cast.getExpression() instanceof ClassExpression && !cast.getType().equals(ClassHelper.CLASS_Type) && !cast.getType().equals(ClassHelper.OBJECT_TYPE)) {
            ClassExpression exp = (ClassExpression) cast.getExpression();
            ConstructorCallExpression newCall = new ConstructorCallExpression(exp.getType(), new ArgumentListExpression());
            newCall.setSourcePosition(exp);
            return compiler.cast(newCall, cast.getType());
        }

        if (cast.getExpression() instanceof ListExpressionTransformer.Untransformed) {
            final CastExpression newExp = new CastExpression(cast.getType(), ((ListExpressionTransformer.Untransformed) cast.getExpression()).exp);
            newExp.setSourcePosition(cast);
            cast = newExp;
        }

        if (cast.getExpression() instanceof MapExpressionTransformer.Untransformed) {
            final CastExpression newExp = new CastExpression(cast.getType(), ((MapExpressionTransformer.Untransformed) cast.getExpression()).exp);
            newExp.setSourcePosition(cast);
            cast = newExp;
        }

        if (cast.getExpression() instanceof MapWithListExpressionTransformer.Untransformed) {
            final CastExpression newExp = new CastExpression(cast.getType(), ((MapWithListExpressionTransformer.Untransformed) cast.getExpression()).exp);
            newExp.setSourcePosition(cast);
            cast = newExp;
        }

        if (cast.getExpression() instanceof MultiPropertySetExpressionTransformer.Untransformed) {
            final MultiPropertySetExpressionTransformer.Untransformed untransformed = (MultiPropertySetExpressionTransformer.Untransformed) cast.getExpression();
            final CastExpression obj = new CastExpression(cast.getType(), untransformed.getObject());
            obj.setSourcePosition(untransformed.getObject());
            final MultiPropertySetExpression newExp = new MultiPropertySetExpression(obj, untransformed.getExp().getProperties());
            newExp.setSourcePosition(untransformed.getExp());
            return (BytecodeExpr) compiler.transformToGround(newExp);
        }

        if (cast.getExpression() instanceof TernaryExpressionTransformer.Untransformed) {
            final TernaryExpression original = ((TernaryExpressionTransformer.Untransformed) cast.getExpression()).exp;

            if (original instanceof ElvisOperatorExpression) {
                final CastExpression newTrue = new CastExpression(cast.getType(), original.getTrueExpression());
                newTrue.setSourcePosition(original.getTrueExpression());

                final CastExpression newFalse = new CastExpression(cast.getType(), original.getFalseExpression());
                newFalse.setSourcePosition(original.getFalseExpression());

                ElvisOperatorExpression newTernary = new ElvisOperatorExpression(newTrue, newFalse);
                newTernary.setSourcePosition(original);

                return (BytecodeExpr) compiler.transformToGround(newTernary);
            }
            else {
                final CastExpression newTrue = new CastExpression(cast.getType(), original.getTrueExpression());
                newTrue.setSourcePosition(original.getTrueExpression());

                final CastExpression newFalse = new CastExpression(cast.getType(), original.getFalseExpression());
                newFalse.setSourcePosition(original.getFalseExpression());

                TernaryExpression newTernary = new TernaryExpression(original.getBooleanExpression(), newTrue, newFalse);
                newTernary.setSourcePosition(original);

                return (BytecodeExpr) compiler.transformToGround(newTernary);
            }
        }

        if (cast.getType().equals(ClassHelper.boolean_TYPE) || cast.getType().equals(ClassHelper.Boolean_TYPE)) {
            if (cast.getExpression() instanceof ListExpression) {
                return compiler.castToBoolean( new ListExpressionTransformer.TransformedListExpr( (ListExpression)cast.getExpression(), TypeUtil.ARRAY_LIST_TYPE, compiler, true), cast.getType());
            }
            if (cast.getExpression() instanceof MapExpression) {
                return compiler.castToBoolean( new MapExpressionTransformer.TransformedMapExpr( (MapExpression)cast.getExpression(), TypeUtil.LINKED_HASH_MAP_TYPE, compiler), cast.getType());
            }
            return compiler.castToBoolean((BytecodeExpr)compiler.transform(cast.getExpression()), cast.getType());
        }

        if (cast.getExpression() instanceof ListExpression) {
            ListExpression listExpression = (ListExpression) cast.getExpression();

            if (cast.getType().isArray()) {
                ClassNode componentType = cast.getType().getComponentType();
                improveListTypes(listExpression, componentType);
                final ArrayExpression array = new ArrayExpression(componentType, listExpression.getExpressions(), null);
                array.setSourcePosition(listExpression);
                return (BytecodeExpr) compiler.transform(array);
            }

            if(cast.getType().implementsInterface(TypeUtil.ITERABLE) || cast.getType().equals(TypeUtil.ITERABLE)) {
                if(compiler.findConstructor(cast.getType(), ClassNode.EMPTY_ARRAY, null) != null){
                    ClassNode componentType = compiler.getCollectionType(cast.getType());
                    improveListTypes(listExpression, componentType);
                    final List<Expression> list = listExpression.getExpressions();
                    for (int i = 0; i != list.size(); ++i) {
                        list.set(i, compiler.transform(list.get(i)));
                    }

                    ClassNode collType = calcResultCollectionType(cast, componentType, compiler);
                    return new ListExpressionTransformer.TransformedListExpr(listExpression, collType, compiler, false);
                }
            }

            if (!TypeUtil.isDirectlyAssignableFrom(cast.getType(), TypeUtil.ARRAY_LIST_TYPE)) {
                final ArgumentListExpression args = new ArgumentListExpression(listExpression.getExpressions());
                if (cast.getType().redirect() instanceof InnerClassNode && (cast.getType().getModifiers() & ACC_STATIC) == 0) {
                    args.getExpressions().add(0, VariableExpression.THIS_EXPRESSION);
                }
                final ConstructorCallExpression constr = new ConstructorCallExpression(cast.getType(), args);
                constr.setSourcePosition(cast);
                return (BytecodeExpr) compiler.transform(constr);
            }
            else {
                // Assignable from ArrayList but not Iterable
                ClassNode componentType = ClassHelper.OBJECT_TYPE;
                improveListTypes(listExpression, componentType);
                final List<Expression> list = listExpression.getExpressions();
                for (int i = 0; i != list.size(); ++i) {
                    list.set(i, compiler.transform(list.get(i)));
                }

                return new ListExpressionTransformer.TransformedListExpr(listExpression, TypeUtil.ARRAY_LIST_TYPE,
                        compiler, true);
            }
        }

        if (cast.getExpression() instanceof MapExpression) {
            MapExpression mapExpression = (MapExpression) cast.getExpression();
            ClassNode castType = cast.getType();
            if (castType.equals(TypeUtil.FHASHMAP_TYPE)) {
                ClassNode keyType = compiler.getMapKeyType(castType);
                ClassNode valueType = compiler.getMapValueType(castType);
                improveMapTypes(mapExpression, keyType, valueType);

                ClassNode collType = ClassHelper.make (TypeUtil.FHASHMAP_TYPE.getName());
                collType.setRedirect(castType.redirect());
                collType.setGenericsTypes(new GenericsType[]{new GenericsType(keyType), new GenericsType(valueType)});

                return new MapExpressionTransformer.TransformedFMapExpr(mapExpression, collType, compiler);
            }

            if (cast.getType().implementsInterface(ClassHelper.MAP_TYPE)) {
                if(compiler.findConstructor(cast.getType(), ClassNode.EMPTY_ARRAY, null) != null){
                    ClassNode keyType = compiler.getMapKeyType(cast.getType());
                    ClassNode valueType = compiler.getMapValueType(cast.getType());
                    improveMapTypes(mapExpression, keyType, valueType);

                    ClassNode mapType = calcResultMapType(cast, keyType, valueType, compiler);
                    return new MapExpressionTransformer.TransformedMapExpr(mapExpression, mapType, compiler);
                }
            }

            boolean isMap = cast.getType().implementsInterface(ClassHelper.MAP_TYPE) || cast.getType().equals(ClassHelper.MAP_TYPE);
            if (!isMap && !TypeUtil.isAssignableFrom(cast.getType(), TypeUtil.LINKED_HASH_MAP_TYPE)) {
                return buildClassFromMap (mapExpression, cast.getType(), compiler);
            }
            else {
                ClassNode mapType = TypeUtil.LINKED_HASH_MAP_TYPE;
                if (isMap) {
                    final GenericsType[] generics = TypeUtil.getSubstitutedType(ClassHelper.MAP_TYPE, ClassHelper.MAP_TYPE, cast.getType()).getGenericsTypes();
                    ClassNode keyType = ClassHelper.OBJECT_TYPE;
                    ClassNode valueType = ClassHelper.OBJECT_TYPE;
                    if (generics != null) {
                        keyType = compiler.getCollOrMapGenericType(generics[0].getType());
                        valueType = compiler.getCollOrMapGenericType(generics[1].getType());

                        improveMapTypes(mapExpression, keyType, valueType);
                    }

                    mapType = calcResultMapType(cast, keyType, valueType, compiler);
                }

                final MapExpressionTransformer.TransformedMapExpr inner = new MapExpressionTransformer.TransformedMapExpr((MapExpression) cast.getExpression(), mapType, compiler);
                return standardCast(cast, compiler, inner);
            }
        }

        BytecodeExpr expr = (BytecodeExpr) compiler.transform(cast.getExpression());

        if (expr.getType().implementsInterface(TypeUtil.TCLOSURE)) {
            List<MethodNode> one = ClosureUtil.isOneMethodAbstract(cast.getType());
            MethodNode doCall = ClosureUtil.isMatch(one, (ClosureClassNode) expr.getType(), cast.getType(), compiler);
            if (doCall != null) {
                return expr;
            }
            else {
                if(cast.getType().equals(ClassHelper.CLOSURE_TYPE)) {
                    compiler.processPendingClosure((CompiledClosureBytecodeExpr) expr);
                    return expr;
                }
            }
        }

        if (cast.getType().equals(ClassHelper.STRING_TYPE)) {
            if (cast.getExpression() instanceof ListExpression) {
                return compiler.castToString( new ListExpressionTransformer.TransformedListExpr( (ListExpression)cast.getExpression(), TypeUtil.ARRAY_LIST_TYPE, compiler, true));
            }
            return compiler.castToString(expr);
        }

        if (expr.getType().implementsInterface(TypeUtil.TTHIS)) {
            ClassNode castType = cast.getType();
            final ClassNode exprType = expr.getType().getOuterClass();
            if (TypeUtil.isDirectlyAssignableFrom(castType, exprType)) return expr;
            ClassNode outer = exprType.getOuterClass();
            while(!TypeUtil.isDirectlyAssignableFrom(castType, outer)) {
                outer = outer.getOuterClass();
            }
            return new InnerThisBytecodeExpr(expr, outer, compiler, exprType);
        }

        if (!TypeUtil.isDirectlyAssignableFrom(cast.getType(), expr.getType())) {
            MethodNode unboxing = TypeUtil.getReferenceUnboxingMethod(expr.getType());
            if (unboxing != null) {
                BytecodeExpr mce = ResolvedMethodBytecodeExpr.create(cast, unboxing, expr,
                        new ArgumentListExpression(), compiler);
                if (TypeUtil.isDirectlyAssignableFrom(ClassHelper.getUnwrapper(cast.getType()),
                                                      ClassHelper.getUnwrapper(mce.getType())))
                    return mce;
            }
        }

        return standardCast(cast, compiler, expr);
    }

    private BytecodeExpr buildClassFromMap(MapExpression exp, ClassNode type, final CompilerTransformer compiler) {

        final List<MapEntryExpression> list = exp.getMapEntryExpressions();

        Expression superArgs = exp instanceof MapWithListExpression ? ((MapWithListExpression)exp).listExpression : null;

        for (int i = 0; i != list.size(); ++i) {
            final MapEntryExpression me = list.get(i);

            Expression key = me.getKeyExpression();
            if (!(key instanceof ConstantExpression) || !(((ConstantExpression)key).getValue() instanceof String)) {
                compiler.addError( "<key> must have java.lang.String type", key);
                return null;
            }
        }

        ClassNode objType = null;

        if ((type.getModifiers() & ACC_ABSTRACT) != 0 || type.isInterface()) {
            objType = createNewType(type, exp, compiler);
        }

        List<MapEntryExpression> methods = new LinkedList<MapEntryExpression>();
        final List<MapEntryExpression> fields = new LinkedList<MapEntryExpression>();
        final List<MapEntryExpression> props = new LinkedList<MapEntryExpression>();

        for (int i = 0; i != list.size(); ++i) {
            final MapEntryExpression me = list.get(i);

            String keyName = (String) ((ConstantExpression)me.getKeyExpression()).getValue();
            Expression value = me.getValueExpression();

            if (keyName.equals("super")) {
                if(superArgs != null) {
                    compiler.addError( "<super> conflicts with already provided constructor arguments", me.getKeyExpression());
                    return null;
                }

                if (objType == null)
                    objType = createNewType(type, exp, compiler);
                superArgs = value;
                continue;
            }

            final Object prop = PropertyUtil.resolveSetProperty(type, keyName, TypeUtil.NULL_TYPE, compiler, true);
            if (prop != null) {
                ClassNode propType;
                ClassNode propDeclClass;
                if (prop instanceof MethodNode) {
                    propType = ((MethodNode)prop).getParameters()[0].getType();
                    propDeclClass = ((MethodNode)prop).getDeclaringClass();
                }
                else
                    if (prop instanceof FieldNode) {
                        propType = ((FieldNode)prop).getType();
                        propDeclClass = ((FieldNode)prop).getDeclaringClass();
                    }
                    else {
                        propDeclClass = ((PropertyNode)prop).getDeclaringClass();
                        propType = ((PropertyNode)prop).getType();
                    }

                propType = TypeUtil.getSubstitutedType(propType, propDeclClass, type);

                final CastExpression cast = new CastExpression(propType, value);
                cast.setSourcePosition(value);
                final BytecodeExpr obj = new BytecodeExpr(type, type) {
                    protected void compile(MethodVisitor mv) {
                        mv.visitInsn(DUP);
                    }
                };
                final BytecodeExpr setter = PropertyUtil.createSetProperty(me, compiler, keyName, obj, (BytecodeExpr) compiler.transform(cast), prop);
                props.add(new MapEntryExpression(me.getKeyExpression(), setter));
            }
            else {
                if (objType == null)
                    objType = createNewType(type, exp, compiler);

                if (value instanceof ClosureExpression) {
                    ClosureExpression ce = (ClosureExpression) value;

                    methods.add (me);

                    ClosureUtil.addFields(ce, objType, compiler);
                }
                else {
                    fields.add(me);
                }
            }
        }

        if (superArgs != null) {
            if (superArgs instanceof ListExpression) {
                superArgs = new ArgumentListExpression(((ListExpression)superArgs).getExpressions());
            }
            else
                superArgs = new ArgumentListExpression(superArgs);
        }
        else
            superArgs = new ArgumentListExpression();

        if (objType != null) {
            final Expression finalSA = compiler.transform(superArgs);
            final MethodNode constructor = ConstructorCallExpressionTransformer.findConstructorWithClosureCoercion(objType.getSuperClass(), compiler.exprToTypeArray(finalSA), compiler, objType);

            if (constructor == null) {
                compiler.addError ("Cannot find super constructor " + objType.getSuperClass().getName(), exp);
                return null;
            }

            final List<Expression> ll = ((ArgumentListExpression) superArgs).getExpressions();
            final Parameter[] parameters = constructor.getParameters();
            if (parameters.length > 0 && parameters[parameters.length-1].getType().isArray()) {
                for (int i = 0; i != parameters.length-1; ++i)
                    ll.set(i, compiler.cast(ll.get(i), parameters[i].getType()));

                final ClassNode last = parameters[parameters.length - 1].getType().getComponentType();
                for (int i = parameters.length-1; i != ll.size(); ++i)
                    ll.set(i, compiler.cast(ll.get(i), last));
            }
            else {
                for (int i = 0; i != ll.size(); ++i)
                    ll.set(i, compiler.cast(ll.get(i), parameters[i].getType()));
            }

            for (MapEntryExpression me : fields) {
                final String keyName = (String) ((ConstantExpression) me.getKeyExpression()).getValue();

                final Expression init = compiler.transform(me.getValueExpression());

                me.setValueExpression(init);

                FieldNode fieldNode = objType.addField(keyName, 0, init.getType(), null);
                fieldNode.addAnnotation(new AnnotationNode(TypeUtil.NO_EXTERNAL_INITIALIZATION));
            }

            for (MapEntryExpression me : methods) {
                final String keyName = (String) ((ConstantExpression) me.getKeyExpression()).getValue();
                StaticCompiler.closureToMethod(type, compiler, objType, keyName, (ClosureExpression) me.getValueExpression());
            }

            return new BytecodeExpr(exp, objType) {
                protected void compile(MethodVisitor mv) {
                    ClassNode type = getType();
                    if (compiler.policy == TypePolicy.STATIC && !compiler.context.isOuterClassInstanceUsed(type) &&
                            type.getDeclaredField("this$0") != null /* todo: remove this check */) {
                        type.removeField("this$0");
                    }

                    final Parameter[] constrParams = ClosureUtil.createClosureConstructorParams(type, compiler);
                    ClosureUtil.createClosureConstructor(type, constrParams, finalSA, compiler);
                    ClosureUtil.instantiateClass(type, compiler, constrParams, finalSA, mv);

                    for (MapEntryExpression me : fields) {
                        final String keyName = (String) ((ConstantExpression) me.getKeyExpression()).getValue();

                        final FieldNode fieldNode = type.getDeclaredField(keyName);

                        mv.visitInsn(DUP);
                        ((BytecodeExpr)me.getValueExpression()).visit(mv);
                        mv.visitFieldInsn(PUTFIELD, BytecodeHelper.getClassInternalName(type), fieldNode.getName(), BytecodeHelper.getTypeDescription(fieldNode.getType()));
                    }

                    for (MapEntryExpression me : props) {
                        ((BytecodeExpr)me.getValueExpression()).visit(mv);
                        mv.visitInsn(POP);
                    }
                }
            };
        }
        else {
            final ConstructorCallExpression constr = new ConstructorCallExpression(type, superArgs);
            constr.setSourcePosition(exp);
            final BytecodeExpr transformendConstr = (BytecodeExpr) compiler.transform(constr);
            return new BytecodeExpr(exp, type) {
                protected void compile(MethodVisitor mv) {
                    transformendConstr.visit(mv);
                    for (MapEntryExpression me : props) {
                        ((BytecodeExpr)me.getValueExpression()).visit(mv);
                        mv.visitInsn(POP);
                    }
                }
            };
        }
    }

    private ClassNode createNewType(ClassNode type, Expression exp, CompilerTransformer compiler) {
        ClassNode objType;
        if ((type.getModifiers() & Opcodes.ACC_FINAL) != 0) {
            compiler.addError("You are not allowed to overwrite the final class '" + type.getName() + "'", exp);
            return null;
        }

        objType = new InnerClassNode(compiler.classNode, compiler.getNextClosureName(), ACC_PUBLIC|ACC_FINAL|ACC_SYNTHETIC, ClassHelper.OBJECT_TYPE);
        if (type.isInterface()) {
            objType.setInterfaces(new ClassNode [] {type} );
        }
        else {
            objType.setSuperClass(type);
        }
        objType.setModule(compiler.classNode.getModule());

        if (!compiler.methodNode.isStatic() || compiler.classNode.getName().endsWith("$TraitImpl"))
            objType.addField("this$0", ACC_PUBLIC|ACC_FINAL|ACC_SYNTHETIC, !compiler.methodNode.isStatic() ? compiler.classNode : compiler.methodNode.getParameters()[0].getType(), null);

        if(compiler.policy == TypePolicy.STATIC)
            CleaningVerifier.improveVerifier(objType);

        return objType;
    }

    private ClassNode calcResultMapType(CastExpression exp, ClassNode keyType, ClassNode valueType, CompilerTransformer compiler) {
        ClassNode collType = exp.getType();
        if ((collType.getModifiers() & ACC_ABSTRACT) != 0) {
            if (collType.equals(ClassHelper.MAP_TYPE)) {
                if (collType.getGenericsTypes() != null) {
                    collType = ClassHelper.make ("java.util.LinkedHashMap");
                    collType.setRedirect(TypeUtil.LINKED_HASH_MAP_TYPE);
                    collType.setGenericsTypes(new GenericsType[]{new GenericsType(keyType), new GenericsType(valueType)});
                }
                else
                    collType = TypeUtil.LINKED_HASH_MAP_TYPE;
            }
            else {
                if (collType.equals(TypeUtil.SORTED_MAP_TYPE)) {
                    if (collType.getGenericsTypes() != null) {
                        collType = ClassHelper.make ("java.util.TreeMap");
                        collType.setRedirect(TypeUtil.LINKED_HASH_SET_TYPE);
                        collType.setGenericsTypes(new GenericsType[]{new GenericsType(keyType), new GenericsType(valueType)});
                    }
                    else
                        collType = TypeUtil.TREE_MAP_TYPE;
                }
                else {
                        compiler.addError ("Cannot instantiate map as instance of abstract type " + collType.getName(), exp);
                        return null;
                    }
            }
        }
        return collType;
    }

    private ClassNode calcResultCollectionType(CastExpression exp, ClassNode componentType, CompilerTransformer compiler) {
        ClassNode collType = exp.getType();
        if ((collType.getModifiers() & ACC_ABSTRACT) != 0) {
            if (collType.equals(ClassHelper.LIST_TYPE) || collType.equals(TypeUtil.COLLECTION_TYPE) || collType.equals(TypeUtil.ITERABLE)) {
                if (collType.getGenericsTypes() != null) {
                    collType = ClassHelper.make ("java.util.ArrayList");
                    collType.setRedirect(TypeUtil.ARRAY_LIST_TYPE);
                    collType.setGenericsTypes(new GenericsType[]{new GenericsType(componentType)});
                }
                else
                    collType = TypeUtil.ARRAY_LIST_TYPE;
            }
            else {
                if (collType.equals(TypeUtil.SET_TYPE)) {
                    if (collType.getGenericsTypes() != null) {
                        collType = ClassHelper.make ("java.util.LinkedHashSet");
                        collType.setRedirect(TypeUtil.LINKED_HASH_SET_TYPE);
                        collType.setGenericsTypes(new GenericsType[]{new GenericsType(componentType)});
                    }
                    else
                        collType = TypeUtil.LINKED_HASH_SET_TYPE;
                }
                else {
                    if (collType.equals(TypeUtil.SORTED_SET_TYPE)) {
                        if (collType.getGenericsTypes() != null) {
                            collType = ClassHelper.make ("java.util.TreeSet");
                            collType.setRedirect(TypeUtil.TREE_SET_TYPE);
                            collType.setGenericsTypes(new GenericsType[]{new GenericsType(componentType)});
                        }
                        else
                            collType = TypeUtil.TREE_SET_TYPE;
                    }
                    else {
                        if (collType.equals(TypeUtil.QUEUE_TYPE)) {
                            if (collType.getGenericsTypes() != null) {
                                collType = ClassHelper.make ("java.util.LinkedList");
                                collType.setRedirect(TypeUtil.LINKED_LIST_TYPE);
                                collType.setGenericsTypes(new GenericsType[]{new GenericsType(componentType)});
                            }
                            else
                                collType = TypeUtil.LINKED_LIST_TYPE;
                        }
                        else {
                            compiler.addError ("Cannot instantiate list as instance of abstract type " + collType.getName(), exp);
                            return null;
                        }
                    }
                }
            }
        }
        return collType;
    }

    private BytecodeExpr standardCast(final CastExpression exp, CompilerTransformer compiler, final BytecodeExpr expr) {
        if (exp.isCoerce()) {
            // a)
            final ClassNode type = TypeUtil.wrapSafely(exp.getType());
            Expression arg = ClassExpressionTransformer.newExpr(exp, type);
            return new AsType(exp, type, expr, (BytecodeExpr) arg);
        } else {
            if (TypeUtil.isNumericalType(exp.getType()) && TypeUtil.isNumericalType(expr.getType())) {
                // b)
                return new Cast(exp.getType(), expr);
            } else {
                ClassNode rtype = TypeUtil.wrapSafely(expr.getType());
                if (rtype.equals(TypeUtil.NULL_TYPE) && ClassHelper.isPrimitiveType(exp.getType())) {
                    return new BytecodeExpr(exp, exp.getType()) {
                        protected void compile(MethodVisitor mv) {
                            if (exp.getType() == ClassHelper.double_TYPE) {
                                mv.visitInsn(DCONST_0);
                            } else if (exp.getType() == ClassHelper.float_TYPE) {
                                mv.visitInsn(FCONST_0);
                            } else if (exp.getType() == ClassHelper.long_TYPE) {
                                mv.visitInsn(LCONST_0);
                            } else
                                mv.visitInsn(ICONST_0);
                            }
                    };
                }

                if (TypeUtil.isDirectlyAssignableFrom(exp.getType(), rtype)) {
                    // c)
                    final ClassNode castType = exp.getType();
                    if (castType.getGenericsTypes() == null && castType.redirect().getGenericsTypes() != null) {
                        // Correect type arguments.
                        final ClassNode mapped = TypeUtil.mapTypeFromSuper(castType.redirect(), castType.redirect(), rtype);
                        if (mapped != null) {
                            exp.setType(mapped);
                        }
                    }
                    if (rtype.equals(castType)) {
                        expr.setType(castType); // important for correct generic signature
                        return expr;
                    }
                    else {
                        return new BytecodeExpr(expr, exp.getType()) {
                            protected void compile(MethodVisitor mv) {
                                expr.visit(mv);
                                box(expr.getType(), mv);
                            }
                        };
                    }
                } else {
                    // d
                    if (!TypeUtil.isConvertibleFrom(exp.getType(), rtype)) {
                        compiler.addError("Cannot convert " + PresentationUtil.getText(rtype) +
                                " to " + PresentationUtil.getText(exp.getType()), exp);
                        return null;
                    }
                    return new Cast(exp.getType(), expr);
                }
            }
        }
    }

    private void improveListTypes(ListExpression listExpression, ClassNode componentType) {
        List<Expression> list = listExpression.getExpressions();
        int count = list.size();
        for (int i = 0; i != count; ++i) {
            Expression el = list.get(i);
            CastExpression castExpression = new CastExpression(componentType, el);
            castExpression.setSourcePosition(el);
            list.set(i, castExpression);
        }
    }

    private void improveMapTypes(MapExpression mapExpression, ClassNode keyType, ClassNode valueType) {
        List<MapEntryExpression> list = mapExpression.getMapEntryExpressions();
        int count = list.size();
        for (int i = 0; i != count; ++i) {
            MapEntryExpression el = list.get(i);
            CastExpression castExpression;
            castExpression = new CastExpression(keyType, el.getKeyExpression());
            castExpression.setSourcePosition(el.getKeyExpression());
            el.setKeyExpression(castExpression);
            castExpression = new CastExpression(valueType, el.getValueExpression());
            castExpression.setSourcePosition(el.getValueExpression());
            el.setValueExpression(castExpression);
        }
    }

    private static class AsType extends BytecodeExpr {
        private final BytecodeExpr expr;
        private final BytecodeExpr arg1;

        public AsType(CastExpression exp, ClassNode type, BytecodeExpr expr, BytecodeExpr arg1) {
            super(exp, type);
            this.expr = expr;
            this.arg1 = arg1;
        }

        protected void compile(MethodVisitor mv) {
            expr.visit(mv);
            box(expr.getType(), mv);
            arg1.visit(mv);
            mv.visitMethodInsn(INVOKESTATIC, "org/codehaus/groovy/runtime/ScriptBytecodeAdapter", "asType", "(Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;");
            BytecodeExpr.checkCast(getType(), mv);
        }
    }

    public static class Cast extends BytecodeExpr {
        private final BytecodeExpr expr;

        public Cast(ClassNode type, BytecodeExpr expr) {
            super(expr, type);
            this.expr = expr;
        }

        protected void compile(MethodVisitor mv) {
            expr.visit(mv);
            box(expr.getType(), mv);
            cast(TypeUtil.wrapSafely(expr.getType()), TypeUtil.wrapSafely(getType()), mv);
            unbox(getType(), mv);
        }
    }
}
