// Generated by BUCKLESCRIPT VERSION 1.9.3, PLEASE EDIT WITH CARE
'use strict';

var Curry = require("bs-platform/lib/js/curry.js");

function map(t, f) {
  if (t) {
    return /* Some */[Curry._1(f, t[0])];
  } else {
    return /* None */0;
  }
}

function bind(t, f) {
  if (t) {
    return Curry._1(f, t[0]);
  } else {
    return /* None */0;
  }
}

function both(x, y) {
  if (x && y) {
    return /* Some */[/* tuple */[
              x[0],
              y[0]
            ]];
  } else {
    return /* None */0;
  }
}

function $$return(x) {
  return /* Some */[x];
}

var Open_in_body = /* module */[/* return */$$return];

var Open_on_rhs = /* module */[/* return */$$return];

var Let_syntax = /* module */[
  /* return */$$return,
  /* bind */bind,
  /* map */map,
  /* both */both,
  /* Open_in_body */Open_in_body,
  /* Open_on_rhs */Open_on_rhs
];

exports.map        = map;
exports.bind       = bind;
exports.both       = both;
exports.$$return   = $$return;
exports.Let_syntax = Let_syntax;
/* No side effect */
