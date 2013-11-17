goog.provide('lib1.sub');

goog.require('lib1');
goog.require('haml._h');

/**
 * @namespace
 */
lib1.sub = {};

lib1.sub.bar = function () {
    var _h = haml._h;
    alert('bar');
    alert(_h.div());
    alert(_h.div());
};