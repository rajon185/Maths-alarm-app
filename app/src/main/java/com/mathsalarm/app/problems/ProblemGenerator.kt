package com.mathsalarm.app.problems

import com.mathsalarm.app.model.Difficulty
import com.mathsalarm.app.model.ProblemType
import kotlin.random.Random

data class Problem(
    val type: ProblemType,
    /** Instruction shown above the main content, e.g. "Solve for x". */
    val title: String,
    /** The question itself. */
    val prompt: String,
    val answer: String,
    /** Use the on-screen number pad rather than the text keyboard. */
    val numeric: Boolean = true,
    /** For memory problems: how long the prompt stays visible before it is hidden. */
    val memorizeMillis: Long = 0,
) {
    fun isCorrect(input: String): Boolean {
        val given = input.trim()
        if (given.isEmpty()) return false
        return when (type) {
            // Compare digit-for-digit (leading zeros matter) and character-for-character.
            ProblemType.MEMORY, ProblemType.TYPING -> given == answer
            else -> given.toLongOrNull() != null && given.toLongOrNull() == answer.toLongOrNull()
        }
    }
}

object ProblemGenerator {

    fun generate(types: Set<ProblemType>, difficulty: Difficulty, random: Random = Random.Default): Problem {
        val type = types.ifEmpty { setOf(ProblemType.ARITHMETIC) }.random(random)
        return when (type) {
            ProblemType.ARITHMETIC -> arithmetic(difficulty, random)
            ProblemType.ALGEBRA -> algebra(difficulty, random)
            ProblemType.SEQUENCE -> sequence(difficulty, random)
            ProblemType.MEMORY -> memory(difficulty, random)
            ProblemType.TYPING -> typing(difficulty, random)
        }
    }

    private fun Random.range(from: Int, to: Int) = nextInt(from, to + 1)

    // ---------------------------------------------------------------- arithmetic

    fun arithmetic(difficulty: Difficulty, r: Random): Problem {
        val (q, a) = when (difficulty) {
            Difficulty.EASY -> when (r.nextInt(3)) {
                0 -> { val x = r.range(2, 20); val y = r.range(2, 20); "$x + $y" to x + y }
                1 -> { val x = r.range(5, 30); val y = r.range(1, x); "$x − $y" to x - y }
                else -> { val x = r.range(2, 10); val y = r.range(2, 10); "$x × $y" to x * y }
            }
            Difficulty.MEDIUM -> when (r.nextInt(4)) {
                0 -> { val x = r.range(15, 99); val y = r.range(15, 99); "$x + $y" to x + y }
                1 -> { val x = r.range(40, 150); val y = r.range(11, x - 1); "$x − $y" to x - y }
                2 -> { val x = r.range(11, 25); val y = r.range(3, 12); "$x × $y" to x * y }
                else -> { val y = r.range(3, 12); val ans = r.range(3, 20); "${y * ans} ÷ $y" to ans }
            }
            Difficulty.HARD -> when (r.nextInt(5)) {
                0 -> { val x = r.range(13, 99); val y = r.range(13, 99); "$x × $y" to x * y }
                1 -> { val x = r.range(12, 40); val y = r.range(6, 20); val z = r.range(50, 300); "$x × $y + $z" to x * y + z }
                2 -> { val x = r.range(11, 60); val y = r.range(11, 60); val z = r.range(3, 12); "($x + $y) × $z" to (x + y) * z }
                3 -> { val y = r.range(11, 25); val ans = r.range(11, 40); "${y * ans} ÷ $y" to ans }
                else -> { val x = r.range(300, 999); val y = r.range(100, 999); val z = r.range(100, 400); val s = x + y; "$x + $y − $z" to s - z }
            }
        }
        return Problem(ProblemType.ARITHMETIC, "Calculate", "$q = ?", a.toString())
    }

    // ---------------------------------------------------------------- algebra

