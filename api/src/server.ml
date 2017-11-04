open Express
open Util
module Let_syntax = Option.Let_syntax

let app = express ()

[%%bs.raw{|
  // Add headers
app.use(function (req, res, next) {

    // Website you wish to allow to connect
    res.setHeader('Access-Control-Allow-Origin', '*');

    // Request methods you wish to allow
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS, PUT, PATCH, DELETE');

    // Request headers you wish to allow
    res.setHeader('Access-Control-Allow-Headers', 'X-Requested-With,content-type');

    // Set to true if you need the website to include cookies in the requests sent
    // to the API (e.g. in case you use sessions)
    res.setHeader('Access-Control-Allow-Credentials', true);

    // Pass to next layer of middleware
    next();
});
|}]

(* TODO: use parse-body *)
let _ =
  App.get app ~path:"/users/:screen_name/:since_id"
    (Middleware.from
       (fun req res _next ->
          let%bind screen_name = getParam req "screen_name" in
          let%bind since_id = getParam req "since_id" in
          Twitter.get_user_timeline screen_name 10 since_id
            (fun data  ->
               (* setRespHeaders res; *)
               ignore @@
               (Response.sendJson res (makeSuccessJson data)));
          None))

let _ =
  App.get app ~path:"/search/:q/:since_id/:max_id"
    (Middleware.from
       (fun req res _next ->
          let%bind q = getParam req "q" in
          let%bind since_id = getParam req "since_id" in
          let%bind max_id = getParam req "max_id" in
          Twitter.search q since_id max_id
            (fun data  ->
               (* setRespHeaders res; *)
               ignore @@
               (Response.sendJson res
                  (makeSuccessJson data)));
          None))

(* TODO: not working now, sse *)
let _ =
  App.get app ~path:"/tweets/:track"
    (Middleware.from
       (fun req res _next ->
          let%bind track = getParam req "track" in
          Twitter.track_stream track
            (fun data  ->
               ignore @@
               (* setRespHeaders res; *)
               (Response.sendJson res (makeSuccessJson data)))
            (fun error  ->
               ignore @@
               (Response.sendJson res (makeFailJson error)));
          None))

let _ = App.listen app ()
