goog.provide('lib2.sub');

goog.require('lib2');
goog.require('bootstrap.bs');

/**
 * @namespace
 */
lib2.sub = {};

lib2.sub.foo = function () {
    var bs = bootstrap.bs;
    alert('foo');
    alert(bs.btn.primary);
};