    fun algebra(difficulty: Difficulty, r: Random): Problem {
        val x: Int
        val eq: String
        when (difficulty) {
            Difficulty.EASY -> {
                x = r.range(1, 20)
                if (r.nextBoolean()) {
                    val a = r.range(1, 30); eq = "x + $a = ${x + a}"
                } else {
                    val a = r.range(2, 9); eq = "${a}x = ${a * x}"
                }
            }
            Difficulty.MEDIUM -> {
                x = r.range(2, 15)
                val a = r.range(2, 9)
                val b = r.range(1, 40)
                eq = if (r.nextBoolean()) {
                    "${a}x + $b = ${a * x + b}"
                } else {
                    val m = r.range(1, a * x - 1) // keep the right-hand side positive
                    "${a}x − $m = ${a * x - m}"
                }
            }
            Difficulty.HARD -> {
                x = r.range(2, 20)
                if (r.nextBoolean()) {
                    val c = r.range(2, 8)
                    val a = c + r.range(1, 6)
                    val b = r.range(1, 30)
                    val d = a * x + b - c * x
                    eq = "${a}x + $b = ${c}x + $d"
                } else {
                    val a = r.range(2, 9)
                    val b = r.range(1, 15)
                    eq = "$a(x + $b) = ${a * (x + b)}"
                }
            }
        }
        return Problem(ProblemType.ALGEBRA, "Solve for x", eq, x.toString())
    }

    // ---------------------------------------------------------------- sequences

    fun sequence(difficulty: Difficulty, r: Random): Problem {
        val terms: List<Long> = when (difficulty) {
            Difficulty.EASY -> {
                val start = r.range(1, 20).toLong(); val step = r.range(2, 10).toLong()
                List(5) { start + it * step }
            }
            Difficulty.MEDIUM -> if (r.nextBoolean()) {
                val start = r.range(1, 6).toLong(); val ratio = r.range(2, 3).toLong()
                generateSequence(start) { it * ratio }.take(5).toList()
            } else {
                val start = r.range(50, 200).toLong(); val step = r.range(6, 25).toLong()
                val sign = if (start > step * 5 && r.nextBoolean()) -1 else 1
                List(5) { start + sign * it * step }
            }
            Difficulty.HARD -> when (r.nextInt(3)) {
                0 -> { // Fibonacci-like
                    val a = r.range(1, 9).toLong(); val b = r.range(2, 12).toLong()
                    val list = mutableListOf(a, b)
                    while (list.size < 6) list += list[list.size - 1] + list[list.size - 2]
                    list
                }
                1 -> { // constant second difference
                    val start = r.range(1, 20).toLong(); var diff = r.range(1, 6).toLong(); val inc = r.range(2, 5).toLong()
                    val list = mutableListOf(start)
                    while (list.size < 6) { list += list.last() + diff; diff += inc }
                    list
                }
                else -> { // alternating ×k then +m
                    val k = r.range(2, 3).toLong(); val m = r.range(1, 9).toLong()
                    val list = mutableListOf(r.range(1, 5).toLong())
                    while (list.size < 6) list += if (list.size % 2 == 1) list.last() * k else list.last() + m
                    list
                }
            }
        }
        val shown = terms.dropLast(1).joinToString(",  ") + ",  ?"
        return Problem(ProblemType.SEQUENCE, "What comes next?", shown, terms.last().toString())
    }

    // ---------------------------------------------------------------- memory

    fun memory(difficulty: Difficulty, r: Random): Problem {
        val (digits, millis) = when (difficulty) {
            Difficulty.EASY -> 4 to 4000L
            Difficulty.MEDIUM -> 6 to 4000L
            Difficulty.HARD -> 9 to 5000L
        }
        val number = buildString { repeat(digits) { append(r.nextInt(10)) } }
        return Problem(ProblemType.MEMORY, "Memorise this number", number, number, numeric = true, memorizeMillis = millis)
    }

    // ---------------------------------------------------------------- typing

    private const val LOWER = "abcdefghijkmnpqrstuvwxyz"   // no l / o
    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"   // no I / O
    private const val DIGITS = "23456789"                  // no 0 / 1
    private const val SYMBOLS = "!?#@%&*+"

    fun typing(difficulty: Difficulty, r: Random): Problem {
        val (length, charset) = when (difficulty) {
            Difficulty.EASY -> 6 to LOWER
            Difficulty.MEDIUM -> 10 to LOWER + UPPER + DIGITS
            Difficulty.HARD -> 14 to LOWER + UPPER + DIGITS + SYMBOLS
        }
        val text = buildString { repeat(length) { append(charset[r.nextInt(charset.length)]) } }
        return Problem(ProblemType.TYPING, "Type this exactly (case-sensitive)", text, text, numeric = false)
    }
}
