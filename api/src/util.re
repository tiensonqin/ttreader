let makeSuccessJson data => {
  let json = Js.Dict.empty ();
  Js.Dict.set json "success" data;
  Js.Json.object_ json
};

let makeFailJson error => {
  let json = Js.Dict.empty ();
  Js.Dict.set json "bad" error;
  Js.Json.object_ json
};

let getDictString dict key =>
  switch (Js.Dict.get dict key) {
  | Some json => Js.Json.decodeString json
  | _ => None
  };

let getParam req key => getDictString (Express.Request.params req) key;
