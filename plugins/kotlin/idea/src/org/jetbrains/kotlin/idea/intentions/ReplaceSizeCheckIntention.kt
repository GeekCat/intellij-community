// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.core.replaced
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

abstract class ReplaceSizeCheckIntention(textGetter: () -> String) : SelfTargetingOffsetIndependentIntention<KtBinaryExpression>(
    KtBinaryExpression::class.java, textGetter
) {
    override fun applyTo(element: KtBinaryExpression, editor: Editor?) {
        val target = getTargetExpression(element)
        val newExpression = if (target is KtDotQualifiedExpression) {
            "${target.receiverExpression.text}.${getGenerateMethodSymbol()}"
        } else {
            getGenerateMethodSymbol()
        }
        element.replaced(KtPsiFactory(element).createExpression(newExpression))
    }

    override fun isApplicableTo(element: KtBinaryExpression): Boolean {
        val targetExpression = getTargetExpression(element) ?: return false
        return targetExpression.isSizeOrLength() || targetExpression.isCountCall { it.valueArguments.isEmpty() }
    }

    abstract fun getTargetExpression(element: KtBinaryExpression): KtExpression?

    abstract fun getGenerateMethodSymbol(): String
}