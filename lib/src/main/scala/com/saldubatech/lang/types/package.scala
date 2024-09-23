package com.saldubatech.lang

package object types:


  type MAP[T, TPL, L[_ <: T]] <: Tuple =
    TPL match
      case x *: xs => L[x] *: MAP[T, xs, L]
      case EmptyTuple => EmptyTuple

  type TUPLIFY[TPL, ELEM] =
    TPL match
      case x *: xs => ELEM *: TUPLIFY[xs, ELEM]
      case EmptyTuple => EmptyTuple
      case _ => ELEM

  type SUB_TUPLE[TPL, ELEM] = TPL <:< TUPLIFY[TPL, ELEM]

  type OR[T /*<: Tuple*/ ] = // NULL_RFOLD[T, |]
    T match
      case x *: EmptyTuple => x
      case x *: xs => x | OR[xs]
      case _ => T
//  type RFOLD[T <: Tuple, CMP[_, _], BOTTOM] =
//    T match
//      case x *: xs => CMP[x, RFOLD[xs, CMP, BOTTOM]]
//      case EmptyTuple => BOTTOM

//  type L_FOLD[T <: Tuple, HEAD, CMP[_, _]] =
//    T match
//      case EmptyTuple => HEAD
//      case x *: xs => L_FOLD[xs, CMP[HEAD, x], CMP]

//  type NULL_R_FOLD[T /*<: Tuple*/, CMP[_ <: T, _]] =
//    T match
//      case x *: EmptyTuple => x
//      case x *: xs => CMP[x, NULL_R_FOLD[xs, CMP]]

//  type NULL_L_FOLD[T <: Tuple, CMP[_, _]] =
//    T match
//      case x *: EmptyTuple => x
//      case x *: xs => L_FOLD[xs, CMP[HEAD, x], CMP]

