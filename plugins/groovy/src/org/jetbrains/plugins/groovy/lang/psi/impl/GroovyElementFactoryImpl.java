/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrWhileStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;

/**
 * @author ven
 */
public class GroovyElementFactoryImpl extends GroovyElementFactory implements ProjectComponent {
  Project myProject;

  public GroovyElementFactoryImpl(Project project) {
    myProject = project;
  }

  private static String DUMMY = "dummy.";

  public PsiElement createIdentifierFromText(String idText) {
    PsiFile file = createGroovyFile(idText);
    return ((GrReferenceExpression) ((GroovyFile) file).getTopStatements()[0]).getReferenceNameElement();
  }

  public GrReferenceExpression createReferenceExpressionFromText(String idText) {
    PsiFile file = createGroovyFile(idText);
    return (GrReferenceExpression) ((GroovyFile) file).getTopStatements()[0];
  }

  public GrVariableDeclaration createVariableDeclaration(String identifier, GrExpression initializer, PsiType type, boolean isFinal) {
    StringBuffer text = new StringBuffer();
    text.append("def ");
    if (isFinal) {
      text.append("final ");
    }
    if (type != null) {
      text.append(type.getPresentableText()).append(" ");
    }
    text.append(identifier);
    text.append("=").append(initializer.getText());
    PsiFile file = createGroovyFile(text.toString());
    return ((GrVariableDeclaration) ((GroovyFile) file).getTopStatements()[0]);
  }

  @Nullable
  public GroovyPsiElement createTopElementFromText(String text) {
    PsiFile dummyFile = PsiManager.getInstance(myProject).getElementFactory().createFileFromText(DUMMY + GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension(),
        text);
    return (GroovyPsiElement) dummyFile.getFirstChild();
  }

  public GrClosableBlock createClosureFromText(String s) throws IncorrectOperationException {
    PsiFile psiFile = PsiManager.getInstance(myProject).getElementFactory().createFileFromText("__DUMMY." + GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension(), s);
    ASTNode node = psiFile.getFirstChild().getNode();
    if (node.getElementType() != GroovyElementTypes.CLOSABLE_BLOCK)
      throw new IncorrectOperationException("Invalid closure text");
    return (GrClosableBlock) node.getPsi();
  }

  public GrParameter createParameter(String name, @Nullable String typeText) throws IncorrectOperationException {
    String fileText;
    if (typeText != null) {
      fileText = "def foo(" + typeText + " " + name + ") {}";
    } else {
      fileText = "def foo(" + name + ") {}";
    }
    PsiFile psiFile = PsiManager.getInstance(myProject).getElementFactory().createFileFromText("__DUMMY." + GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension(), fileText);
    ASTNode node = psiFile.getFirstChild().getNode();
    if (node.getElementType() != GroovyElementTypes.METHOD_DEFINITION)
      throw new IncorrectOperationException("Invalid closure text");
    return ((GrMethod) node.getPsi()).getParameters()[0];
  }

  private PsiFile createGroovyFile(String idText) {
    return PsiManager.getInstance(myProject).getElementFactory().createFileFromText("__DUMMY." + GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension(), idText);
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "Groovy Element Factory";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public PsiElement createWhiteSpace() {
    PsiFile dummyFile = PsiManager.getInstance(myProject).getElementFactory().createFileFromText(DUMMY + GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension(),
        " ");
    return dummyFile.getFirstChild();
  }

  public PsiElement createNewLine() {
        PsiFile dummyFile = PsiManager.getInstance(myProject).getElementFactory().createFileFromText(DUMMY + GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension(),
        "\n");
    return dummyFile.getFirstChild();
  }

  public PsiElement createSemicolon() {
        PsiFile dummyFile = PsiManager.getInstance(myProject).getElementFactory().createFileFromText(DUMMY + GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension(),
        ";");
    return dummyFile.getFirstChild();
  }

  public GrArgumentList createArgumentList(GrExpression... expressions) {
    StringBuffer text = new StringBuffer();
    text.append("ven (");
    for (GrExpression expression : expressions) {
      text.append(expression.getText());
    }
    text.append(")");
    PsiFile file = createGroovyFile(text.toString());
    assert file.getChildren()[0] != null && (file.getChildren()[0] instanceof GrMethodCall);
    return (((GrMethodCall) file.getChildren()[0])).getArgumentList();
  }

  public GrOpenBlock createOpenBlockFromStatements(@NonNls GrStatement... statements) {
    StringBuffer text = new StringBuffer();
    text.append("while (true) { \n");
    for (GrStatement statement : statements) {
      text.append(statement.getText()).append("\n");
    }
    text.append("}");
    PsiFile file = createGroovyFile(text.toString());
    assert file.getChildren()[0] != null && (file.getChildren()[0] instanceof GrWhileStatement);
    return ((GrOpenBlock) ((GrWhileStatement) file.getChildren()[0]).getBody());
  }

  public GrImportStatement createImportStatementFromText(String qName) {
    PsiFile dummyFile = PsiManager.getInstance(myProject).getElementFactory().createFileFromText(DUMMY + GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension(),
        "import " + qName + " ");
    return ((GrImportStatement) dummyFile.getFirstChild());
  }


}
