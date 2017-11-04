type t;

type cred =
  Js.t {
    .
    consumer_key : string,
    consumer_secret : string,
    access_token_key : string,
    access_token_secret : string
  };

external twitter : cred => t = "" [@@bs.new] [@@bs.module];

let twitter_cred = {
  "consumer_key": Sys.getenv "TWITTER_CONSUMER_KEY",
  "consumer_secret": Sys.getenv "TWITTER_CONSUMER_SECRET",
  "access_token_key": Sys.getenv "TWITTER_ACCESS_TOKEN_KEY",
  "access_token_secret": Sys.getenv "TWITTER_ACCESS_TOKEN_SECRET"
};

type endpoint = string;

type timeline_params = Js.t {. screen_name : string, count : int, since_id : string};

type error;

type response;

type stream_params = Js.t {. track : string};

type stream;

external stream : t => endpoint => stream_params => stream = "" [@@bs.send];

external on : stream => [ | `data | `error] [@bs.string] => ('event => unit) => unit =
  "" [@@bs.send];

let client = twitter twitter_cred;

type callback = Js.Json.t => unit;

let handle callback error tweets _response =>
  switch (Js.Null_undefined.to_opt error) {
  | None => callback tweets
  | Some error => Js.log error
  };

type search_params = Js.t {. q : string, count : int, since_id : string, max_id : string};

external get :
  t =>
  endpoint =>
  [ | `timeline timeline_params | `search search_params] [@bs.unwrap] =>
  (Js.null_undefined error => Js.Json.t => response => unit) =>
  unit =
  "" [@@bs.send];

let get_user_timeline screen_name count since_id callback => {
  let params = `timeline {"screen_name": screen_name, "count": count, "since_id": since_id};
  get client "statuses/user_timeline" params (handle callback)
};

let track_stream track event_fun error_fun => {
  let s = stream client "statuses/filter" {"track": track};
  on s `data (fun event => event_fun event);
  on s `error (fun error => error_fun error)
};

let search q since_id max_id callback => {
  let params = `search {"q": q, "count": 100, "since_id": since_id, "max_id": max_id};
  get client "search/tweets" params (handle callback)
};
