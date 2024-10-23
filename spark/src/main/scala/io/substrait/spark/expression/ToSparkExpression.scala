/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.substrait.spark.expression

import io.substrait.spark.{DefaultExpressionVisitor, HasOutputStack, SparkExtension, ToSubstraitType}
import io.substrait.spark.logical.ToLogicalPlan

import org.apache.spark.sql.catalyst.expressions.{CaseWhen, Cast, Expression, In, Literal, MakeDecimal, NamedExpression, ScalarSubquery}
import org.apache.spark.sql.types.Decimal
import org.apache.spark.substrait.SparkTypeUtil
import org.apache.spark.unsafe.types.UTF8String

import io.substrait.`type`.{StringTypeVisitor, Type}
import io.substrait.{expression => exp}
import io.substrait.expression.{Expression => SExpression}
import io.substrait.util.DecimalUtil
import io.substrait.utils.Util

import scala.collection.JavaConverters.{asScalaBufferConverter, mapAsScalaMapConverter}

class ToSparkExpression(
    val scalarFunctionConverter: ToScalarFunction,
    val toLogicalPlan: Option[ToLogicalPlan] = None)
  extends DefaultExpressionVisitor[Expression]
  with HasOutputStack[Seq[NamedExpression]] {

  override def visit(expr: SExpression.BoolLiteral): Expression = {
    if (expr.value()) {
      Literal.TrueLiteral
    } else {
      Literal.FalseLiteral
    }
  }

  override def visit(expr: SExpression.I8Literal): Expression = {
    Literal(expr.value().asInstanceOf[Byte], ToSubstraitType.convert(expr.getType))
  }

  override def visit(expr: SExpression.I16Literal): Expression = {
    Literal(expr.value().asInstanceOf[Short], ToSubstraitType.convert(expr.getType))
  }

  override def visit(expr: SExpression.I32Literal): Expression = {
    Literal(expr.value(), ToSubstraitType.convert(expr.getType))
  }

  override def visit(expr: SExpression.I64Literal): Expression = {
    Literal(expr.value(), ToSubstraitType.convert(expr.getType))
  }

  override def visit(expr: SExpression.FP32Literal): Literal = {
    Literal(expr.value(), ToSubstraitType.convert(expr.getType))
  }

  override def visit(expr: SExpression.FP64Literal): Expression = {
    Literal(expr.value(), ToSubstraitType.convert(expr.getType))
  }

  override def visit(expr: SExpression.StrLiteral): Expression = {
    Literal(UTF8String.fromString(expr.value()), ToSubstraitType.convert(expr.getType))
  }

  override def visit(expr: SExpression.FixedCharLiteral): Expression = {
    Literal(UTF8String.fromString(expr.value()), ToSubstraitType.convert(expr.getType))
  }

  override def visit(expr: SExpression.VarCharLiteral): Expression = {
    Literal(UTF8String.fromString(expr.value()), ToSubstraitType.convert(expr.getType))
  }

  override def visit(expr: SExpression.BinaryLiteral): Literal = {
    Literal(expr.value().toByteArray, ToSubstraitType.convert(expr.getType))
  }

  override def visit(expr: SExpression.DecimalLiteral): Expression = {
    val value = expr.value.toByteArray
    val decimal = DecimalUtil.getBigDecimalFromBytes(value, expr.scale, 16)
    Literal(Decimal(decimal), ToSubstraitType.convert(expr.getType))
  }

  override def visit(expr: SExpression.DateLiteral): Expression = {
    Literal(expr.value(), ToSubstraitType.convert(expr.getType))
  }

  override def visit(expr: SExpression.PrecisionTimestampLiteral): Literal = {
    Literal(
      Util.toMicroseconds(expr.value(), expr.precision()),
      ToSubstraitType.convert(expr.getType))

  }

  override def visit(expr: SExpression.PrecisionTimestampTZLiteral): Literal = {
    Literal(
      Util.toMicroseconds(expr.value(), expr.precision()),
      ToSubstraitType.convert(expr.getType))
  }

  override def visit(expr: SExpression.IntervalDayLiteral): Literal = {
    val micros =
      (expr.days() * Util.SECONDS_PER_DAY + expr.seconds()) * Util.MICROSECOND_PRECISION +
        Util.toMicroseconds(expr.subseconds(), expr.precision())
    Literal(micros, ToSubstraitType.convert(expr.getType))
  }

  override def visit(expr: SExpression.IntervalYearLiteral): Literal = {
    val months = expr.years() * 12 + expr.months()
    Literal(months, ToSubstraitType.convert(expr.getType))
  }

  override def visit(expr: SExpression.ListLiteral): Literal = {
    val array = expr.values().asScala.map(value => value.accept(this).asInstanceOf[Literal].value)
    Literal.create(array, ToSubstraitType.convert(expr.getType))
  }

  override def visit(expr: SExpression.EmptyListLiteral): Expression = {
    Literal.default(ToSubstraitType.convert(expr.getType))
  }

  override def visit(expr: SExpression.MapLiteral): Literal = {
    val map = expr.values().asScala.map {
      case (key, value) =>
        (
          key.accept(this).asInstanceOf[Literal].value,
          value.accept(this).asInstanceOf[Literal].value
        )
    }
    Literal.create(map, ToSubstraitType.convert(expr.getType))
  }

  override def visit(expr: SExpression.EmptyMapLiteral): Literal = {
    Literal.default(ToSubstraitType.convert(expr.getType))
  }

  override def visit(expr: SExpression.NullLiteral): Expression = {
    Literal(null, ToSubstraitType.convert(expr.getType))
  }

  override def visit(expr: SExpression.Cast): Expression = {
    val childExp = expr.input().accept(this)
    Cast(childExp, ToSubstraitType.convert(expr.getType))
  }

  override def visit(expr: exp.FieldReference): Expression = {
    withFieldReference(expr)(i => currentOutput(i).clone())
  }

  override def visit(expr: SExpression.IfThen): Expression = {
    val branches = expr
      .ifClauses()
      .asScala
      .map(
        ifClause => {
          val predicate = ifClause.condition().accept(this)
          val elseValue = ifClause.`then`().accept(this)
          (predicate, elseValue)
        })
    val default = expr.elseClause().accept(this) match {
      case l: Literal if l.nullable => None
      case other => Some(other)
    }
    CaseWhen(branches, default)
  }

  override def visit(expr: SExpression.ScalarSubquery): Expression = {
    val rel = expr.input()
    val dataType = ToSubstraitType.convert(expr.getType)
    toLogicalPlan
      .map(
        relConverter => {
          val plan = rel.accept(relConverter)
          require(plan.resolved)
          val result = ScalarSubquery(plan)
          SparkTypeUtil.sameType(result.dataType, dataType)
          result
        })
      .getOrElse(visitFallback(expr))
  }

  override def visit(expr: SExpression.SingleOrList): Expression = {
    val value = expr.condition().accept(this)
    val list = expr.options().asScala.map(e => e.accept(this))
    In(value, list)
  }
  override def visit(expr: SExpression.ScalarFunctionInvocation): Expression = {
    val eArgs = expr.arguments().asScala
    val args = eArgs.zipWithIndex.map {
      case (arg, i) =>
        arg.accept(expr.declaration(), i, this)
    }

    expr.declaration.name match {
      case "make_decimal" if expr.declaration.uri == SparkExtension.uri =>
        expr.outputType match {
          // Need special case handing of this internal function.
          // Because the precision and scale arguments are extracted from the output type,
          // we can't use the generic scalar function conversion mechanism here.
          case d: Type.Decimal => MakeDecimal(args.head, d.precision, d.scale)
          case _ =>
            throw new IllegalArgumentException("Output type of MakeDecimal must be a decimal type")
        }
      case _ =>
        scalarFunctionConverter
          .getSparkExpressionFromSubstraitFunc(expr.declaration().key(), expr.outputType())
          .flatMap(sig => Option(sig.makeCall(args)))
          .getOrElse({
            val msg = String.format(
              "Unable to convert scalar function %s(%s).",
              expr.declaration.name,
              expr.arguments.asScala
                .map {
                  case ea: exp.EnumArg => ea.value.toString
                  case e: SExpression => e.getType.accept(new StringTypeVisitor)
                  case t: Type => t.accept(new StringTypeVisitor)
                  case a => throw new IllegalStateException("Unexpected value: " + a)
                }
                .mkString(", ")
            )
            throw new IllegalArgumentException(msg)
          })
    }
  }
}