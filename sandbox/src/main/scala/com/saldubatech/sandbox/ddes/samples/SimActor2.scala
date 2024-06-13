package com.saldubatech.sandbox.ddes.samples

object Dummy

//abstract class SimActor2[-PAYLOAD <: DomainMessage](
//                                                    using
//                                                    private val clock: Clock
//                                                  ) extends LogEnabled:
//  self =>
//  val name: String
//
//  override def toString: String = s"${name} SimActor"
//
//  final type PROTOCOL = types.DOMAIN_MESSAGE
//
//  def newAction[DM <: DOMAIN_MESSAGE : ClassTag](action: SimAction, from: SimActor[?], message: DM): EVENT_ACTION
//
//  private var _ctx: Option[SELF_CONTEXT] = None
//  lazy val ctx: SELF_CONTEXT = _ctx.get
//
//  private var _at: Tick = 0
//
//  def at: Tick = _at
//
//  def accept[DM <: DOMAIN_MESSAGE](at: Tick, ctx: SELF_CONTEXT, ev: DOMAIN_EVENT): ActionResult
//
//  def oam(msg: OAMMessage, ctx: SELF_CONTEXT): ActionResult
//
//  private def command[DM <: DOMAIN_MESSAGE : ClassTag](forTime: Tick, from: SimActor[?], message: DM): Command =
//    new Command:
//      override val forEpoch: Tick = forTime
//      override val action: SimAction = SimAction(forEpoch)
//
//      override def toString: String = super.toString + s" with Msg: $message"
//
//      override def send: SimAction = {
//        log.debug(s"Sending command at $at from ${from.name} to $name")
//        ctx.self ! newAction(action, from, message)
//        action
//      }
//
//  final def init()(using ClassTag[EVENT_ACTION]): Behavior[EVENT_ACTION | OAMMessage] =
//    Behaviors.setup {
//      ctx =>
//        _ctx = Some(ctx)
//        Behaviors.receiveMessage {
//          case oamMsg: OAMMessage =>
//            oam(oamMsg, ctx)
//            Behaviors.same
//          case evAct: EVENT_ACTION =>
//            log.debug(s"$name receiving at ${evAct.event.at} : ${evAct.event}")
//            _at = evAct.event.at
//            accept(evAct.action.at, ctx, evAct.event)
//            clock.complete(evAct.action, this)
//            Behaviors.same
//        }
//    }
//
//  def schedule[TARGET_PROTOCOL <: DomainMessage]
//  (target: SimActor[TARGET_PROTOCOL])(forTime: Tick, targetMsg: target.PROTOCOL): Unit =
//    clock.request(target.command(forTime, this, targetMsg)(using target.types.dmCt))
//
//
//}