package org.example

/**
 * A super tiny compiler which does the following ->
 *
 * 1. Provided a lisp style expression convert it into a list of tokens.
 * 2. Take those tokens and create an AST while validating the syntax.
 * 3. Create a Visitor that visits all the nodes and reconstructs the original expression. Different visitors can be used
 *    to output different code, showcasing the power of the design pattern.
 */
fun main() {
    val expression = "(add 2 (subtract 4 (add 5 10)))"
    val tokens = tokenize(expression)
    println(tokens)
    val node = ASTNode.Program(parse(tokens, 0).node)
    println("AST: $node")
    println("Visiting the tree and reconstructing original expression")
    visit(node)
}

abstract class Visitor<T: ASTNode> {
    fun visit(node: T) {
        enter(node)
        exit(node)
    }

    abstract fun enter(node: T)
    abstract fun exit(node: T)
}

fun visit(node: ASTNode) {
    when (node) {
        is ASTNode.Add -> AddVisitor().visit(node)
        is ASTNode.NumberLiteral -> NumberVisitor().visit(node)
        is ASTNode.Program -> ProgramVisitor().visit(node)
        is ASTNode.StringLiteral -> StringVisitor().visit(node)
        is ASTNode.Subtract -> SubVisitor().visit(node)
    }
}

abstract class PrintVisitor<T: ASTNode> : Visitor<T>()


class ProgramVisitor: PrintVisitor<ASTNode.Program>() {
    override fun enter(node: ASTNode.Program) {
        visit(node.body)
    }

    override fun exit(node: ASTNode.Program) {
    }
}

class AddVisitor: PrintVisitor<ASTNode.Add>() {
    override fun enter(node: ASTNode.Add) {
        print("(add ")
        visit(node.leftOperand)
        visit(node.rightOperand)
    }

    override fun exit(node: ASTNode.Add) {
        print(")")
    }
}

class SubVisitor: PrintVisitor<ASTNode.Subtract>() {
    override fun enter(node: ASTNode.Subtract) {
        print("(subtract ")
        visit(node.leftOperand)
        visit(node.rightOperand)
    }


    override fun exit(node: ASTNode.Subtract) {
        print(")")
    }
}

class StringVisitor: PrintVisitor<ASTNode.StringLiteral>() {
    override fun enter(node: ASTNode.StringLiteral) {
        print(node.value)
    }

    override fun exit(node: ASTNode.StringLiteral) {
        print(" ")
    }
}


class NumberVisitor: PrintVisitor<ASTNode.NumberLiteral>() {
    override fun enter(node: ASTNode.NumberLiteral) {
        print(node.value)
    }

    override fun exit(node: ASTNode.NumberLiteral) {
        print(" ")
    }
}



sealed interface Token {

    data class NumberLiteral(val value: Int) : Token
    data class StringLiteral(val value: String) : Token
    data class CallExpression(val type: CallType) : Token {
        enum class CallType {
            ADD,
            SUBTRACT,
        }
    }
    data object ParenthesisBegin : Token
    data object ParenthesisEnd : Token
}

sealed interface ASTNode {
    data class NumberLiteral(val value: Int): ASTNode
    data class StringLiteral(val value: String): ASTNode
    data class Program(val body: ASTNode): ASTNode
    data class Add(val leftOperand: ASTNode, val rightOperand: ASTNode): ASTNode
    data class Subtract(val leftOperand: ASTNode, val rightOperand: ASTNode): ASTNode
}

data class ParsedValue(
    val node: ASTNode,
    val index: Int
)

/**
 * Parses on param of call expression, can be used for either left or right param
 */
fun parseCallExpressionsParam(tokens: List<Token>, startingIndex: Int): ParsedValue {
    var currentIndex = startingIndex + 1
    var currentToken = tokens[currentIndex]
    var node: ASTNode
    while (true) {
        when (currentToken) {
            is Token.CallExpression -> error("2 consecutive Calls")
            is Token.NumberLiteral -> {
                node = ASTNode.NumberLiteral(currentToken.value)
                break
            }
            Token.ParenthesisBegin -> {
                val (parsedNode, finalIndex) = parse(tokens, currentIndex)
                node = parsedNode
                currentIndex = finalIndex
                break;
            }
            Token.ParenthesisEnd -> error("Invalid syntax")
            is Token.StringLiteral -> {
                node = ASTNode.StringLiteral(currentToken.value)
                break
            }
        }
    }

    return ParsedValue(node, currentIndex)
}


fun parse(tokens: List<Token>, startingIndex: Int): ParsedValue {
    var currentIndex = startingIndex
    if (tokens[currentIndex] != Token.ParenthesisBegin) {
        error("Must be used from starting of Parenthesis")
    }
    currentIndex ++
    var nodeToReturn: ASTNode? = null
    while (true) {
        var currentToken = tokens[currentIndex]
        when (currentToken) {
            is Token.NumberLiteral -> error("Number literal should not come here")
            is Token.StringLiteral -> error("String literal should not come here")
            is Token.CallExpression -> {
                nodeToReturn = when (currentToken.type) {
                    Token.CallExpression.CallType.ADD -> {
                        val (leftOperand, nextIndex) = parseCallExpressionsParam(tokens, currentIndex)
                        val (rightOperand, finalIndex) = parseCallExpressionsParam(tokens, nextIndex)
                        currentIndex = finalIndex + 1
                        ASTNode.Add(leftOperand, rightOperand)
                    }
                    Token.CallExpression.CallType.SUBTRACT -> {
                        val (leftOperand, nextIndex) = parseCallExpressionsParam(tokens, currentIndex)
                        val (rightOperand, finalIndex) = parseCallExpressionsParam(tokens, nextIndex)
                        currentIndex = finalIndex + 1
                        ASTNode.Subtract(leftOperand, rightOperand)
                    }
                }
            }
            Token.ParenthesisBegin -> {
                parse(tokens, currentIndex).also {
                    nodeToReturn = it.node
                    currentIndex = it.index
                }
            }
            Token.ParenthesisEnd -> {
                break
            }
        }
    }
    return ParsedValue(requireNotNull(nodeToReturn), currentIndex)
}


fun tokenize(input: String): List<Token> {
    var current = 0
    var tokens = mutableListOf<Token>()
    val whitespace = Regex("\\s")

    while (current < input.length) {
        var c : Char = input[current]
        if (c == '(') {
            tokens.add(Token.ParenthesisBegin)
            current++
            continue
        }
        if (c == ')') {
            tokens.add(Token.ParenthesisEnd)
            current++
            continue
        }


        if (whitespace.matches(c.toString())) {
            current++
            continue
        }

        if (c.isDigit()) {
            var value = ""
            while (c.isDigit()) {
                value += c
                current ++
                c = input[current]
            }
            tokens.add(Token.NumberLiteral(value.toInt()))
            continue;
        }

        if (c == '"') {
            var value = ""
            current++
            c = input[current]
            while (c != '"') {
                value += c
                current++
                c = input[current]
            }
            current++
            c = input[current]

            tokens.add(Token.StringLiteral(value))

            continue
        }

        if (c.isLetter()) {
            var value = ""
            while (c.isLetter()) {
                value += c
                current ++
                c = input[current]
            }

            val callType = Token.CallExpression.CallType.valueOf(value.uppercase())
            tokens.add(Token.CallExpression(callType))

            continue
        }

        error("Unknown char: $c")
    }

    return tokens

}
