package com.doit.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TaskTreeTest {

    private fun task(
        id: Long,
        parent: Long?,
        title: String,
        done: Boolean = false,
        order: Long = 0,
        priority: Int = 0,
    ) = TaskEntity(
        id = id,
        parentId = parent,
        title = title,
        done = done,
        sortOrder = order,
        priority = priority,
    )

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
    fun `紧急优先只重排顶级任务且同优先级稳定`() {
        val flat = listOf(
            task(1, null, "不设", priority = 0),
            task(2, null, "紧急甲", priority = 4),
            task(3, null, "普通", priority = 2),
            task(4, null, "紧急乙", priority = 4),
            task(5, 2, "子任务一", priority = 0),
            task(6, 2, "子任务二", priority = 4),
        )
        val tree = buildTree(flat)
        val sorted = sortTopByPriority(tree, true)
        assertEquals(listOf("紧急甲", "紧急乙", "普通", "不设"), sorted.map { it.task.title })
        assertEquals(listOf("子任务一", "子任务二"), sorted[0].children.map { it.task.title })
        // 子任务节点引用保持不变，未被重新构造或排序
        assertSame(tree.first { it.task.id == 2L }.children[1], sorted[0].children[1])
        // 不修改入参顺序
        assertEquals("不设", tree[0].task.title)
    }

    @Test
    fun `紧急优先关闭时保持原顺序`() {
        val tree = buildTree(listOf(task(1, null, "低", priority = 1), task(2, null, "紧急", priority = 4)))
        val unchanged = sortTopByPriority(tree, false)
        assertEquals(listOf("低", "紧急"), unchanged.map { it.task.title })
        assertSame(tree, unchanged)
    }

    private fun completedAtOn(day: LocalDate): Long =
        day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun `今日待办只显示未完成或今天完成的顶级任务`() {
        val today = LocalDate.of(2026, 11, 27)
        val yesterday = today.minusDays(1)
        val node = { id: Long, done: Boolean, at: Long? ->
            buildTree(listOf(task(id, null, "t$id", done = done).copy(completedAt = at))).first()
        }
        // 未完成始终显示
        assertTrue(showInToday(node(1, false, null), today))
        // 今天完成显示
        assertTrue(showInToday(node(2, true, completedAtOn(today)), today))
        // 昨天完成隐藏（跨午夜刷新后不再出现在今日）
        assertFalse(showInToday(node(3, true, completedAtOn(yesterday)), today))
        // 完成时间缺失隐藏
        assertFalse(showInToday(node(4, true, null), today))
    }

    @Test
    fun `跨午夜后同一任务从显示变为隐藏`() {
        val day1 = LocalDate.of(2026, 11, 26)
        val day2 = day1.plusDays(1)
        val finished = buildTree(
            listOf(task(9, null, "昨天的任务", done = true).copy(completedAt = completedAtOn(day1))),
        ).first()
        assertTrue(showInToday(finished, day1))
        assertFalse(showInToday(finished, day2))
    }

    @Test
    fun `子任务未全做完的父任务即使勾上也照常显示`() {
        val today = LocalDate.of(2026, 11, 27)
        val parent = buildTree(
            listOf(
                task(1, null, "父", done = true).copy(completedAt = completedAtOn(today.minusDays(3))),
                task(2, 1, "未完成的子任务", done = false),
            ),
        ).first()
        // 父任务自己勾了但子任务没全做完 → 未整体完成，旧完成时间不该把它隐藏
        assertTrue(showInToday(parent, today))
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
