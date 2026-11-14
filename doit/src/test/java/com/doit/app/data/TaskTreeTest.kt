package com.doit.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskTreeTest {

    private fun task(id: Long, parent: Long?, title: String, done: Boolean = false, order: Long = 0) =
        TaskEntity(id = id, parentId = parent, title = title, done = done, sortOrder = order)

    @Test
    fun `子任务按 parentId 归一且顺序稳定`() {
        val flat = listOf(
            task(1, null, "读完第三章", order = 0),
            task(2, 1, "写读后记", order = 0),
            task(3, 1, "折角标好句", order = 1),
            task(4, null, "给妈妈回个电话", order = 1),
        )
        val tree = buildTree(flat)
        assertEquals(2, tree.size)
        assertEquals("读完第三章", tree[0].task.title)
        assertEquals(2, tree[0].children.size)
        assertEquals("写读后记", tree[0].children[0].task.title)
    }

    @Test
    fun `子任务未做完时父任务被锁住`() {
        val flat = listOf(
            task(1, null, "父任务"),
            task(2, 1, "第一步", done = true),
            task(3, 1, "第二步"),
        )
        val node = buildTree(flat).first()
        assertTrue(node.hasChildren)
        assertTrue(node.locked)
        assertFalse(node.ready)
        assertEquals(1, node.doneChildren)
    }

    @Test
    fun `子任务全做完时父任务进入可收起状态`() {
        val flat = listOf(
            task(1, null, "父任务"),
            task(2, 1, "第一步", done = true),
            task(3, 1, "第二步", done = true),
        )
        val node = buildTree(flat).first()
        assertTrue(node.allChildrenDone)
        assertFalse(node.locked)
        assertTrue(node.ready)
    }
}
