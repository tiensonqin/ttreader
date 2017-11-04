// Generated by BUCKLESCRIPT VERSION 1.9.3, PLEASE EDIT WITH CARE
'use strict';

var Js_json = require("bs-platform/lib/js/js_json.js");

function makeSuccessJson(data) {
  var json = { };
  json["success"] = data;
  return json;
}

function makeFailJson(error) {
  var json = { };
  json["bad"] = error;
  return json;
}

function getDictString(dict, key) {
  var match = dict[key];
  if (match !== undefined) {
    return Js_json.decodeString(match);
  } else {
    return /* None */0;
  }
}

function getParam(req, key) {
  return getDictString(req.params, key);
}

exports.makeSuccessJson = makeSuccessJson;
exports.makeFailJson    = makeFailJson;
exports.getDictString   = getDictString;
exports.getParam        = getParam;
/* No side effect */
