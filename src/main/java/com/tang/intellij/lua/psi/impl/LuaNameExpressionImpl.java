/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
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

package com.tang.intellij.lua.psi.impl;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceService;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.tang.intellij.lua.comment.psi.api.LuaComment;
import com.tang.intellij.lua.lang.type.LuaType;
import com.tang.intellij.lua.lang.type.LuaTypeSet;
import com.tang.intellij.lua.psi.*;
import com.tang.intellij.lua.search.SearchContext;
import com.tang.intellij.lua.stubs.LuaNameStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 *
 * Created by TangZX on 2017/4/12.
 */
public class LuaNameExpressionImpl extends StubBasedPsiElementBase<LuaNameStub> implements LuaExpression, LuaGlobalVar {
    public LuaNameExpressionImpl(@NotNull LuaNameStub stub, @NotNull IStubElementType nodeType) {
        super(stub, nodeType);
    }

    public LuaNameExpressionImpl(@NotNull ASTNode node) {
        super(node);
    }

    public LuaNameExpressionImpl(LuaNameStub stub, IElementType nodeType, ASTNode node) {
        super(stub, nodeType, node);
    }

    @NotNull
    @Override
    public PsiReference[] getReferences() {
        return ReferenceProvidersRegistry.getReferencesFromProviders(this, PsiReferenceService.Hints.NO_HINTS);
    }

    @Override
    public PsiReference getReference() {
        PsiReference[] references = getReferences();

        if (references.length > 0)
            return references[0];
        return null;
    }

    @Override
    public LuaTypeSet guessType(SearchContext context) {
        LuaTypeSet typeSet = LuaTypeSet.create();
        LuaNameExpr nameExpr = (LuaNameExpr) this;

        PsiElement[] multiResolve = LuaPsiResolveUtil.multiResolve(nameExpr, context);
        for (PsiElement def : multiResolve) {
            LuaTypeSet set = getTypeSet(context, nameExpr, def);
            typeSet = typeSet.union(set);
        }
        return typeSet;
    }

    @Nullable
    private LuaTypeSet getTypeSet(SearchContext context, LuaNameExpr nameExpr, PsiElement def) {
        if (def == null) { //也许是Global
            return LuaTypeSet.create(LuaType.createGlobalType(nameExpr));
        } else if (def instanceof LuaTypeGuessable) {
            return ((LuaTypeGuessable) def).guessType(context);
        } else if (def instanceof LuaNameExpr) {
            LuaNameExpr newRef = (LuaNameExpr) def;
            LuaTypeSet typeSet = null;
            LuaAssignStat luaAssignStat = PsiTreeUtil.getParentOfType(def, LuaAssignStat.class);
            if (luaAssignStat != null) {
                LuaComment comment = luaAssignStat.getComment();
                //优先从 Comment 猜
                if (comment != null) {
                    typeSet = comment.guessType(context);
                }
                //再从赋值猜
                if (typeSet == null) {
                    LuaExprList exprList = luaAssignStat.getExprList();
                    if (exprList != null)
                        typeSet = exprList.guessTypeAt(0, context);//TODO : multi
                }
            }
            //同时是 Global ?
            if (LuaPsiResolveUtil.resolveLocal(newRef, context) == null) {
                if (typeSet == null || typeSet.isEmpty())
                    typeSet = LuaTypeSet.create(LuaType.createGlobalType(newRef));
                else
                    typeSet.addType(LuaType.createGlobalType(newRef));
            }
            return typeSet;
        }
        return null;
    }

    @Nullable
    @Override
    public LuaNameExpr getNameRef() {
        return (LuaNameExpr)this;
    }
}
