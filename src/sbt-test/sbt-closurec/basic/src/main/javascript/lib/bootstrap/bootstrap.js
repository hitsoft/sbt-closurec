goog.provide('bootstrap.bs');

/**
 * @namespace
 */
var bootstrap = {};

bootstrap.bs = {
    grid: {
        row: '.row',
        span: function(cols){
            return '.span' + cols;
        }
    },
    btn: {
        def: '.btn',
        primary: '.btn.primary'
    }
};