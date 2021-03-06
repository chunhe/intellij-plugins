// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.actions.styling;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.markdown.lang.MarkdownElementTypes;
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes;
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement;
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElementFactory;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeaderImpl;
import org.intellij.plugins.markdown.ui.actions.MarkdownActionUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public abstract class MarkdownHeaderAction extends AnAction implements DumbAware {
  /**
   * Returns function that increases or decreases level by 1
   */
  @NotNull
  protected abstract Function<Integer, Integer> getLevelFunction();

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);

    final Editor editor = MarkdownActionUtil.findMarkdownTextEditor(e);
    final PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
    if (editor == null || psiFile == null || !psiFile.isValid()) {
      return;
    }

    for (Caret caret : ContainerUtil.reverse(editor.getCaretModel().getAllCarets())) {
      final Couple<PsiElement> elements = MarkdownActionUtil.getElementsUnderCaretOrSelection(psiFile, caret);
      if (elements == null || elements.getFirst() != elements.getSecond()) {
        e.getPresentation().setEnabled(false);
        return;
      }

      PsiElement element = elements.getFirst();
      MarkdownHeaderImpl header = PsiTreeUtil.getParentOfType(element, MarkdownHeaderImpl.class, false);
      if (header == null && element.getNode().getElementType() != MarkdownTokenTypes.TEXT) {
        e.getPresentation().setEnabled(false);
        return;
      }

      e.getPresentation().setEnabled(true);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Editor editor = MarkdownActionUtil.findMarkdownTextEditor(e);
    final PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
    if (editor == null || psiFile == null) {
      return;
    }

    WriteCommandAction.runWriteCommandAction(psiFile.getProject(), () -> {
      if (!psiFile.isValid()) {
        return;
      }

      for (Caret caret : ContainerUtil.reverse(editor.getCaretModel().getAllCarets())) {
        PsiElement element = Objects.requireNonNull(MarkdownActionUtil.getElementsUnderCaretOrSelection(psiFile, caret)).getFirst();
        MarkdownHeaderImpl header = PsiTreeUtil.getParentOfType(element, MarkdownHeaderImpl.class, false);

        if (header != null) {
          header.replace(createNewLevelHeader(header));
        }
        else if (element.getNode().getElementType() == MarkdownTokenTypes.TEXT) {
          element.replace(createHeaderForText(element));
        }
      }
    });
  }

  @NotNull
  public MarkdownPsiElement createHeaderForText(@NotNull PsiElement textElement) {
    int level = (getLevelFunction().fun(0) + 7) % 7;

    return MarkdownPsiElementFactory.createHeader(textElement.getProject(), textElement.getText(), level);
  }

  @NotNull
  public MarkdownPsiElement createNewLevelHeader(@NotNull MarkdownHeaderImpl header) {
    int level = getLevelFunction().fun(Objects.requireNonNull(header).getHeaderNumber()) % 7;

    MarkdownPsiElement newElement;
    Project project = header.getProject();
    String headerName = Objects.requireNonNull(header.getName());
    if (header.getNode().getElementType() == MarkdownElementTypes.SETEXT_1 && level == 2) {
      newElement = MarkdownPsiElementFactory.createSetext(project, headerName, "-", header.getLastChild().getTextLength());
    }
    else if (header.getNode().getElementType() == MarkdownElementTypes.SETEXT_2 && level == 1) {
      newElement = MarkdownPsiElementFactory.createSetext(project, headerName, "=", header.getLastChild().getTextLength());
    }
    else {
      newElement = level == 0
                   ? MarkdownPsiElementFactory.createTextElement(project, headerName)
                   : MarkdownPsiElementFactory.createHeader(project, headerName, level);
    }
    return newElement;
  }
}