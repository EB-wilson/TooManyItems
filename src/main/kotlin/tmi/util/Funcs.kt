package tmi.util

import arc.func.Cons
import arc.func.Cons2
import arc.func.Cons3
import arc.func.Cons4
import arc.func.ConsT
import arc.func.Func
import arc.func.Func2
import arc.func.Func3
import arc.func.Prov

operator fun <P1, P2> Cons2<P1, P2>.invoke(p1: P1, p2: P2) = get(p1, p2)
operator fun <P1, P2, P3> Cons3<P1, P2, P3>.invoke(p1: P1, p2: P2, p3: P3) = get(p1, p2, p3)
operator fun <P1, P2, P3, P4> Cons4<P1, P2, P3, P4>.invoke(p1: P1, p2: P2, p3: P3, p4: P4) = get(p1, p2, p3, p4)
operator fun <P> Cons<P>.invoke(p: P) = get(p)
operator fun <P, T: Throwable> ConsT<P, T>.invoke(p: P) = get(p)
operator fun <P1, P2, R> Func2<P1, P2, R>.invoke(p1: P1, p2: P2): R = get(p1, p2)
operator fun <P1, P2, P3, R> Func3<P1, P2, P3, R>.invoke(p1: P1, p2: P2, p3: P3): R = get(p1, p2, p3)
operator fun <P, R> Func<P, R>.invoke(p: P): R = get(p)
operator fun <R> Prov<R>.invoke(): R = get()