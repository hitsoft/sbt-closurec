goog.require('lib1.sub');
goog.require('lib2.sub');

(function(){
  lib2.sub.foo();
  lib1.sub.bar();
}());