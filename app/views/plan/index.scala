package views.html.plan

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._
import lidraughts.pref.PrefCateg

import controllers.routes

object index {

  import trans.patron._

  def apply(
    email: Option[lidraughts.common.EmailAddress],
    stripePublicKey: String,
    patron: Option[lidraughts.plan.Patron],
    recentIds: List[String],
    bestIds: List[String]
  )(implicit ctx: Context) = {

    views.html.base.layout(
      title = becomePatron.txt(),
      moreCss = cssTag("plan"),
      moreJs = frag(
        // script(src := "https://checkout.stripe.com/checkout.js"),
        jsTag("checkout.js"),
        embedJsUnsafe(s"""lidraughts.checkout("$stripePublicKey");""")
      ),
      openGraph = lidraughts.app.ui.OpenGraph(
        title = becomePatron.txt(),
        url = s"$netBaseUrl${routes.Plan.index.url}",
        description = freeDraughts.txt()
      ).some,
      csp = defaultCsp.withStripe.some
    ) {
        main(cls := "page-menu plan")(
          st.aside(cls := "page-menu__menu recent-patrons")(
            h2(newPatrons()),
            div(cls := "list")(
              recentIds.map { userId =>
                div(userIdLink(userId.some))
              }
            )
          ),
          div(cls := "page-menu__content box")(
            patron.ifTrue(ctx.me.??(_.isPatron)).map { p =>
              div(cls := "banner one_time_active")(
                iconTag(patronIconChar),
                div(
                  h1(thankYou()),
                  if (p.isLifetime) youHaveLifetime()
                  else p.expiresAt.map { expires =>
                    frag(
                      patronUntil(showDate(expires)), br,
                      ifNotRenewed()
                    )
                  }
                ),
                iconTag(patronIconChar)
              )
            } getOrElse div(cls := "banner moto")(
              iconTag(patronIconChar),
              div(
                h1(freeDraughts()),
                p(noAdsNoSubs())
              ),
              iconTag(patronIconChar)
            ),
            div(cls := "box__pad")(
              div(cls := "wrapper")(
                div(cls := "text")(
                  p(weAreVolunteers()),
                  p(weRelyOnSupport())
                ),
                div(cls := "content")(

                  div(
                    cls := "plan_checkout",
                    attr("data-email") := email.??(_.value),
                    attr("data-lifetime-usd") := lidraughts.plan.Cents.lifetime.usd.toString,
                    attr("data-lifetime-cents") := lidraughts.plan.Cents.lifetime.value
                  )(
                      raw(s"""
<form class="paypal_checkout onetime none" action="https://www.paypal.com/cgi-bin/webscr" method="post" target="_top">
  <input type="hidden" name="custom" value="${~ctx.userId}">
  <input type="hidden" name="amount" class="amount" value="">
  <input type="hidden" name="cmd" value="_xclick">
  <input type="hidden" name="business" value="EWNKNKK58PMS6">
  <input type="hidden" name="item_name" value="lidraughts.org one-time">
  <input type="hidden" name="button_subtype" value="services">
  <input type="hidden" name="no_note" value="1">
  <input type="hidden" name="no_shipping" value="1">
  <input type="hidden" name="rm" value="1">
  <input type="hidden" name="return" value="$netBaseUrl/patron/thanks">
  <input type="hidden" name="cancel_return" value="$netBaseUrl/patron">
  <input type="hidden" name="lc" value="US">
  <input type="hidden" name="currency_code" value="EUR">
</form>
<form class="paypal_checkout monthly none" action="https://www.paypal.com/cgi-bin/webscr" method="post" target="_top">
  <input type="hidden" name="custom" value="${~ctx.userId}">
  <input type="hidden" name="a3" class="amount" value="">
  <input type="hidden" name="cmd" value="_xclick-subscriptions">
  <input type="hidden" name="business" value="EWNKNKK58PMS6">
  <input type="hidden" name="item_name" value="lidraughts.org monthly">
  <input type="hidden" name="no_note" value="1">
  <input type="hidden" name="no_shipping" value="1">
  <input type="hidden" name="rm" value="1">
  <input type="hidden" name="return" value="$netBaseUrl/patron/thanks">
  <input type="hidden" name="cancel_return" value="$netBaseUrl/patron">
  <input type="hidden" name="src" value="1">
  <input type="hidden" name="p3" value="1">
  <input type="hidden" name="t3" value="M">
  <input type="hidden" name="lc" value="US">
  <input type="hidden" name="currency_code" value="EUR">
</form>
<form class="paypal_checkout lifetime none" action="https://www.paypal.com/cgi-bin/webscr" method="post" target="_top">
  <input type="hidden" name="custom" value="${~ctx.userId}">
  <input type="hidden" name="amount" class="amount" value="">
  <input type="hidden" name="cmd" value="_xclick">
  <input type="hidden" name="business" value="EWNKNKK58PMS6">
  <input type="hidden" name="item_name" value="lidraughts.org lifetime">
  <input type="hidden" name="button_subtype" value="services">
  <input type="hidden" name="no_note" value="1">
  <input type="hidden" name="no_shipping" value="1">
  <input type="hidden" name="rm" value="1">
  <input type="hidden" name="return" value="$netBaseUrl/patron/thanks">
  <input type="hidden" name="cancel_return" value="$netBaseUrl/patron">
  <input type="hidden" name="lc" value="US">
  <input type="hidden" name="currency_code" value="EUR">
</form>"""),

                      ctx.me map { me =>
                        p(style := "text-align:center;margin-bottom:1em")(
                          if (patron.exists(_.isLifetime))
                            makeExtraDonation()
                          else
                            frag(
                              donatingPubliclyAs(userSpan(me))
                            )
                        )
                      },
                      st.group(cls := "radio buttons freq")(
                        div(
                          st.title := payLifetimeOnce.txt(lidraughts.plan.Cents.lifetime.usd),
                          cls := List("lifetime-check" -> patron.exists(_.isLifetime)),
                          input(tpe := "radio", name := "freq", id := "freq_lifetime", patron.exists(_.isLifetime) option disabled, value := "lifetime"),
                          label(`for` := "freq_lifetime")(lifetime())
                        ),
                        div(
                          st.title := recurringBilling.txt(),
                          input(tpe := "radio", name := "freq", id := "freq_monthly", value := "monthly"),
                          label(`for` := "freq_monthly")(monthly())
                        ),
                        div(
                          st.title := singleDonation.txt(),
                          input(tpe := "radio", name := "freq", id := "freq_onetime", checked, value := "onetime"),
                          label(`for` := "freq_onetime")(onetime())
                        )
                      ),
                      div(cls := "amount_choice")(
                        st.group(cls := "radio buttons amount")(
                          lidraughts.plan.StripePlan.defaultAmounts.map { cents =>
                            val id = s"plan_${cents.value}"
                            div(
                              input(tpe := "radio", name := "plan",
                                st.id := id,
                                cents.usd.value == 10 option checked,
                                value := cents.value,
                                attr("data-usd") := cents.usd.toString,
                                attr("data-amount") := cents.value),
                              label(`for` := id)(cents.usd.toString)
                            )
                          },
                          div(cls := "other")(
                            input(tpe := "radio", name := "plan",
                              id := "plan_other",
                              value := "other"),
                            label(
                              `for` := "plan_other",
                              title := pleaseEnterAmount.txt(),
                              attr("data-trans-other") := otherAmount.txt()
                            )(otherAmount())
                          )
                        )
                      ),
                      div(cls := "amount_fixed none")(
                        st.group(cls := "radio buttons amount")(
                          div {
                            val cents = lidraughts.plan.Cents.lifetime
                            label(`for` := s"plan_${cents.value}")(cents.usd.toString)
                          }
                        )
                      ),
                      div(cls := "service")(
                        // button(cls := "stripe button")(withCreditCard()),
                        button(cls := "paypal button")(withPaypal())
                      )
                    )
                )
              ),
              p(cls := "small_team")(weAreSmallTeam()),
              faq,
              div(cls := "best_patrons")(
                h2(celebratedPatrons()),
                div(cls := "list")(
                  bestIds.map { userId =>
                    div(userIdLink(userId.some))
                  }
                )
              )
            )
          )
        )
      }
  }

  private def faq(implicit ctx: Context) = div(cls := "faq")(
    dl(
      dt(whereMoneyGoes()),
      dd(
        serversAndExpenses()
      )
    ),
    dl(
      dt(changeMonthlySupport()),
      dd(
        changeOrContact(a(href := routes.Page.contact, target := "_blank")(contactSupport()))
      ),
      dt(otherMethods()),
      dd(
        bankTransfers(contactEmailLink)
      )
    ),
    dl(
      dt(patronFeatures()),
      dd(
        noPatronFeatures(), br,
        a(href := routes.Plan.features, target := "_blank")(featuresComparison())
      )
    )
  )
}
