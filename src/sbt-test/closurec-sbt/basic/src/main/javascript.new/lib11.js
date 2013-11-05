goog.provide('lib1.sub');

goog.require('lib1');

/**
 * @namespace
 */
lib1.sub = {};

lib1.sub.bar = function(){
  alert('bar');
};