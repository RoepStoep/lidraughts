package views.html.site

import lidraughts.api.Context
import lidraughts.app.templating.Environment._
import lidraughts.app.ui.ScalatagsTemplate._

import controllers.routes

object bits {

  def getDraughtsnet()(implicit ctx: Context) =
    views.html.base.layout(
      title = "Draughtsnet API key request",
      csp = defaultCsp.withGoogleForm.some
    ) {
      main(
        iframe(
          //src := "https://docs.google.com/forms/d/e/1FAIpQLSeSAp51tSaW9JlPGVX0o8dcScAuxGMhNOL9eEUIfARGzpITmA/viewform?embedded=true",
          style := "width:100%;height:1400px",
          st.frameborder := 0
        )(spinner)
      )
    }

  def api = raw("""<!DOCTYPE html>
<html>
  <head>
    <meta charset="utf-8">
    <meta http-equiv="Content-Security-Policy" content="default-src 'self'; style-src 'unsafe-inline'; script-src https://cdn.jsdelivr.net blob:; child-src blob:; connect-src https://raw.githubusercontent.com; img-src data: https://lidraughts.org;">
    <title>Lidraughts.org API reference</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link rel="icon" type="image/png" href="https://lidraughts.org/assets/images/favicon-32-white.png">
    <style>body { margin: 0; padding: 0; }</style>
  </head>
  <body>
    <redoc spec-url="https://raw.githubusercontent.com/roepstoep/lidraughts-api/master/doc/specs/lidraughts-api.yaml"></redoc>
    <script src="https://cdn.jsdelivr.net/npm/redoc@2.5.3/bundles/redoc.standalone.js"></script>
  </body>
</html>""")
}
