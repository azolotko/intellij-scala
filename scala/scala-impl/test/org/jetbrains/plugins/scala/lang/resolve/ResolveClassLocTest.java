package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait;

/**
 * User: Alexander Podkhalyuzin
 * Date: 01.11.11
 */
public class ResolveClassLocTest extends ScalaResolveTestCase {
  public String folderPath() {
    return super.folderPath() + "resolve/class/loc/";
  }

  public void testMyClass() {
    PsiReference ref = findReferenceAtCaret();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof ScTrait);
    assertEquals(((ScTrait) resolved).qualifiedName(), "org.MyTrait");
  }
}
