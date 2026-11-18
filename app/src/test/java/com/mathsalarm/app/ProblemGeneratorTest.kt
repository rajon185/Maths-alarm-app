package com.mathsalarm.app

import com.mathsalarm.app.model.Difficulty
import com.mathsalarm.app.model.ProblemType
import com.mathsalarm.app.problems.ProblemGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ProblemGeneratorTest {

    private fun evalArithmetic(expr: String): Long {
        // Tiny evaluator for the generator's own notation: + − × ÷ and parentheses.
        val tokens = Regex("\\d+|[+−×÷()]").findAll(expr).map { it.value }.toList()
        var pos = 0
        fun parseExpr(): Long {
            fun factor(): Long {
                val t = tokens[pos++]
                return if (t == "(") { val v = parseExpr(); pos++; v } else t.toLong()
            }
            fun term(): Long {
                var v = factor()
                while (pos < tokens.size && tokens[pos] in setOf("×", "÷")) {
                    val op = tokens[pos++]; val r = factor()
                    v = if (op == "×") v * r else { assertEquals("non-exact division in $expr", 0, v % r); v / r }
                }
                return v
            }
            var v = term()
            while (pos < tokens.size && tokens[pos] in setOf("+", "−")) {
                val op = tokens[pos++]; val r = term()
                v = if (op == "+") v + r else v - r
            }
            return v
        }
        return parseExpr()
    }

    @Test
    fun arithmeticAnswersAreCorrectAndNonNegative() {
        val r = Random(1)
        for (d in Difficulty.entries) repeat(2000) {
            val p = ProblemGenerator.arithmetic(d, r)
            val expr = p.prompt.removeSuffix(" = ?")
            val expected = evalArithmetic(expr)
            assertEquals(p.prompt, expected.toString(), p.answer)
            assertTrue(p.prompt, expected >= 0)
        }
    }

    @Test
    fun algebraSolutionsSatisfyTheEquation() {
        val r = Random(2)
        for (d in Difficulty.entries) repeat(2000) {
            val p = ProblemGenerator.algebra(d, r)
            val x = p.answer.toLong()
            assertTrue(x > 0)
            val (lhs, rhs) = p.prompt.split(" = ")
            fun side(s: String): Long {
                val withX = s.replace(Regex("(\\d+)x")) { "${it.groupValues[1]}×$x" }
                    .replace(Regex("(\\d+)\\(")) { "${it.groupValues[1]}×(" }
                    .replace("x", "$x")
                return evalArithmetic(withX)
            }
            assertEquals(p.prompt, side(lhs), side(rhs))
        }
    }

    @Test
    fun sequencesHavePositiveAnswers() {
        val r = Random(3)
        for (d in Difficulty.entries) repeat(2000) {
            val p = ProblemGenerator.sequence(d, r)
            assertTrue(p.prompt.endsWith("?"))
            assertTrue(p.answer.toLong() > 0)
        }
    }

    @Test
    fun memoryAndTypingCompareExactly() {
        val r = Random(4)
        val m = ProblemGenerator.memory(Difficulty.HARD, r)
        assertEquals(9, m.answer.length)
        assertTrue(m.isCorrect(m.answer))
        assertTrue(m.memorizeMillis > 0)

        val t = ProblemGenerator.typing(Difficulty.MEDIUM, r)
        assertEquals(10, t.answer.length)
        assertTrue(t.isCorrect(t.answer))
        assertTrue(t.isCorrect(" ${t.answer} "))
        assertFalse(t.isCorrect(t.answer.lowercase().let { if (it == t.answer) it + "x" else it }))
    }

    @Test
    fun numericAnswersIgnoreLeadingZeros() {
        val p = ProblemGenerator.arithmetic(Difficulty.EASY, Random(5))
        assertTrue(p.isCorrect("0" + p.answer))
        assertFalse(p.isCorrect(""))
        assertFalse(p.isCorrect("abc"))
    }

    @Test
    fun generateOnlyUsesSelectedTypes() {
        val r = Random(6)
        val allowed = setOf(ProblemType.ALGEBRA, ProblemType.TYPING)
        repeat(200) { assertTrue(ProblemGenerator.generate(allowed, Difficulty.MEDIUM, r).type in allowed) }
    }
}
