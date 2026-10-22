package fr.randommediapicker.domain

import org.junit.Assert.*
import org.junit.Test

class FolderSelectionTest {
    private val parent = FolderNode("parent", null, "Parent", 0, selection = SelectionOverride.INCLUDE)
    private val child = FolderNode("child", "parent", "Enfant", 1)
    private val grandchild = FolderNode("grandchild", "child", "Petit-enfant", 2)

    @Test fun excludingChildOverridesSelectedParent() {
        val nodes = listOf(parent, child.copy(selection = SelectionOverride.EXCLUDE), grandchild)
        assertEquals(mapOf("parent" to true, "child" to false, "grandchild" to false), FolderSelection.effective(nodes))
        assertEquals(FolderCheck.PARTIAL, FolderSelection.checks(nodes)["parent"])
    }

    @Test fun childCanBeReincludedAndAncestorsBecomePartial() {
        val nodes = listOf(parent.copy(selection = SelectionOverride.EXCLUDE), child,
            grandchild.copy(selection = SelectionOverride.INCLUDE))
        val checks = FolderSelection.checks(nodes)
        assertEquals(FolderCheck.PARTIAL, checks["parent"])
        assertEquals(FolderCheck.PARTIAL, checks["child"])
        assertEquals(FolderCheck.INCLUDED, checks["grandchild"])
    }

    @Test fun parentSelectionAffectsEveryDescendant() {
        assertEquals(setOf("parent", "child", "grandchild"), FolderSelection.descendants("parent", listOf(parent, child, grandchild)))
    }

    @Test fun orderOfFoldersDoesNotAffectInheritance() {
        assertEquals(FolderSelection.effective(listOf(parent, child, grandchild)), FolderSelection.effective(listOf(grandchild, child, parent)))
    }

    @Test fun tenThousandNestedFoldersDoNotOverflowStack() {
        val folders = (0 until 10_000).map { i -> FolderNode("$i", if (i == 0) null else "${i - 1}", "$i", i) }
        assertEquals(10_000, FolderSelection.effective(folders.reversed()).count { it.value })
        assertEquals(10_000, FolderSelection.descendants("0", folders).size)
    }
}
